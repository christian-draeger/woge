package dev.woge.host

import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.moduleScript
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.TargetRevision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ActionRegionUpdatesTest {
    private val secret = RenderIdentitySecret.of(ByteArray(32))

    @Test
    fun `typed updates preserve declaration order escape markup and advance explicit revisions`() {
        val page = page()
        val first = TextRegion("first").target(page)
        val second = TextRegion("second").target(page)
        val result =
            actionRegionUpdates(applicationUrl("/done"), interaction = InteractionSequence.of(2)) {
                replace(first, "<private>", revision = TargetRevision.of(7))
                replace(second, "Saved")
            }

        assertEquals(listOf(first.target, second.target), result.patches.map { it.target })
        assertEquals(
            "&lt;private&gt;",
            result.patches
                .first()
                .html.value,
        )
        assertEquals(InteractionSequence.of(2), result.patches.first().interactionSequence)
        assertEquals(
            7L,
            result.patches
                .first()
                .revision.base.value,
        )
        assertEquals(
            8L,
            result.patches
                .first()
                .revision.next.value,
        )
        assertEquals(ResponseStatus.SEE_OTHER, result.nativeRedirect().metadata.status)
        assertEquals("/done", result.nativeRedirect().location.value)
    }

    @Test
    fun `empty duplicate and mixed page updates fail before a result exists`() {
        assertThrows(IllegalArgumentException::class.java) { actionRegionUpdates(applicationUrl("/done")) {} }
        val target = TextRegion("first").target(page())
        assertThrows(IllegalArgumentException::class.java) {
            actionRegionUpdates(applicationUrl("/done")) {
                replace(target, "First")
                replace(target, "Second")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            actionRegionUpdates(applicationUrl("/done")) {
                replace(target, "First")
                replace(TextRegion("second").target(page("other")), "Second")
            }
        }
    }

    @Test
    fun `render failure aborts preparation and cannot be swallowed into partial success`() {
        val broken =
            object : PageRegion<String>("broken") {
                override fun render(
                    writer: HtmlWriter,
                    input: String,
                ) {
                    error("Render failed")
                }
            }.target(page())
        assertThrows(IllegalStateException::class.java) {
            actionRegionUpdates(applicationUrl("/done")) {
                replace(TextRegion("first").target(page()), "Ready")
                assertThrows(IllegalStateException::class.java) { replace(broken, "Broken") }
            }
        }
    }

    @Test
    fun `replacement limit is inclusive and results cannot be extended through a retained builder`() {
        val page = page()
        var retained: ActionRegionUpdates? = null
        val result =
            actionRegionUpdates(applicationUrl("/done")) {
                retained = this
                repeat(128) { replace(TextRegion("region-$it").target(page), "Ready") }
            }
        assertEquals(128, result.patches.size)
        assertThrows(IllegalStateException::class.java) {
            requireNotNull(retained).replace(TextRegion("extra").target(page), "Late")
        }
        assertThrows(IllegalArgumentException::class.java) {
            val other = page()
            actionRegionUpdates(applicationUrl("/done")) {
                repeat(129) { replace(TextRegion("region-$it").target(other), "Ready") }
            }
        }
    }

    @Test
    fun `disallowed patch markup fails during preparation rather than streaming`() {
        val unsafe =
            object : PageRegion<Unit>("unsafe") {
                override fun render(
                    writer: HtmlWriter,
                    input: Unit,
                ) {
                    writer.moduleScript(applicationUrl("/app.js"))
                }
            }.target(page())
        assertThrows(dev.woge.protocol.PatchStreamException::class.java) {
            actionRegionUpdates(applicationUrl("/done")) { replace(unsafe, Unit) }
        }
    }

    private fun page(epoch: String = "page-1") = PageIdentity(PageEpoch.of(epoch), secret)

    private class TextRegion(
        name: String,
    ) : PageRegion<String>(name) {
        override fun render(
            writer: HtmlWriter,
            input: String,
        ) {
            writer.text(input)
        }
    }
}
