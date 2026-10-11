package dev.woge.runtime

import dev.woge.host.PageRegion
import dev.woge.host.RenderSessions
import dev.woge.host.atomic
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.PatchTransaction
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class AtomicActionPatchStreamTest {
    @Test
    fun `all hosts receive one atomic chunk from the shared encoder without rerendering`() =
        runTest {
            val session = RenderSessions().create()
            val region = CountingRegion()
            val updates = session.updates(applicationUrl("/page")) { replace(region, "Safe") }.atomic(true)
            val chunks = updates.encodeActionPatchStream().toList()
            assertEquals(3, chunks.size)
            assertEquals(listOf(false, false, true), chunks.map { it.terminal })
            val decoder = PatchStreamV1.decoder()
            val events = chunks.flatMap { decoder.feed(it.bytes) }
            decoder.finish()
            val transaction =
                assertInstanceOf(
                    PatchTransaction::class.java,
                    (events.first() as PatchStreamEvent.PatchFrame).patch,
                )
            assertEquals(true, transaction.viewTransition)
            assertEquals(1, region.renders)
            assertEquals(PatchStreamEvent.Complete(1), events.last())
        }

    private class CountingRegion : PageRegion<String>("test") {
        var renders = 0

        override fun render(
            writer: HtmlWriter,
            input: String,
        ) {
            renders++
            writer.text(input)
        }
    }
}
