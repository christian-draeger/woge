// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.p
import dev.woge.html.renderHtml
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisclosureCardTest {
    @Test
    fun `recipe preserves native semantics and escapes text`() {
        val html = renderHtml { disclosureCard("Help <&>", open = true) { p { text("Body <&>") } } }
        assertTrue(html.contains("<details data-woge-ui=\"disclosure\" open"))
        assertTrue(html.contains("Help &lt;&amp;&gt;"))
        assertTrue(html.contains("<p>Body &lt;&amp;&gt;</p>"))
    }
}
