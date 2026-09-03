package dev.woge.spike.framing

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

public class MultipartCodec(private val boundary: String) {
    init {
        require(boundary.matches(Regex("[A-Za-z0-9._-]{8,70}"))) { "Unsafe multipart boundary" }
    }

    public val mediaType: String = "multipart/mixed; boundary=$boundary"

    public fun encode(frames: List<WireFrame>): ByteArray {
        validateMultipartSequence(frames)
        val output = ByteArrayOutputStream()
        frames.forEach { frame ->
            val metadata = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(frame.metadata.toByteArray(StandardCharsets.UTF_8))
            output.write("--$boundary\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write("Woge-Kind: ${frame.kind.name}\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write("Content-Type: ${frame.contentType}\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write("Woge-Metadata: $metadata\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write("Content-Length: ${frame.payload.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write(frame.payload)
            output.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
        }
        output.write("--$boundary--\r\n".toByteArray(StandardCharsets.US_ASCII))
        return output.toByteArray()
    }

    public fun decoder(): FrameDecoder = Decoder(boundary)

    private class Decoder(boundary: String) : FrameDecoder {
        private val partPrefix = "--$boundary\r\n".toByteArray(StandardCharsets.US_ASCII)
        private val finalBoundary = "--$boundary--\r\n".toByteArray(StandardCharsets.US_ASCII)
        private val headerEnd = "\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
        private var buffer: ByteArray = byteArrayOf()
        private var pending: PendingPart? = null
        private var terminalRead: Boolean = false
        private var closed: Boolean = false

        override fun feed(chunk: ByteArray): List<WireFrame> {
            if (closed && chunk.isNotEmpty()) throw FrameException("Bytes after final multipart boundary")
            buffer += chunk
            val frames = mutableListOf<WireFrame>()

            while (!closed) {
                if (terminalRead && pending == null) {
                    if (buffer.size < finalBoundary.size) break
                    if (!buffer.startsWith(finalBoundary)) throw FrameException("Missing final multipart boundary")
                    buffer = buffer.dropPrefix(finalBoundary.size)
                    closed = true
                    if (buffer.isNotEmpty()) throw FrameException("Bytes after final multipart boundary")
                    break
                }

                val current = pending
                if (current == null) {
                    val end = buffer.indexOf(headerEnd)
                    if (end < 0) {
                        if (buffer.size > 8 * 1024) throw FrameException("Multipart headers exceed 8192 bytes")
                        break
                    }
                    val headerBytes = buffer.copyOfRange(0, end)
                    if (!headerBytes.startsWith(partPrefix.copyOfRange(0, partPrefix.size - 2))) {
                        throw FrameException("Invalid multipart boundary")
                    }
                    val headerText = headerBytes.decodeAscii("multipart header")
                    pending = parseHeaders(headerText.substringAfter("\r\n"))
                    buffer = buffer.dropPrefix(end + headerEnd.size)
                    continue
                }

                val required = current.contentLength.toLong() + 2
                if (required > Int.MAX_VALUE || buffer.size < required.toInt()) break
                if (buffer[current.contentLength] != '\r'.code.toByte() ||
                    buffer[current.contentLength + 1] != '\n'.code.toByte()
                ) {
                    throw FrameException("Multipart body is not followed by CRLF")
                }
                val payload = buffer.copyOfRange(0, current.contentLength)
                val frame = WireFrame(current.kind, current.contentType, current.metadata, payload)
                validateFrame(frame)
                frames += frame
                terminalRead = frame.kind.terminal
                pending = null
                buffer = buffer.dropPrefix(current.contentLength + 2)
            }

            return frames
        }

        override fun finish() {
            if (pending != null || buffer.isNotEmpty()) throw FrameException("Truncated multipart stream")
            if (!terminalRead) throw FrameException("Missing terminal frame")
            if (!closed) throw FrameException("Missing final multipart boundary")
        }

        private fun parseHeaders(text: String): PendingPart {
            val headers = linkedMapOf<String, String>()
            text.split("\r\n").forEach { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) throw FrameException("Malformed multipart header")
                val name = line.substring(0, separator).lowercase()
                if (name in headers) throw FrameException("Duplicate multipart header: $name")
                headers[name] = line.substring(separator + 1).trim()
            }
            val kind = headers["woge-kind"]?.let { value ->
                FrameKind.entries.firstOrNull { it.name == value }
            } ?: throw FrameException("Missing or invalid Woge-Kind")
            val contentType = headers["content-type"] ?: throw FrameException("Missing Content-Type")
            val metadata = try {
                Base64.getUrlDecoder().decode(headers["woge-metadata"] ?: "")
                    .decodeUtf8("metadata")
            } catch (_: IllegalArgumentException) {
                throw FrameException("Invalid Woge-Metadata")
            }
            if (metadata.toByteArray(StandardCharsets.UTF_8).size > MAX_METADATA_BYTES) {
                throw FrameException("Metadata exceeds $MAX_METADATA_BYTES bytes")
            }
            val contentLength = headers["content-length"]?.toIntOrNull()
                ?: throw FrameException("Missing or invalid Content-Length")
            if (contentLength !in 0..MAX_PAYLOAD_BYTES) {
                throw FrameException("Invalid payload length: $contentLength")
            }
            return PendingPart(kind, contentType, metadata, contentLength)
        }
    }

    private data class PendingPart(
        val kind: FrameKind,
        val contentType: String,
        val metadata: String,
        val contentLength: Int,
    )
}

private fun validateMultipartSequence(frames: List<WireFrame>) {
    require(frames.isNotEmpty()) { "At least one frame is required" }
    frames.forEachIndexed { index, frame ->
        validateFrame(frame)
        require(!frame.kind.terminal || index == frames.lastIndex) { "Terminal frame must be last" }
    }
    require(frames.last().kind.terminal) { "A terminal frame is required" }
}
