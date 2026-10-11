package dev.woge.runtime

import dev.woge.html.UnsafeWogeHtmlApi
import dev.woge.html.unsafeHtml
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.TargetRevision
import dev.woge.protocol.TargetRevisionStep
import dev.woge.protocol.patchHtml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeDpuEncoderTest {
    @Test
    fun `encodes replace IR with an opaque native range template`() {
        val name = newNativeDpuName()
        val patch = replace { element("p") { text("Ready") } }
        assertTrue(name.value.matches(Regex("woge-[0-9a-f-]{36}")))
        assertEquals(
            "<?start name=\"${name.value}\"><p>Loading</p><?end>",
            NativeDpuEncoder.encodeRangeStart(name, patchHtml { element("p") { text("Loading") } }),
        )
        assertEquals(
            "<template for=\"${name.value}\"><p>Ready</p></template>",
            NativeDpuEncoder.encodeReplace(patch, name),
        )
        assertNotEquals(newNativeDpuName(), name)
    }

    @Test
    fun `mode selection requires opt in signal and eligible content`() {
        val html = replace { element("p") { text("Ready") } }.html
        assertFalse(NativeDpuEncoder.isSelected(NativeDpuMode.OFF, "woge_dpu=1", html))
        assertFalse(NativeDpuEncoder.isSelected(NativeDpuMode.AUTO, null, html))
        assertFalse(NativeDpuEncoder.isSelected(NativeDpuMode.AUTO, "woge_dpu=0", html))
        assertFalse(NativeDpuEncoder.isSelected(NativeDpuMode.AUTO, "woge_dpu=10", html))
        assertFalse(NativeDpuEncoder.isSelected(NativeDpuMode.AUTO, "woge_dpu=1; woge_dpu=0", html))
        assertTrue(NativeDpuEncoder.isSelected(NativeDpuMode.AUTO, "other=x; woge_dpu=1", html))
        assertTrue(NativeDpuEncoder.isSelected(NativeDpuMode.FORCE_NATIVE, null, html))
        assertFalse(NativeDpuEncoder.isSelected(NativeDpuMode.FORCE_FALLBACK, "woge_dpu=1", html))
    }

    @OptIn(UnsafeWogeHtmlApi::class)
    @Test
    fun `active content and raw unsafe markup are not eligible`() {
        val hostile =
            listOf(
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "<iframe srcdoc='<script>alert(1)</script>'></iframe>",
                "<a href='javascript:alert(1)'>link</a>",
                "<p style='background:url(javascript:alert(1))'>text</p>",
            )
        hostile.forEach { html -> assertFalse(NativeDpuEncoder.isEligible(rawPatch(html).html), html) }
        assertFalse(
            NativeDpuEncoder.isSelected(NativeDpuMode.FORCE_NATIVE, "woge_dpu=1", rawPatch("<script>x</script>").html),
        )
        assertThrows(IllegalArgumentException::class.java) {
            NativeDpuEncoder.encodeReplace(replace { raw(unsafeHtml("<script>x</script>")) }, newNativeDpuName())
        }
        assertFalse(NativeDpuEncoder.isEligible(rawPatch("<strong>raw</strong>").html))
    }

    private fun replace(content: dev.woge.html.HtmlWriter.() -> Unit): ReplacePatch =
        ReplacePatch(
            patchId = PatchId.of("patch"),
            target = PatchTarget(PageEpoch.of("page"), RegionTargetId.of("region")),
            interactionSequence = InteractionSequence.INITIAL,
            revision = TargetRevisionStep.after(TargetRevision.INITIAL),
            html = patchHtml(content),
        )

    @OptIn(UnsafeWogeHtmlApi::class)
    private fun rawPatch(markup: String): ReplacePatch = replace { raw(unsafeHtml(markup)) }
}
