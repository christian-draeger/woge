// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.renderHtml
import dev.woge.ui.LiveRegion
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatusMessageTest {
    @Test
    fun `recipe preserves native semantics and escapes text`() {
        assertTrue(renderHtml { statusMessage() }.endsWith("></div>"))
        val html = renderHtml { statusMessage("Error <&>", LiveRegion.ALERT) }
        assertTrue(html.contains("role=\"alert\""))
        assertTrue(html.contains("Error &lt;&amp;&gt;"))
    }
}
