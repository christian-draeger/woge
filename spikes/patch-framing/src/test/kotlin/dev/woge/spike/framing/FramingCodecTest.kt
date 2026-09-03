package dev.woge.spike.framing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

public class FramingCodecTest {
    private val frames = measurementFrames()

    @Test
    public fun `length prefixed frames survive every two-chunk byte split`() {
        assertEverySplit(LengthPrefixedCodec.encode(frames), LengthPrefixedCodec::decoder)
    }

    @Test
    public fun `multipart frames survive every two-chunk byte split`() {
        val codec = MultipartCodec("woge-test-boundary")
        assertEverySplit(codec.encode(frames), codec::decoder)
    }

    @Test
    public fun `both formats survive one-byte reads and gzip transport`() {
        val multipart = MultipartCodec("woge-test-boundary")
        val cases = listOf(
            LengthPrefixedCodec.encode(frames) to LengthPrefixedCodec::decoder,
            multipart.encode(frames) to multipart::decoder,
        )

        for ((encoded, decoderFactory) in cases) {
            assertDecoded(encoded.map { byteArrayOf(it) }, decoderFactory)
            assertDecoded(gunzip(gzip(encoded)).map { byteArrayOf(it) }, decoderFactory)
        }
    }

    @Test
    public fun `safe error is an explicit terminal frame`() {
        val errorFrames = listOf(
            WireFrame.patch("{\"target\":\"summary\"}", "<p>partial</p>"),
            WireFrame.error("{\"code\":\"WOGE_RENDER_FAILED\",\"correlationId\":\"test-1\"}"),
        )

        assertDecoded(
            listOf(LengthPrefixedCodec.encode(errorFrames)),
            LengthPrefixedCodec::decoder,
            errorFrames,
        )
    }

    @Test
    public fun `truncated and unknown frames fail closed`() {
        val encoded = LengthPrefixedCodec.encode(frames)
        val truncated = LengthPrefixedCodec.decoder()
        truncated.feed(encoded.copyOf(encoded.size - 1))
        assertFailsWith<FrameException> { truncated.finish() }

        val unknown = encoded.copyOf().also { it[5] = 99 }
        assertFailsWith<FrameException> { LengthPrefixedCodec.decoder().feed(unknown) }

        val malformedMetadata = encoded.copyOf().also { bytes ->
            val contentTypeLength = bytes[6].toInt() and 0xff
            val metadataStart = 5 + 10 + contentTypeLength
            bytes[metadataStart] = 0xc3.toByte()
            bytes[metadataStart + 1] = 0x28.toByte()
        }
        assertFailsWith<FrameException> { LengthPrefixedCodec.decoder().feed(malformedMetadata) }

        val multipart = MultipartCodec("woge-test-boundary").encode(frames)
        val truncatedMultipart = MultipartCodec("woge-test-boundary").decoder()
        truncatedMultipart.feed(multipart.copyOf(multipart.size - 1))
        assertFailsWith<FrameException> { truncatedMultipart.finish() }
    }

    @Test
    public fun `oversized metadata is rejected before allocation`() {
        val header = byteArrayOf(
            'W'.code.toByte(), 'O'.code.toByte(), 'G'.code.toByte(), 'E'.code.toByte(), 1,
            FrameKind.PATCH.code.toByte(), 0,
            0, 1, 0, 1,
            0, 0, 0, 0,
        )

        assertFailsWith<FrameException> { LengthPrefixedCodec.decoder().feed(header) }
    }

    private fun assertEverySplit(encoded: ByteArray, decoderFactory: () -> FrameDecoder) {
        for (split in 0..encoded.size) {
            assertDecoded(
                listOf(encoded.copyOfRange(0, split), encoded.copyOfRange(split, encoded.size)),
                decoderFactory,
            )
        }
    }

    private fun assertDecoded(
        chunks: List<ByteArray>,
        decoderFactory: () -> FrameDecoder,
        expected: List<WireFrame> = frames,
    ) {
        val decoder = decoderFactory()
        val actual = chunks.flatMap(decoder::feed)
        decoder.finish()
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedFrame, actualFrame) ->
            assertEquals(expectedFrame.kind, actualFrame.kind)
            assertEquals(expectedFrame.contentType, actualFrame.contentType)
            assertEquals(expectedFrame.metadata, actualFrame.metadata)
            assertContentEquals(expectedFrame.payload, actualFrame.payload)
        }
    }

    private fun gzip(value: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(value) }
        output.toByteArray()
    }

    private fun gunzip(value: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(value)).use { it.readAllBytes() }
}
