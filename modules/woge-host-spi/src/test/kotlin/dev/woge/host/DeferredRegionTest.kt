package dev.woge.host

import dev.woge.html.renderHtml
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.patchHtml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DeferredRegionTest {
    @Test
    fun `placeholder renders ordinary loading HTML without starting deferred work`() {
        var contentStarted = false
        val target =
            PatchTarget(
                pageEpoch = PageEpoch.of("page-1"),
                region = RegionTargetId.of("summary-1"),
            )
        val region =
            deferredRegion(
                target = target,
                loading = { element("p") { text("Loading projects…") } },
                onFailure = { patchHtml { element("p") { text("Projects are unavailable") } } },
                content = {
                    contentStarted = true
                    patchHtml { element("p") { text("3 open projects") } }
                },
            )

        val html =
            renderHtml {
                regionPlaceholder(region, elementName = "section") {
                    classes("project-summary")
                    aria("label", "Project summary")
                }
            }

        assertEquals(
            "<section data-woge-region=\"summary-1\" data-woge-revision=\"0\" " +
                "class=\"project-summary\" aria-label=\"Project summary\">" +
                "<p>Loading projects…</p></section>",
            html,
        )
        assertEquals(target, region.target)
        assertFalse(contentStarted)
    }
}
