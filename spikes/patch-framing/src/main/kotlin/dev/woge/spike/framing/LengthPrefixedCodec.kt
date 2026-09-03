package dev.woge.spike.framing

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

public object LengthPrefixedCodec {
    public const val mediaType: String = "application/vnd.woge.patch-stream; version=1"
    private val magic = byteArrayOf('W'.code.toByte(), 'O'.code.toByte(), 'G'.code.toByte(), 'E'.code.toByte(), 1)

    public fun encode(frames: List<WireFrame>): ByteArray {
        validateSequence(frames)
        val output = ByteArrayOutputStream().apply { write(magic) }
        frames.forEach { frame ->
            val contentType = frame.contentType.toByteArray(StandardCharsets.US_ASCII)
            val metadata = frame.metadata.toByteArray(StandardCharsets.UTF_8)
            require(contentType.size <= 255) { "Content type is too long" }
            val header = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
                .put(frame.kind.code.toByte())
                .put(contentType.size.toByte())
                .putInt(metadata.size)
                .putInt(frame.payload.size)
                .array()
            output.write(header)
            output.write(contentType)
            output.write(metadata)
            output.write(frame.payload)
        }
        return output.toByteArray()
    }

    public fun decoder(): FrameDecoder = Decoder()

    private class Decoder : FrameDecoder {
        private var buffer: ByteArray = byteArrayOf()
        private var magicRead: Boolean = false
        private var terminalRead: Boolean = false

        override fun feed(chunk: ByteArray): List<WireFrame> {
            if (terminalRead && chunk.isNotEmpty()) throw FrameException("Bytes after terminal frame")
            buffer += chunk
            val frames = mutableListOf<WireFrame>()

            if (!magicRead) {
                if (buffer.size < magic.size) return frames
                if (!buffer.startsWith(magic)) throw FrameException("Invalid Woge framing magic or version")
                buffer = buffer.dropPrefix(magic.size)
                magicRead = true
            }

            while (buffer.size >= 10 && !terminalRead) {
                val header = ByteBuffer.wrap(buffer, 0, 10).order(ByteOrder.BIG_ENDIAN)
                val kind = FrameKind.fromCode(header.get().toInt() and 0xff)
                val contentTypeSize = header.get().toInt() and 0xff
                val metadataSize = header.int
                val payloadSize = header.int
                validateLengths(metadataSize, payloadSize)
                val frameSize = 10L + contentTypeSize + metadataSize + payloadSize
                if (frameSize > Int.MAX_VALUE || buffer.size < frameSize.toInt()) return frames

                var offset = 10
                val contentType = buffer.copyOfRange(offset, offset + contentTypeSize)
                    .decodeAscii("content type")
                offset += contentTypeSize
                val metadata = buffer.copyOfRange(offset, offset + metadataSize)
                    .decodeUtf8("metadata")
                offset += metadataSize
                val payload = buffer.copyOfRange(offset, offset + payloadSize)
                val frame = WireFrame(kind, contentType, metadata, payload)
                validateFrame(frame)
                frames += frame
                buffer = buffer.dropPrefix(frameSize.toInt())
                terminalRead = kind.terminal
            }

            if (terminalRead && buffer.isNotEmpty()) throw FrameException("Bytes after terminal frame")
            return frames
        }

        override fun finish() {
            if (!magicRead) throw FrameException("Missing Woge framing preamble")
            if (buffer.isNotEmpty()) throw FrameException("Truncated Woge frame")
            if (!terminalRead) throw FrameException("Missing terminal frame")
        }
    }

    private fun validateLengths(metadataSize: Int, payloadSize: Int) {
        if (metadataSize !in 0..MAX_METADATA_BYTES) throw FrameException("Invalid metadata length: $metadataSize")
        if (payloadSize !in 0..MAX_PAYLOAD_BYTES) throw FrameException("Invalid payload length: $payloadSize")
    }
}

private fun validateSequence(frames: List<WireFrame>) {
    require(frames.isNotEmpty()) { "At least one frame is required" }
    frames.forEachIndexed { index, frame ->
        validateFrame(frame)
        require(!frame.kind.terminal || index == frames.lastIndex) { "Terminal frame must be last" }
    }
    require(frames.last().kind.terminal) { "A terminal frame is required" }
}
