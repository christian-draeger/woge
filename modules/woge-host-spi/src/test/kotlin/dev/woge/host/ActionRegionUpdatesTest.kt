package dev.woge.host

import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.moduleScript
import dev.woge.protocol.ByteSink
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.TargetRevision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ActionRegionUpdatesTest {
    private val secret = RenderIdentitySecret.of(ByteArray(32))

    @Test
    fun `region refresh prepares exactly one typed replacement with requesting ordering context`() {
        val target = TextRegion("refresh").target(page())
        val result =
            regionRefresh(
                applicationUrl("/page"),
                target,
                "<current>",
                TargetRevision.of(12),
                InteractionSequence.of(7),
            )
        val patch = result.patches.single()
        assertEquals(target.target, patch.target)
        assertEquals(12L, patch.revision.base.value)
        assertEquals(13L, patch.revision.next.value)
        assertEquals(7L, patch.interactionSequence.value)
        assertEquals("&lt;current&gt;", patch.html.value)
        assertEquals("/page", (result.nativeResult as PageResult.Redirect).location.value)
        assertThrows(IllegalArgumentException::class.java) {
            regionRefresh(
                applicationUrl("/page"),
                target,
                "Current",
                TargetRevision.of(Long.MAX_VALUE),
                InteractionSequence.INITIAL,
            )
        }
    }

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
        val native =
            org.junit.jupiter.api.Assertions.assertInstanceOf(
                PageResult.Redirect::class.java,
                result.nativeResult,
            )
        assertEquals(ResponseStatus.SEE_OTHER, native.metadata.status)
        assertEquals("/done", native.location.value)
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
        val exceeded =
            assertThrows(ResourceLimitException::class.java) {
                val other = page()
                actionRegionUpdates(applicationUrl("/done")) {
                    repeat(129) { replace(TextRegion("region-$it").target(other), "Ready") }
                }
            }
        assertEquals(ResourceLimitExceeded(ResourceLimit.PATCH_COUNT, 128), exceeded.exceededLimit)
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

    @Test
    fun `action preparation counts whole wire output including Unicode and completion`() {
        val target = TextRegion("first").target(page())
        val baseline = actionRegionUpdates(applicationUrl("/done")) { replace(target, "\u00e9") }
        var wireBytes = 0L
        val encoder = PatchStreamV1.encoder(ByteSink { wireBytes += it.size })
        baseline.patches.forEach(encoder::write)
        encoder.complete()
        val exactLimits = PatchStreamLimits(maxBytes = wireBytes)
        val exact =
            actionRegionUpdates(applicationUrl("/done"), patchStreamLimits = exactLimits) {
                replace(target, "\u00e9")
            }
        assertEquals(exactLimits, exact.patchStreamLimits)
        val exceeded =
            assertThrows(ResourceLimitException::class.java) {
                actionRegionUpdates(
                    applicationUrl("/done"),
                    patchStreamLimits = exactLimits.copy(maxBytes = wireBytes - 1),
                ) {
                    replace(target, "\u00e9")
                }
            }
        assertEquals(ResourceLimitExceeded(ResourceLimit.PATCH_STREAM_BYTES, wireBytes - 1), exceeded.exceededLimit)
    }

    @Test
    fun `action count override stops before rendering a rejected replacement and cannot hide partial failure`() {
        var rendered = false
        val rejected =
            object : PageRegion<Unit>("second") {
                override fun render(
                    writer: HtmlWriter,
                    input: Unit,
                ) {
                    rendered = true
                    writer.text("Never")
                }
            }.target(page())
        assertThrows(IllegalStateException::class.java) {
            actionRegionUpdates(applicationUrl("/done"), patchStreamLimits = PatchStreamLimits(maxPatches = 1)) {
                replace(TextRegion("first").target(page()), "Ready")
                assertThrows(ResourceLimitException::class.java) { replace(rejected, Unit) }
            }
        }
        assertFalse(rendered)
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
