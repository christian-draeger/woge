package dev.woge.registry

import dev.woge.html.p
import dev.woge.html.renderHtml
import dev.woge.registry.css.disclosureCard
import dev.woge.registry.css.statusMessage
import dev.woge.ui.LiveRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.woge.registry.tailwind.disclosureCard as tailwindDisclosureCard

class RegistryComponentsTest {
    @Test
    fun `every disclosure variant preserves native semantics and escaped content`() {
        val css =
            renderHtml {
                disclosureCard("Help <&>", open = true) { p { text("Body <&>") } }
            }
        val tailwind =
            renderHtml {
                tailwindDisclosureCard("Help <&>", open = true) { p { text("Body <&>") } }
            }
        assertEquals(css.replace(Regex(" class=\"[^\"]*\""), ""), tailwind.replace(Regex(" class=\"[^\"]*\""), ""))
        assertTrue(css.contains("<details data-woge-ui=\"disclosure\" open"))
        assertTrue(css.contains("Help &lt;&amp;&gt;"))
        assertTrue(css.contains("<p>Body &lt;&amp;&gt;</p>"))
        assertTrue(renderHtml { disclosureCard("Closed") {} }.contains("<summary"))
    }

    @Test
    fun `status starts empty and exposes typed live region semantics`() {
        val empty = renderHtml { statusMessage() }
        assertTrue(empty.contains("role=\"status\""))
        assertTrue(empty.endsWith("></div>"))
        val alert = renderHtml { statusMessage("Error <&>", LiveRegion.ALERT) }
        assertTrue(alert.contains("role=\"alert\""))
        assertTrue(alert.contains("data-woge-scope=\"woge-status-message\""))
        assertTrue(alert.contains("Error &lt;&amp;&gt;"))
    }
}
