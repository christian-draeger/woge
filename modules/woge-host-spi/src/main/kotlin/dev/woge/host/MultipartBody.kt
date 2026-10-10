package dev.woge.host

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path

/**
 * Incremental native multipart reader. A caller owns [close] until [finish] transfers ownership.
 * File bytes go to private temporary files; text and headers have independent bounded buffers.
 */
@Suppress("TooManyFunctions")
public class MultipartBody<Command : Any> internal constructor(
    private val decoder: FormDecoder<Command>,
    contentType: String?,
    private val limits: UploadLimits,
    temporaryDirectory: Path?,
) : AutoCloseable {
    private val boundary = multipartBoundary(contentType)
    private val opening = "--$boundary".toByteArray(Charsets.US_ASCII)
    private val delimiter = "\r\n--$boundary".toByteArray(Charsets.US_ASCII)
    private val pending = ByteArray(delimiter.size + 2)
    private var pendingCount = 0
    private val headers = ByteArrayOutputStream()
    private val storage = UploadStorage(temporaryDirectory)
    private val files = mutableListOf<UploadedFile>()
    private val fields = linkedMapOf<String, MutableList<String>>()
    private var state = MultipartState.OPENING
    private var part: MultipartPart? = null
    private var requestBytes = 0L
    private var storedBytes = 0L
    private var textBytes = 0L
    private var headerTail = 0
    private var transferred = false
    private var closed = false
    private var failure: Throwable? = null

    /** Rejection is terminal. Adapters must stop reading and close this reader immediately. */
    public fun accept(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset,
    ) {
        failure?.let { throw it }
        check(!closed) { "Multipart input is closed" }
        check(!transferred) { "Multipart input has already finished" }
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length) { "Invalid byte range" }
        for (index in offset until offset + length) {
            if (requestBytes == limits.requestBytes) reject(UploadLimit.REQUEST_BYTES, limits.requestBytes)
            requestBytes++
            try {
                read(bytes[index])
            } catch (cause: FormDecodingException) {
                fail(cause)
            } catch (cause: UploadDecodingException) {
                fail(cause)
            } catch (cause: java.io.IOException) {
                fail(cause)
            }
        }
    }

    /** A complete body transfers its temporary files to the returned submission. */
    public fun finish(): MultipartSubmission<Command> {
        failure?.let { throw it }
        check(!closed) { "Multipart input is closed" }
        check(!transferred) { "Multipart input has already finished" }
        if (state !in setOf(MultipartState.END, MultipartState.END_CRLF)) malformed()
        val submission = MultipartSubmission(decoder.multipartSubmission(fields), files, storage)
        transferred = true
        return submission
    }

    override fun close() {
        if (closed) return
        try {
            part?.output?.close()
        } finally {
            if (!transferred) storage.close()
        }
        closed = true
    }

    private fun read(byte: Byte) {
        when (state) {
            MultipartState.OPENING -> {
                if (byte != opening[pendingCount++]) malformed()
                if (pendingCount == opening.size) {
                    pendingCount = 0
                    state = MultipartState.SUFFIX
                }
            }
            MultipartState.HEADERS -> readHeader(byte)
            MultipartState.CONTENT -> readContent(byte)
            MultipartState.SUFFIX -> readSuffix(byte)
            MultipartState.END -> {
                if (byte != '\r'.code.toByte()) malformed()
                state = MultipartState.END_LF
            }
            MultipartState.END_LF -> {
                if (byte != '\n'.code.toByte()) malformed()
                state = MultipartState.END_CRLF
            }
            MultipartState.END_CRLF -> malformed()
        }
    }

    private fun readSuffix(byte: Byte) {
        pending[pendingCount++] = byte
        if (pendingCount != 2) return
        val suffix = pending.copyOf(2)
        val last = suffix[0] == '-'.code.toByte() && suffix[1] == '-'.code.toByte()
        val next = suffix[0] == '\r'.code.toByte() && suffix[1] == '\n'.code.toByte()
        pendingCount = 0
        if (last || next) {
            if (part != null) finishPart()
            state = if (last) MultipartState.END else MultipartState.HEADERS
        } else {
            if (part == null) malformed()
            delimiter.forEach(::writePart)
            state = MultipartState.CONTENT
            suffix.forEach(::readContent)
        }
    }

    private fun readHeader(byte: Byte) {
        if (headers.size() == limits.headerBytes) reject(UploadLimit.HEADER_BYTES, limits.headerBytes.toLong())
        headers.write(byte.toInt())
        headerTail = (headerTail shl Byte.SIZE_BITS) or (byte.toInt() and BYTE_MASK)
        if (headerTail == HEADER_END_MARKER) {
            val bytes = headers.toByteArray()
            part = startPart(decodeUtf8(bytes.copyOf(bytes.size - HEADER_END.size)))
            headers.reset()
            headerTail = 0
            state = MultipartState.CONTENT
        }
    }

    private fun readContent(byte: Byte) {
        pending[pendingCount++] = byte
        while (!(0 until pendingCount).all { delimiter[it] == pending[it] }) {
            writePart(pending[0])
            pending.copyInto(pending, 0, 1, pendingCount)
            pendingCount--
        }
        if (pendingCount == delimiter.size) {
            pendingCount = 0
            state = MultipartState.SUFFIX
        }
    }

    private fun startPart(header: String): MultipartPart {
        val parsed = multipartHeaders(header, decoder.limits.nameBytes)
        if (parsed.filename == null) {
            if (fields.values.sumOf { it.size } == decoder.limits.fieldCount) {
                throw FormDecodingException(FormProblem.LimitExceeded(FormLimit.FIELD_COUNT, decoder.limits.fieldCount))
            }
            return MultipartPart(parsed, ByteArrayOutputStream(), null)
        }
        if (files.size == limits.fileCount) reject(UploadLimit.FILE_COUNT, limits.fileCount.toLong())
        val path = storage.create()
        return MultipartPart(parsed, BufferedOutputStream(Files.newOutputStream(path)), path)
    }

    private fun writePart(byte: Byte) {
        val active = checkNotNull(part)
        if (active.path == null) {
            if (active.size == decoder.limits.valueBytes.toLong()) {
                throw FormDecodingException(FormProblem.LimitExceeded(FormLimit.VALUE_BYTES, decoder.limits.valueBytes))
            }
            if (textBytes == decoder.limits.bodyBytes.toLong()) {
                throw FormDecodingException(FormProblem.LimitExceeded(FormLimit.BODY_BYTES, decoder.limits.bodyBytes))
            }
            textBytes++
        } else {
            if (active.size == limits.fileBytes) reject(UploadLimit.FILE_BYTES, limits.fileBytes)
            if (storedBytes == limits.temporaryBytes) reject(UploadLimit.TEMPORARY_BYTES, limits.temporaryBytes)
            storedBytes++
        }
        active.output.write(byte.toInt())
        active.size++
    }

    private fun finishPart() {
        val active = checkNotNull(part)
        active.output.close()
        if (active.path == null) {
            val value = decodeUtf8((active.output as ByteArrayOutputStream).toByteArray())
            fields.getOrPut(active.headers.name) { mutableListOf() }.add(value)
        } else {
            files.add(
                UploadedFile(
                    active.headers.name,
                    checkNotNull(active.headers.filename),
                    active.headers.mediaType,
                    active.size,
                    active.path,
                    storage,
                ),
            )
        }
        part = null
    }

    private fun reject(
        limit: UploadLimit,
        threshold: Long,
    ): Nothing = fail(UploadDecodingException(FailureCategory.PAYLOAD_TOO_LARGE, limit, threshold))

    private fun malformed(): Nothing = fail(UploadDecodingException(FailureCategory.BAD_REQUEST))

    private fun fail(cause: Throwable): Nothing {
        failure = cause
        throw cause
    }
}

/** Creates a request-owned raw multipart reader with no host-framework parser or full-body buffer. */
public fun <Command : Any> FormDecoder<Command>.multipartBody(
    contentType: String?,
    limits: UploadLimits = UploadLimits(),
    temporaryDirectory: Path? = null,
): MultipartBody<Command> = MultipartBody(this, contentType, limits, temporaryDirectory)

private enum class MultipartState { OPENING, HEADERS, CONTENT, SUFFIX, END, END_LF, END_CRLF }

private class MultipartPart(
    val headers: MultipartHeaders,
    val output: OutputStream,
    val path: Path?,
    var size: Long = 0,
)

internal fun decodeUtf8(bytes: ByteArray): String =
    try {
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        throw UploadDecodingException(FailureCategory.BAD_REQUEST)
    }

private val HEADER_END = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
private const val HEADER_END_MARKER = 0x0d0a0d0a
private const val BYTE_MASK = 0xff
