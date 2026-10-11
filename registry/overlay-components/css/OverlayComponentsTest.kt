// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.applicationUrl
import dev.woge.html.renderHtml
import dev.woge.ui.UiId
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlayComponentsTest {
    @Test
    fun `modal and drawer share native focus escape and fallback contracts`() {
        val id = UiId("help")
        val link = renderHtml { overlayLink(id, applicationUrl("/help"), "Help") }
        assertTrue(link.contains("href=\"/help\""))
        assertTrue(link.contains("data-woge-dialog=\"help\""))
        assertTrue(link.contains("data-woge-state-key=\"help-trigger\""))
        val dialogs =
            listOf(
                renderHtml { dialogComponent(id, "Help <&>", "Close") { text("Body") } },
                renderHtml { drawerComponent(id, "Help <&>", "Close") { text("Body") } },
            )
        dialogs.forEach { html ->
            assertTrue(html.startsWith("<dialog id=\"help\" aria-labelledby=\"help-title\""))
            assertTrue(html.contains("id=\"help-title\">Help &lt;&amp;&gt;"))
            assertTrue(html.contains("<form method=\"dialog\">"))
            assertTrue(html.contains("autofocus>Close</button>"))
            assertTrue(!html.contains(" open") && !html.contains("<script"))
        }
    }

    @Test
    fun `popover and tooltip keep native targets and explicit help description`() {
        val popover = renderHtml { popoverComponent(UiId("options"), "Options") { text("Panel") } }
        val tooltip = renderHtml { tooltipComponent(UiId("tip"), "Help", "Description <&>") }
        assertTrue(popover.contains("type=\"button\" popovertarget=\"options\""))
        assertTrue(popover.contains("id=\"options\" popover=\"auto\""))
        assertTrue(tooltip.contains("aria-describedby=\"tip\""))
        assertTrue(tooltip.contains("role=\"tooltip\""))
        assertTrue(tooltip.contains("Description &lt;&amp;&gt;"))
    }
}
