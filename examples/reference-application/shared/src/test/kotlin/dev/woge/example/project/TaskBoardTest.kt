package dev.woge.example.project

import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.LiveResult
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class TaskBoardTest {
    @Test
    fun `region refresh rejects invalid context and never mutates domain data`() =
        runTest {
            val board = TaskBoard()
            val html = document(board)
            val epoch = Regex("""name="epoch" value="([^"]+)"""").find(html)!!.groupValues[1]
            val target = Regex("""data-woge-region="([^"]+)"[^>]*><ul id="board-tasks"""").find(html)!!.groupValues[1]
            val context =
                RequestContext(RequestMethod.GET, RequestTrace(RequestId.of("refresh"), CorrelationId.of("refresh")))
            val result =
                board.refresh.open(
                    PageRequest(BoardRegionInput(epoch, target, 5, 3, "Review"), context),
                ) as PageResult.RegionUpdates
            assertEquals(
                5L,
                result.patches
                    .single()
                    .revision.base.value,
            )
            assertEquals(
                3L,
                result.patches
                    .single()
                    .interactionSequence.value,
            )
            assertEquals(
                true,
                result.patches
                    .single()
                    .html.value
                    .contains("Review the project"),
            )
            for (input in listOf(
                BoardRegionInput(epoch, target, -1, 3),
                BoardRegionInput(epoch, target, Long.MAX_VALUE, 3),
                BoardRegionInput(epoch, target, 0, -1),
                BoardRegionInput("", target, 0, 3),
                BoardRegionInput(epoch, target, 0, 3, "a".repeat(121)),
            )) {
                val failure = board.refresh.open(PageRequest(input, context)) as PageResult.Failure
                assertEquals(FailureCategory.BAD_REQUEST, failure.failure.category)
            }
            val unknown =
                board.refresh.open(
                    PageRequest(BoardRegionInput(epoch, "unknown", 0, 3), context),
                ) as PageResult.Failure
            assertEquals(FailureCategory.NOT_FOUND, unknown.failure.category)
            assertEquals(
                html.substringAfter("<ul").substringBefore("</ul>"),
                document(board).substringAfter("<ul").substringBefore("</ul>"),
            )
        }

    @Test
    fun `one mutation prepares ordered authoritative regions and stale submissions never mutate again`() =
        runTest {
            val board = TaskBoard()
            val html = document(board)
            val epoch = Regex("""name="epoch" value="([^"]+)"""").find(html)!!.groupValues[1]
            val command = AddBoardTask("A new task", epoch, 0, 0, interaction = 2)
            val result =
                assertInstanceOf(
                    PageResult.RegionUpdates::class.java,
                    board.action.execute(PageRequest(command, boardActionContext())),
                )

            assertEquals(4, result.patches.size)
            assertEquals(
                4,
                result.patches
                    .map { it.target.region }
                    .toSet()
                    .size,
            )
            assertEquals(listOf(0L, 0L, 0L, 0L), result.patches.map { it.revision.base.value })
            assertEquals(listOf(1L, 1L, 1L, 1L), result.patches.map { it.revision.next.value })
            assertEquals(listOf(2L, 2L, 2L, 2L), result.patches.map { it.interactionSequence.value })
            assertEquals(
                true,
                result.patches[0]
                    .html.value
                    .contains("2 tasks"),
            )
            assertEquals(
                true,
                result.patches[1]
                    .html.value
                    .contains("A new task"),
            )
            assertEquals(
                true,
                result.patches[2]
                    .html.value
                    .contains("name=\"revision\" value=\"1\""),
            )
            assertInstanceOf(PageResult.Redirect::class.java, result.nativeResult)
            val replay =
                assertInstanceOf(
                    PageResult.Failure::class.java,
                    board.action.execute(PageRequest(command, boardActionContext())),
                )
            assertEquals(FailureCategory.CONFLICT, replay.failure.category)
            assertEquals(1, Regex("A new task").findAll(document(board)).count())
        }

    @Test
    fun `live stream announces only the activity region and the notice counts newer tasks`() =
        runTest {
            val board = TaskBoard()
            val html = document(board)
            val epoch = Regex("""name="epoch" value="([^"]+)"""").find(html)!!.groupValues[1]
            val target = Regex("""data-woge-region="([^"]+)"[^>]*id="board-activity"""").find(html)!!.groupValues[1]
            val context =
                RequestContext(RequestMethod.GET, RequestTrace(RequestId.of("live"), CorrelationId.of("live")))
            assertInstanceOf(
                LiveResult.Refused::class.java,
                board.live.subscribe(PageRequest(BoardLiveInput(""), context)),
            )
            val subscription =
                board.live.subscribe(PageRequest(BoardLiveInput(epoch), context)) as LiveResult.Subscription
            assertEquals(setOf(target), subscription.targets.map { it.value }.toSet())

            val invalidation =
                backgroundScope.async(
                    start = CoroutineStart.UNDISPATCHED,
                ) { subscription.invalidations.first() }
            board.action.execute(PageRequest(AddBoardTask("Live task", epoch, 0, 0), boardActionContext()))
            assertEquals(target, invalidation.await().value)

            suspend fun notice(since: Long?) =
                (
                    board.activity.open(
                        PageRequest(BoardActivityInput(epoch, target, 0, 1, since), context),
                    ) as PageResult.RegionUpdates
                ).patches
                    .single()
                    .html.value
            assertEquals(true, notice(0).contains("1 new task was added."))
            assertEquals(false, notice(1).contains("new task"))
            assertEquals(false, notice(null).contains("new task"))
            val invalid = board.activity.open(PageRequest(BoardActivityInput(epoch, target, 0, 1, -1), context))
            assertEquals(FailureCategory.BAD_REQUEST, (invalid as PageResult.Failure).failure.category)
            val unknown = board.activity.open(PageRequest(BoardActivityInput(epoch, "unknown", 0, 1), context))
            assertEquals(FailureCategory.NOT_FOUND, (unknown as PageResult.Failure).failure.category)
        }

    @Test
    fun `invalid commands and unverified security do not change the board`() =
        runTest {
            val board = TaskBoard()
            val epoch = Regex("""name="epoch" value="([^"]+)"""").find(document(board))!!.groupValues[1]
            val invalid = board.action.execute(PageRequest(AddBoardTask(" ", epoch, 0, 0), boardActionContext()))
            assertEquals(FailureCategory.BAD_REQUEST, (invalid as PageResult.Failure).failure.category)
            val context =
                RequestContext(
                    RequestMethod.POST,
                    RequestTrace(RequestId.of("request"), CorrelationId.of("request")),
                )
            val forbidden = board.action.execute(PageRequest(AddBoardTask("No", epoch, 0, 0), context))
            assertEquals(FailureCategory.FORBIDDEN, (forbidden as PageResult.Failure).failure.category)
            assertEquals(false, document(board).contains("<li>No</li>"))
        }

    private suspend fun document(board: TaskBoard): String {
        val result =
            board.page.open(
                PageRequest(
                    TaskBoardInput(),
                    RequestContext(
                        RequestMethod.GET,
                        RequestTrace(RequestId.of("page"), CorrelationId.of("page")),
                    ),
                ),
            )
        val output = StringBuilder()
        (result as PageResult.Document).frames.collect { it.writeTo(dev.woge.html.HtmlSink(output::append)) }
        return output.toString()
    }
}

private fun boardActionContext(): RequestContext {
    val id = "task-board-test"
    return RequestContext(
        RequestMethod.POST,
        RequestTrace(RequestId.of(id), CorrelationId.of(id)),
        security = RequestSecurity(csrf = CsrfVerification.VERIFIED),
    )
}
