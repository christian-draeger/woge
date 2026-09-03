package dev.woge.spike.framing

import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException

public enum class FrameKind(public val code: Int) {
    PATCH(1),
    COMPLETE(2),
    ERROR(3),
    ;

    public val terminal: Boolean
        get() = this != PATCH

    public companion object {
        public fun fromCode(code: Int): FrameKind = entries.firstOrNull { it.code == code }
            ?: throw FrameException("Unknown frame kind: $code")
    }
}

public class WireFrame(
    public val kind: FrameKind,
    public val contentType: String,
    public val metadata: String,
    public val payload: ByteArray,
) {
    public companion object {
        public fun patch(metadata: String, html: String): WireFrame = WireFrame(
            kind = FrameKind.PATCH,
            contentType = "text/html; charset=utf-8",
            metadata = metadata,
            payload = html.toByteArray(StandardCharsets.UTF_8),
        )

        public fun complete(metadata: String = "{}"): WireFrame = WireFrame(
            kind = FrameKind.COMPLETE,
            contentType = "application/json; charset=utf-8",
            metadata = metadata,
            payload = byteArrayOf(),
        )

        public fun error(safeMetadata: String): WireFrame = WireFrame(
            kind = FrameKind.ERROR,
            contentType = "application/problem+json; charset=utf-8",
            metadata = safeMetadata,
            payload = byteArrayOf(),
        )
    }
}

public class FrameException(message: String) : IllegalArgumentException(message)

public interface FrameDecoder {
    public fun feed(chunk: ByteArray): List<WireFrame>
    public fun finish()
}

internal const val MAX_METADATA_BYTES: Int = 64 * 1024
internal const val MAX_PAYLOAD_BYTES: Int = 8 * 1024 * 1024

internal fun validateFrame(frame: WireFrame) {
    val metadataSize = frame.metadata.toByteArray(StandardCharsets.UTF_8).size
    if (metadataSize > MAX_METADATA_BYTES) throw FrameException("Metadata exceeds $MAX_METADATA_BYTES bytes")
    if (frame.payload.size > MAX_PAYLOAD_BYTES) throw FrameException("Payload exceeds $MAX_PAYLOAD_BYTES bytes")
    if (frame.kind.terminal && frame.payload.isNotEmpty()) {
        throw FrameException("Terminal frame payload must be empty")
    }
    if ('\r' in frame.contentType || '\n' in frame.contentType) {
        throw FrameException("Invalid content type")
    }
    if (frame.contentType.any { it.code !in 0x20..0x7e }) throw FrameException("Content type must be ASCII")
}

internal fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

internal fun ByteArray.dropPrefix(count: Int): ByteArray = copyOfRange(count, size)

internal fun ByteArray.indexOf(needle: ByteArray): Int {
    if (needle.isEmpty()) return 0
    for (start in 0..size - needle.size) {
        if (needle.indices.all { offset -> this[start + offset] == needle[offset] }) return start
    }
    return -1
}

internal fun ByteArray.decodeUtf8(field: String): String = decodeStrict(StandardCharsets.UTF_8, field)

internal fun ByteArray.decodeAscii(field: String): String = decodeStrict(StandardCharsets.US_ASCII, field)

private fun ByteArray.decodeStrict(charset: java.nio.charset.Charset, field: String): String = try {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(this))
        .toString()
} catch (_: CharacterCodingException) {
    throw FrameException("Invalid $field encoding")
}
