package dev.woge.ui

import dev.woge.html.applicationUrl
import dev.woge.html.div
import dev.woge.html.p
import dev.woge.html.renderHtml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class PrimitivesTest {
    @Test
    fun `disclosure renders native details and server open state`() {
        val closed = renderHtml { disclosure(summary = { text("Help") }) { p { text("Body") } } }
        val open =
            renderHtml {
                disclosure(summary = { text("Help") }, open = true, attributes = { classes("card") }) {}
            }

        assertEquals("<details data-woge-ui=\"disclosure\"><summary>Help</summary><p>Body</p></details>", closed)
        assertEquals("<details data-woge-ui=\"disclosure\" open class=\"card\"><summary>Help</summary></details>", open)
    }

    @Test
    fun `dialog link keeps an ordinary fallback URL`() {
        val html =
            renderHtml {
                dialogLink(UiId("help"), applicationUrl("/board?view=help")) { text("Help") }
            }

        assertEquals("<a href=\"/board?view=help\" data-woge-dialog=\"help\">Help</a>", html)
    }

    @Test
    fun `modal dialog is named by its escaped title and closes natively`() {
        val html =
            renderHtml {
                modalDialog(UiId("help"), title = "Tips <& tricks>") {
                    dialogCloseButton { text("Close") }
                }
            }

        assertEquals(
            "<dialog id=\"help\" aria-labelledby=\"help-title\" data-woge-ui=\"dialog\">" +
                "<h2 id=\"help-title\">Tips &lt;&amp; tricks&gt;</h2>" +
                "<form method=\"dialog\"><button type=\"submit\">Close</button></form></dialog>",
            html,
        )
    }

    @Test
    fun `popover button targets a native popover`() {
        val html =
            renderHtml {
                popoverButton(UiId("options")) { text("Options") }
                popoverPanel(UiId("options")) { text("Panel") }
            }

        assertEquals(
            "<button type=\"button\" popovertarget=\"options\">Options</button>" +
                "<div id=\"options\" popover=\"auto\" data-woge-ui=\"popover\">Panel</div>",
            html,
        )
    }

    @Test
    fun `live region picks the announcement role`() {
        val html =
            renderHtml {
                div(attributes = { liveRegion() }) {}
                div(attributes = { liveRegion(LiveRegion.ALERT) }) {}
            }

        assertEquals("<div role=\"status\"></div><div role=\"alert\"></div>", html)
    }

    @Test
    fun `ids stay safe for HTML, CSS selectors and fragments`() {
        listOf("", "1st", "a b", "a\"b", "a".repeat(65)).forEach { value ->
            assertThrows<IllegalArgumentException> { UiId(value) }
        }
        assertEquals("task_help-2", UiId("task_help-2").value)
    }

    @Test
    fun `dialog module stays a small opt-in download`() {
        val source = requireNotNull(javaClass.getResourceAsStream("/static$DIALOG_MODULE_PATH")).readBytes()
        val gzip = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(source) } }.size()

        println("[woge-metrics] behavior=dialog source_bytes=${source.size} gzip_bytes=$gzip")
        assertTrue(gzip <= 1024) { "dialog.js grew to $gzip gzip bytes" }
    }
}
