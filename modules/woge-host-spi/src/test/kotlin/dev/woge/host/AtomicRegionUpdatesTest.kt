package dev.woge.host

import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AtomicRegionUpdatesTest {
    @Test
    fun `render session updates opt into one atomic unit without changing native behavior`() {
        val session = RenderSessions().create()
        val first = TextRegion("first")
        val second = TextRegion("second")
        val prepared =
            session.updates(applicationUrl("/page")) {
                replace(first, "<safe>")
                replace(second, "Second")
            }
        assertNull(prepared.transaction)
        val atomic = prepared.atomic(viewTransition = true)
        assertSame(prepared.nativeResult, atomic.nativeResult)
        assertSame(prepared.metadata, atomic.metadata)
        assertEquals(true, atomic.transaction!!.viewTransition)
        assertEquals(prepared.patches, atomic.transaction.patches)
        assertEquals(
            "&lt;safe&gt;",
            atomic.patches
                .first()
                .html.value,
        )
    }

    @Test
    fun `atomic envelope overhead participates in the prepared wire byte budget`() {
        val session = RenderSessions().create()
        val first = session.target(TextRegion("first"))
        val second = session.target(TextRegion("second"))
        val prepared =
            actionRegionUpdates(applicationUrl("/page")) {
                replace(first, "First")
                replace(second, "Second")
            }
        val size =
            dev.woge.protocol.PatchStreamV1
                .encode(prepared.patches)
                .size
                .toLong()
        val bounded =
            actionRegionUpdates(applicationUrl("/page"), patchStreamLimits = PatchStreamLimits(maxBytes = size)) {
                replace(first, "First")
                replace(second, "Second")
            }
        val failure = assertThrows(ResourceLimitException::class.java) { bounded.atomic() }
        assertEquals(ResourceLimit.PATCH_STREAM_BYTES, failure.exceededLimit.limit)
    }

    private class TextRegion(
        key: String,
    ) : PageRegion<String>(key) {
        override fun render(
            writer: HtmlWriter,
            input: String,
        ) {
            writer.text(input)
        }
    }
}
