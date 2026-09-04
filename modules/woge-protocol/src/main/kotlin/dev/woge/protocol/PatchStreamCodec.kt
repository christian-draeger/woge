package dev.woge.protocol

import dev.woge.protocol.internal.ReplaceMetadata
import dev.woge.protocol.internal.decodeCompletionMetadata
import dev.woge.protocol.internal.decodeRemoteFailureMetadata
import dev.woge.protocol.internal.decodeReplaceMetadata
import dev.woge.protocol.internal.encodeCompletionMetadata
import dev.woge.protocol.internal.encodeRemoteFailureMetadata
import dev.woge.protocol.internal.encodeReplaceMetadata
import dev.woge.protocol.internal.protocolFailure
import dev.woge.protocol.internal.validatePatchHtml
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** Incremental version-1 patch-stream encoder. */
public class PatchStreamEncoder internal constructor(
    private val sink: ByteSink,
) {
    private var started: Boolean = false
    private var terminal: Boolean = false
    private var patchCount: Int = 0
    private var downstreamFailure: Throwable? = null

    /** Encodes one fully validated semantic patch without terminating the stream. */
    public fun write(patch: Patch) {
        ensureOpen()
        when (patch) {
            is ReplacePatch -> writeReplace(patch)
        }
    }

    /** Writes the required terminal completion frame. */
    public fun complete() {
        ensureOpen()
        val metadata = encodeCompletionMetadata(patchCount).encodeUtf8()
        writeFrame(FrameKind.COMPLETE, COMPLETE_CONTENT_TYPE, metadata, EMPTY_BYTES)
        terminal = true
    }

    /** Writes a safe terminal application failure after an HTTP response has been committed. */
    public fun error(failure: RemotePatchFailure) {
        ensureOpen()
        val metadata = encodeRemoteFailureMetadata(failure).encodeUtf8()
        writeFrame(FrameKind.ERROR, ERROR_CONTENT_TYPE, metadata, EMPTY_BYTES)
        terminal = true
    }

    private fun writeReplace(patch: ReplacePatch) {
        if (patchCount == Int.MAX_VALUE) {
            protocolFailure(PatchStreamErrorCode.INVALID_SEQUENCE, "Patch count exceeds the version-1 limit")
        }
        val metadata = encodeReplaceMetadata(patch).encodeUtf8()
        val payload = patch.html.value.encodeUtf8()
        validateFrameLengths(PATCH_CONTENT_TYPE.length, metadata.size, payload.size)
        validatePatchHtml(patch.html.value)
        writeFrame(FrameKind.PATCH, PATCH_CONTENT_TYPE, metadata, payload)
        patchCount += 1
    }

    private fun writeFrame(
        kind: FrameKind,
        contentType: String,
        metadata: ByteArray,
        payload: ByteArray,
    ) {
        val contentTypeBytes = contentType.toByteArray(StandardCharsets.US_ASCII)
        validateFrameLengths(contentTypeBytes.size, metadata.size, payload.size)
        ensurePreamble()
        writeDownstream(frameHeader(kind, contentTypeBytes.size, metadata.size, payload.size))
        writeDownstream(contentTypeBytes)
        writeDownstream(metadata)
        if (payload.isNotEmpty()) writeDownstream(payload)
    }

    private fun ensurePreamble() {
        if (!started) {
            writeDownstream(PREAMBLE.copyOf())
            started = true
        }
    }

    private fun writeDownstream(bytes: ByteArray) {
        val outcome = runCatching { sink.write(bytes) }
        downstreamFailure = outcome.exceptionOrNull()
        outcome.getOrThrow()
    }

    private fun ensureOpen() {
        downstreamFailure?.let { throw it }
        if (terminal) {
            protocolFailure(PatchStreamErrorCode.INVALID_SEQUENCE, "Patch stream is already terminal")
        }
    }
}

internal class VersionOnePatchStreamDecoder : PatchStreamDecoder {
    private var buffer: ByteArray = EMPTY_BYTES
    private var preambleRead: Boolean = false
    private var terminalRead: Boolean = false
    private var patchCount: Int = 0
    private var failure: PatchStreamException? = null

    override fun feed(bytes: ByteArray): List<PatchStreamEvent> =
        guarded {
            if (terminalRead && bytes.isNotEmpty()) {
                protocolFailure(
                    PatchStreamErrorCode.BYTES_AFTER_TERMINAL,
                    "Patch stream contains bytes after its terminal frame",
                )
            }

            val events = mutableListOf<PatchStreamEvent>()
            var offset = 0
            while (offset < bytes.size) {
                drain(events)
                if (terminalRead) {
                    protocolFailure(
                        PatchStreamErrorCode.BYTES_AFTER_TERMINAL,
                        "Patch stream contains bytes after its terminal frame",
                    )
                }

                val capacity = MAX_BUFFER_BYTES - buffer.size
                if (capacity <= 0) {
                    protocolFailure(PatchStreamErrorCode.INVALID_LENGTH, "Patch frame exceeds its bounded buffer")
                }
                val count = minOf(capacity, bytes.size - offset)
                append(bytes, offset, count)
                offset += count
            }
            drain(events)
            events
        }

    override fun finish() {
        guarded {
            when {
                !preambleRead && buffer.isEmpty() ->
                    protocolFailure(PatchStreamErrorCode.INVALID_PREAMBLE, "Patch stream preamble is missing")

                !preambleRead || buffer.isNotEmpty() ->
                    protocolFailure(PatchStreamErrorCode.TRUNCATED_STREAM, "Patch stream ended inside a frame")

                !terminalRead ->
                    protocolFailure(PatchStreamErrorCode.MISSING_TERMINAL, "Patch stream has no terminal frame")
            }
        }
    }

    private fun drain(events: MutableList<PatchStreamEvent>) {
        readPreambleIfAvailable()
        if (!preambleRead) return

        while (!terminalRead && buffer.size >= FRAME_HEADER_BYTES) {
            val kind = FrameKind.fromCode(buffer[0].toInt() and UNSIGNED_BYTE_MASK)
            val contentTypeLength = buffer[1].toInt() and UNSIGNED_BYTE_MASK
            val metadataLength = readUnsignedInt(buffer, METADATA_LENGTH_OFFSET)
            val payloadLength = readUnsignedInt(buffer, PAYLOAD_LENGTH_OFFSET)
            validateDeclaredLengths(contentTypeLength, metadataLength, payloadLength)
            if (kind.terminal) requireEmptyTerminalPayload(payloadLength.toInt())

            val frameLength = FRAME_HEADER_BYTES.toLong() + contentTypeLength + metadataLength + payloadLength
            if (buffer.size.toLong() < frameLength) return

            var offset = FRAME_HEADER_BYTES
            val contentType = buffer.copyOfRange(offset, offset + contentTypeLength).decodeContentType()
            offset += contentTypeLength
            val metadataBytes = buffer.copyOfRange(offset, offset + metadataLength.toInt())
            offset += metadataLength.toInt()

            val event = decodeFrame(kind, contentType, metadataBytes, offset, payloadLength.toInt())
            buffer = buffer.copyOfRange(frameLength.toInt(), buffer.size)
            events += event
            if (kind == FrameKind.PATCH) patchCount += 1
            if (kind.terminal) terminalRead = true
        }

        if (terminalRead && buffer.isNotEmpty()) {
            protocolFailure(
                PatchStreamErrorCode.BYTES_AFTER_TERMINAL,
                "Patch stream contains bytes after its terminal frame",
            )
        }
    }

    private fun readPreambleIfAvailable() {
        if (preambleRead || buffer.size < PREAMBLE_MAGIC.size) return
        if (!buffer.startsWith(PREAMBLE_MAGIC)) {
            protocolFailure(PatchStreamErrorCode.INVALID_PREAMBLE, "Patch stream preamble magic is invalid")
        }
        if (buffer.size < PREAMBLE.size) return
        val version = buffer[PREAMBLE_VERSION_OFFSET].toInt() and UNSIGNED_BYTE_MASK
        if (version != VERSION_ONE) {
            protocolFailure(PatchStreamErrorCode.UNSUPPORTED_VERSION, "Patch stream version is unsupported")
        }
        buffer = buffer.copyOfRange(PREAMBLE.size, buffer.size)
        preambleRead = true
    }

    private fun decodeFrame(
        kind: FrameKind,
        contentType: String,
        metadataBytes: ByteArray,
        payloadOffset: Int,
        payloadLength: Int,
    ): PatchStreamEvent {
        requireContentType(kind, contentType)
        val metadata = metadataBytes.decodeUtf8()
        return when (kind) {
            FrameKind.PATCH -> decodePatch(metadata, payloadOffset, payloadLength)
            FrameKind.COMPLETE -> decodeComplete(metadata, payloadLength)
            FrameKind.ERROR -> decodeError(metadata, payloadLength)
        }
    }

    private fun decodePatch(
        metadataValue: String,
        payloadOffset: Int,
        payloadLength: Int,
    ): PatchStreamEvent.PatchFrame {
        val metadata = decodeReplaceMetadata(metadataValue)
        val html = buffer.copyOfRange(payloadOffset, payloadOffset + payloadLength).decodeUtf8()
        validatePatchHtml(html)
        return PatchStreamEvent.PatchFrame(metadata.toReplacePatch(PatchHtml(html)))
    }

    private fun decodeComplete(
        metadataValue: String,
        payloadLength: Int,
    ): PatchStreamEvent.Complete {
        requireEmptyTerminalPayload(payloadLength)
        val declaredPatchCount = decodeCompletionMetadata(metadataValue)
        if (declaredPatchCount != patchCount) {
            protocolFailure(
                PatchStreamErrorCode.INVALID_SEQUENCE,
                "Completion metadata does not match the decoded patch count",
            )
        }
        return PatchStreamEvent.Complete(declaredPatchCount)
    }

    private fun decodeError(
        metadataValue: String,
        payloadLength: Int,
    ): PatchStreamEvent.Error {
        requireEmptyTerminalPayload(payloadLength)
        return PatchStreamEvent.Error(decodeRemoteFailureMetadata(metadataValue))
    }

    private fun append(
        bytes: ByteArray,
        offset: Int,
        count: Int,
    ) {
        val appended = ByteArray(buffer.size + count)
        buffer.copyInto(appended)
        bytes.copyInto(appended, destinationOffset = buffer.size, startIndex = offset, endIndex = offset + count)
        buffer = appended
    }

    private inline fun <T> guarded(block: () -> T): T {
        failure?.let { throw it }
        return try {
            block()
        } catch (problem: PatchStreamException) {
            failure = problem
            throw problem
        }
    }
}

internal fun encodePatchStream(patches: Iterable<Patch>): ByteArray {
    val output = ByteArrayOutputStream()
    val encoder = PatchStreamEncoder(ByteSink(output::write))
    patches.forEach(encoder::write)
    encoder.complete()
    return output.toByteArray()
}

private fun ReplaceMetadata.toReplacePatch(html: PatchHtml): ReplacePatch =
    ReplacePatch(
        protocolVersion = protocolVersion,
        patchId = patchId,
        target = PatchTarget(pageEpoch, target),
        interactionSequence = interactionSequence,
        revision = revision,
        html = html,
    )
