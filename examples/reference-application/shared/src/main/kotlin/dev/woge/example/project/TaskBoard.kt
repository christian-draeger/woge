package dev.woge.example.project

import dev.woge.host.ActionExecutor
import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.PageIdentity
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.RequestContext
import dev.woge.host.RequestHeaders
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.host.WogeAction
import dev.woge.host.WogeRoute
import dev.woge.host.actionRegionUpdates
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.redirect
import dev.woge.host.regionRefresh
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.TargetRevision
import kotlinx.serialization.Serializable
import java.util.UUID

@WogeRoute("/projects/woge/tasks")
public class TaskBoardInput

@WogeRoute("/projects/woge/tasks/regions/{epoch}/{target}/{revision}/{interaction}")
public data class BoardRegionInput(
    public val epoch: String,
    public val target: String,
    public val revision: Long,
    public val interaction: Long,
    public val query: String? = null,
)

@Serializable
public data class AddBoardTask(
    public val title: String,
    public val epoch: String,
    public val version: Long,
    public val revision: Long,
    public val interaction: Long = 0,
)

public val boardTaskForm: FormDecoder<AddBoardTask> = FormDecoder(AddBoardTask.serializer())

/** The demo is a public shared board; the host rejects cross-origin POSTs before this executor. */
@WogeAction("add-board-task")
public suspend fun addBoardTask(
    command: AddBoardTask,
    context: RequestContext,
): PageResult =
    if (context.csrf != CsrfVerification.VERIFIED) {
        failure(FailureCategory.FORBIDDEN, context.correlationId)
    } else if (!validBoardTitle(command.title) ||
        !validBoardOrdering(command.revision, command.interaction) ||
        !validBoardEpoch(command.epoch)
    ) {
        failure(FailureCategory.BAD_REQUEST, context.correlationId)
    } else {
        redirect(TaskBoardRoute.url(TaskBoardInput()))
    }

private fun validBoardEpoch(value: String): Boolean =
    try {
        PageEpoch.of(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

private fun validBoardTitle(value: String): Boolean = value.isNotBlank() && value.length <= MAX_TASK_TITLE_LENGTH

private fun validBoardOrdering(
    revision: Long,
    interaction: Long,
): Boolean = revision >= 0 && interaction >= 0

private const val MAX_TASK_TITLE_LENGTH = 120

internal data class TaskBoardSnapshot(
    val titles: List<String>,
    val version: Long,
    val revision: Long,
    val page: PageIdentity,
)

/** In-memory reference workflow; one lock protects the mutation and its authoritative render inputs. */
public class TaskBoard {
    private val secret = RenderIdentitySecret.random()
    private var titles = listOf("Review the project")
    private var version = 0L

    public val page: PageUseCase<TaskBoardInput> =
        PageUseCase {
            val snapshot = synchronized(this) { snapshot(0) }
            htmlPage { renderTaskBoard(snapshot) }
        }

    public val action: ActionExecutor<AddBoardTask> =
        ActionExecutor { request ->
            val validation = AddBoardTaskAction.execute(request)
            if (validation !is PageResult.Redirect) validation else update(request)
        }

    /** Read-only public board lookup. Ordering context is not an authorization credential. */
    public val refresh: PageUseCase<BoardRegionInput> =
        PageUseCase { request ->
            val input = request.input
            val validContext =
                validBoardEpoch(input.epoch) &&
                    validBoardOrdering(input.revision, input.interaction) &&
                    input.revision != Long.MAX_VALUE
            if (!validContext || (input.query?.length ?: 0) > MAX_TASK_TITLE_LENGTH) {
                failure(FailureCategory.BAD_REQUEST, request.context.correlationId)
            } else {
                synchronized(this) {
                    val target = BoardTasksRegion.target(PageIdentity(PageEpoch.of(input.epoch), secret))
                    if (target.target.region.value != input.target) {
                        failure(FailureCategory.NOT_FOUND, request.context.correlationId)
                    } else {
                        regionRefresh(
                            TaskBoardRoute.url(TaskBoardInput()),
                            target,
                            titles.filter { it.contains(input.query.orEmpty(), ignoreCase = true) },
                            TargetRevision.of(input.revision),
                            InteractionSequence.of(input.interaction),
                        )
                    }
                }
            }
        }

    private fun update(request: PageRequest<AddBoardTask>): PageResult =
        synchronized(this) {
            val command = request.input
            if (command.version != version || command.revision == Long.MAX_VALUE || version == Long.MAX_VALUE) {
                return@synchronized failure(FailureCategory.CONFLICT, request.context.correlationId)
            }
            val newTitles = titles + command.title.trim()
            val next =
                TaskBoardSnapshot(
                    newTitles,
                    version + 1,
                    command.revision + 1,
                    PageIdentity(PageEpoch.of(command.epoch), secret),
                )
            val revision = TargetRevision.of(command.revision)
            val result =
                actionRegionUpdates(TaskBoardRoute.url(TaskBoardInput()), InteractionSequence.of(command.interaction)) {
                    replace(BoardSummaryRegion.target(next.page), next.titles.size, revision)
                    replace(BoardTasksRegion.target(next.page), next.titles, revision)
                    replace(BoardStateRegion.target(next.page), next, revision)
                    replace(BoardStatusRegion.target(next.page), "Task added", revision)
                }
            titles = newTitles
            version = next.version
            result
        }

    private fun snapshot(revision: Long): TaskBoardSnapshot =
        TaskBoardSnapshot(titles, version, revision, PageIdentity(PageEpoch.of("board-${UUID.randomUUID()}"), secret))
}

/** Called only after the host's strict Origin check; no forwarded header establishes trust. */
public fun boardActionContext(): RequestContext {
    val id = UUID.randomUUID().toString()
    return RequestContext(
        RequestMethod.POST,
        RequestTrace(RequestId.of(id), CorrelationId.of(id)),
        headers = RequestHeaders.EMPTY,
        security = RequestSecurity(csrf = CsrfVerification.VERIFIED),
    )
}
