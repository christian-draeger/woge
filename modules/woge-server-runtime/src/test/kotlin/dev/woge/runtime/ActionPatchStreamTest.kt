package dev.woge.runtime

import dev.woge.host.PageIdentity
import dev.woge.host.PageRegion
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.actionRegionUpdates
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionPatchStreamTest {
    @Test
    fun `prepared action streams have ordered frames and a terminal without rendering again`() =
        runTest {
            var renders = 0
            val page = PageIdentity(PageEpoch.of("page-1"), RenderIdentitySecret.of(ByteArray(32)))

            fun target(name: String) =
                object : PageRegion<String>(name) {
                    override fun render(
                        writer: HtmlWriter,
                        input: String,
                    ) {
                        renders++
                        writer.text(input)
                    }
                }.target(page)
            val result =
                actionRegionUpdates(applicationUrl("/done")) {
                    replace(target("first"), "First")
                    replace(target("second"), "Second")
                }
            assertEquals(2, renders)
            repeat(2) {
                val chunks = result.encodeActionPatchStream().toList()
                assertEquals(4, chunks.size)
                assertTrue(chunks.last().terminal)
                val decoder = PatchStreamV1.decoder()
                val events = chunks.flatMap { decoder.feed(it.bytes) }
                decoder.finish()
                assertEquals(
                    listOf("First", "Second"),
                    events.filterIsInstance<PatchStreamEvent.PatchFrame>().map {
                        assertInstanceOf(ReplacePatch::class.java, it.patch).html.value
                    },
                )
                assertEquals(PatchStreamEvent.Complete(2), events.last())
            }
            assertEquals(2, renders)
        }
}
