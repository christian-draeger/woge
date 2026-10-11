package dev.woge.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class PatchStreamCompressionTest {
    @Test
    fun `content decoded gzip stream survives one-byte reads`() {
        val patch =
            ReplacePatch(
                patchId = PatchId.of("compressed-1"),
                target = PatchTarget(PageEpoch.of("epoch-a"), RegionTargetId.of("summary")),
                interactionSequence = InteractionSequence.INITIAL,
                revision = TargetRevisionStep.after(TargetRevision.INITIAL),
                html = patchHtml { element("p") { text("UTF-8: \uD83D\uDC3A\r\nboundary-like: --woge--") } },
            )
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { it.write(PatchStreamV1.encode(listOf(patch))) }
        val decoded = GZIPInputStream(compressed.toByteArray().inputStream()).use { it.readBytes() }
        val decoder = PatchStreamV1.decoder()
        val events = decoded.flatMap { decoder.feed(byteArrayOf(it)) }
        decoder.finish()

        assertEquals(2, events.size)
        val frame = assertInstanceOf(PatchStreamEvent.PatchFrame::class.java, events[0])
        assertArrayEquals(PatchStreamV1.encode(listOf(patch)), PatchStreamV1.encode(listOf(frame.patch)))
        assertEquals(PatchStreamEvent.Complete(1), events[1])
    }
}
