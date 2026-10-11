// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.renderHtml
import dev.woge.ui.LiveRegion
import dev.woge.ui.UiId
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedbackComponentsTest {
    @Test
    fun `feedback is static except empty stable announcement roots`() {
        val alert = renderHtml { alertComponent("Problem <&>") }
        val toast = renderHtml { toastComponent(UiId("toast")) { text("Saved") } }
        assertTrue(alert.contains("Problem &lt;&amp;&gt;"))
        assertTrue(!alert.contains("role=") && !toast.contains("role="))
        assertTrue(renderHtml { skeletonComponent() }.contains("aria-hidden=\"true\""))
        for (kind in LiveRegion.entries) {
            val html = renderHtml { announcementRegion(UiId("announcements"), kind) }
            assertTrue(html.contains("data-woge-region=\"announcements\" data-woge-revision=\"0\""))
            assertTrue(html.contains("aria-atomic=\"true\""))
            assertTrue(html.contains("role=\"${if (kind == LiveRegion.STATUS) "status" else "alert"}\""))
            assertTrue(html.endsWith("></div>"))
        }
    }

    @Test
    fun `native progress is named and validates determinate values`() {
        val pending = renderHtml { progressComponent("Loading") }
        val progress = renderHtml { progressComponent("Uploading", 25.0) }
        assertTrue(pending.startsWith("<progress aria-label=\"Loading\" max=\"100.0\""))
        assertTrue(!pending.contains(" value="))
        assertTrue(progress.contains("value=\"25.0\""))
        for (value in listOf(-1.0, 101.0, Double.NaN)) {
            assertThrows(IllegalArgumentException::class.java) { renderHtml { progressComponent("Loading", value) } }
        }
    }
}
