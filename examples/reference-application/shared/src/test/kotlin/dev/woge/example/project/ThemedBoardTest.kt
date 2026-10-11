package dev.woge.example.project

import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.PageIdentity
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.html.HtmlSink
import dev.woge.html.renderHtml
import dev.woge.protocol.PageEpoch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemedBoardTest {
    @Test
    fun `themes share semantic markup scope and region identities`() {
        val snapshot =
            TaskBoardSnapshot(
                listOf("Ship access"),
                0,
                0,
                PageIdentity(PageEpoch.of("themed-board"), RenderIdentitySecret.of(ByteArray(32))),
            )
        val plain = renderHtml { renderTaskBoard(snapshot) }
        val brandedSnapshot =
            snapshot.copy(
                page = PageIdentity(PageEpoch.of("themed-board"), RenderIdentitySecret.of(ByteArray(32))),
            )
        val branded =
            renderHtml { renderTaskBoard(brandedSnapshot, input = TaskBoardInput(theme = BoardTheme.TAILWIND)) }
        for (html in listOf(plain, branded)) {
            assertTrue(html.contains("<th scope=\"col\">Owner</th>"))
            assertTrue(html.contains("data-woge-scope=\"woge-disclosure-card\""))
            assertTrue(html.contains("Northstar operations handbook"))
            assertTrue(html.contains("<fieldset><legend>Create a task</legend>"))
        }
        val region = Regex("data-woge-region=\"[^\"]+\"")
        assertEquals(region.findAll(plain).map { it.value }.toList(), region.findAll(branded).map { it.value }.toList())
        assertFalse(plain.contains("operations-tailwind.css"))
        assertTrue(branded.contains("operations-tailwind.css"))
    }

    @Test
    fun `filter sorting and pagination are bounded server operations`() {
        val titles = (1..20).map { "Task ${it.toString().padStart(2, '0')}" }
        assertEquals(titles.take(8), visibleBoardTitles(titles, TaskBoardInput(descending = false)))
        assertEquals(titles.drop(8).take(8), visibleBoardTitles(titles, TaskBoardInput(descending = false, page = 2)))
        assertEquals(titles.asReversed().take(8), visibleBoardTitles(titles, TaskBoardInput(descending = true)))
        assertEquals(listOf("Task 20"), visibleBoardTitles(titles, TaskBoardInput(query = "TASK 20")))
        assertEquals(emptyList<String>(), visibleBoardTitles(titles, TaskBoardInput(query = "missing")))
        assertFalse(validBoardInput(TaskBoardInput(page = 0)))
        assertFalse(validBoardInput(TaskBoardInput(query = "x".repeat(121))))
    }

    @Test
    fun `editing updates ownership priority status and title atomically without creating a second task`() =
        runTest {
            val board = TaskBoard()
            val initial = document(board)
            val epoch = Regex("name=\"epoch\" value=\"([^\"]+)\"").find(initial)!!.groupValues[1]
            val command =
                AddBoardTask(
                    "Ship access",
                    epoch,
                    0,
                    0,
                    edit = "Review the project",
                    owner = "Rhea",
                    priority = "Urgent",
                    status = "Done",
                    theme = BoardTheme.TAILWIND,
                )
            val result =
                board.action.execute(
                    PageRequest(command, context(RequestMethod.POST)),
                ) as PageResult.RegionUpdates
            assertEquals(4, result.patches.size)
            assertTrue(
                result.patches[1]
                    .html.value
                    .contains("Rhea"),
            )
            assertTrue(
                result.patches[1]
                    .html.value
                    .contains("Done"),
            )
            assertTrue(
                result.patches[2]
                    .html.value
                    .contains("name=\"edit\" value=\"Ship access\""),
            )
            assertEquals("Task updated", result.patches[3].html.value)
            val updated = document(board)
            assertTrue(updated.contains("1 tasks"))
            assertFalse(updated.contains("Review the project"))
            val stale = board.action.execute(PageRequest(command, context(RequestMethod.POST))) as PageResult.Failure
            assertEquals(FailureCategory.CONFLICT, stale.failure.category)
            val again = command.copy(title = "Ship access again", edit = "Ship access", version = 1, revision = 1)
            assertTrue(
                board.action.execute(PageRequest(again, context(RequestMethod.POST))) is PageResult.RegionUpdates,
            )
        }

    @Test
    fun `invalid delivery fields and unknown edits never mutate the board`() =
        runTest {
            val board = TaskBoard()
            val epoch = Regex("name=\"epoch\" value=\"([^\"]+)\"").find(document(board))!!.groupValues[1]
            val base = AddBoardTask("New", epoch, 0, 0)
            for (command in listOf(
                base.copy(owner = " "),
                base.copy(priority = "Invented"),
                base.copy(status = "Unknown"),
                base.copy(page = 0),
                base.copy(query = "x".repeat(121)),
            )) {
                val failure =
                    board.action.execute(
                        PageRequest(command, context(RequestMethod.POST)),
                    ) as PageResult.Failure
                assertEquals(FailureCategory.BAD_REQUEST, failure.failure.category)
            }
            val missing = board.action.execute(PageRequest(base.copy(edit = "missing"), context(RequestMethod.POST)))
            assertEquals(FailureCategory.NOT_FOUND, (missing as PageResult.Failure).failure.category)
            val page = board.page.open(PageRequest(TaskBoardInput(edit = "missing"), context(RequestMethod.GET)))
            assertEquals(FailureCategory.NOT_FOUND, (page as PageResult.Failure).failure.category)
            assertTrue(document(board).contains("Review the project"))
        }

    private suspend fun document(board: TaskBoard): String {
        val result = board.page.open(PageRequest(TaskBoardInput(), context(RequestMethod.GET))) as PageResult.Document
        val output = StringBuilder()
        result.frames.collect { it.writeTo(HtmlSink(output::append)) }
        return output.toString()
    }

    private fun context(method: RequestMethod): RequestContext =
        RequestContext(
            method,
            RequestTrace(RequestId.of("themed-test"), CorrelationId.of("themed-test")),
            security = RequestSecurity(csrf = CsrfVerification.VERIFIED),
        )
}
