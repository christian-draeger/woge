package dev.woge.example.project

import dev.woge.host.PageIdentity
import dev.woge.host.RenderIdentitySecret
import dev.woge.html.renderHtml
import dev.woge.protocol.PageEpoch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskBoardAccessibilityTest {
    @Test
    fun `every board view has a viewport main landmark and native skip target`() {
        for (view in listOf(null, TaskBoardView.HELP)) {
            val html = renderHtml { renderTaskBoard(snapshot(), view) }
            assertTrue(html.contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"))
            assertTrue(html.contains("href=\"#board-main\""))
            assertTrue(html.contains("<main id=\"board-main\" tabindex=\"-1\""))
            assertEquals(1, Regex("<main\\b").findAll(html).count())
            assertTrue(html.contains("<label for=\"task-title\">Task title</label>"))
        }
    }

    @Test
    fun `only user action outcomes are live and the unrequested activity notice stays silent`() {
        val html = renderHtml { renderTaskBoard(snapshot()) }
        val activity = Regex("""<div[^>]*id="board-activity"[^>]*>""").find(html)!!.value
        assertFalse(activity.contains("role="))
        assertFalse(activity.contains("aria-live="))
        assertEquals(1, Regex("role=\"status\"").findAll(html).count())
        assertEquals(1, Regex("role=\"alert\"").findAll(html).count())
        assertTrue(html.contains("id=\"board-alert\" role=\"alert\"></p>"))
        assertTrue(html.contains("id=\"board-status\" role=\"status\"></p>"))
    }

    private fun snapshot() =
        TaskBoardSnapshot(
            listOf("Task"),
            0,
            0,
            PageIdentity(PageEpoch.of("audit-page"), RenderIdentitySecret.of(ByteArray(32))),
        )
}
