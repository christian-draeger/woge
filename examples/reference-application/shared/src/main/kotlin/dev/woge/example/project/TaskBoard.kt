package dev.woge.example.project

import dev.woge.host.ActionExecutor
import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.LiveUseCase
import dev.woge.host.PageIdentity
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.RequestContext
import dev.woge.host.WogeAction
import dev.woge.host.WogeRoute
import dev.woge.host.actionRegionUpdates
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.liveRefused
import dev.woge.host.liveSubscription
import dev.woge.host.redirect
import dev.woge.host.regionRefresh
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.TargetRevision
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID

/** `HELP` renders the board help as a normal page section: the no-JavaScript fallback of the help dialog. */
public enum class TaskBoardView {
    HELP,
}

/** The typed URL `/projects/woge/tasks?view=help` of the task board. */
@WogeRoute("/projects/woge/tasks")
public data class TaskBoardInput(
    public val view: TaskBoardView? = null,
    public val theme: BoardTheme? = null,
    public val query: String? = null,
    public val descending: Boolean? = null,
    public val page: Int? = null,
    public val edit: String? = null,
)

@WogeRoute("/projects/woge/tasks/regions/{epoch}/{target}/{revision}/{interaction}")
public data class BoardRegionInput(
    public val epoch: String,
    public val target: String,
    public val revision: Long,
    public val interaction: Long,
    public val query: String? = null,
)

/** The board's live stream for one rendered page. */
@WogeRoute("/projects/woge/tasks/live/{epoch}")
public data class BoardLiveInput(
    public val epoch: String,
)

/** Safe GET for the live activity notice; [since] is the board version the page already shows. */
@WogeRoute("/projects/woge/tasks/activity/{epoch}/{target}/{revision}/{interaction}")
public data class BoardActivityInput(
    public val epoch: String,
    public val target: String,
    public val revision: Long,
    public val interaction: Long,
    public val since: Long? = null,
)

@Serializable
public data class AddBoardTask(
    public val title: String,
    public val epoch: String,
    public val version: Long,
    public val revision: Long,
    public val interaction: Long = 0,
    public val edit: String = "",
    public val owner: String = "Team",
    public val priority: String = "Normal",
    public val status: String = "Open",
    public val theme: BoardTheme? = null,
    public val query: String? = null,
    public val descending: Boolean? = null,
    public val page: Int? = null,
)

public val boardTaskForm: FormDecoder<AddBoardTask> = FormDecoder(AddBoardTask.serializer())

/** The demo is a public shared board; the host verifies same-origin POSTs before this executor. */
@WogeAction("add-board-task")
public suspend fun addBoardTask(
    command: AddBoardTask,
    context: RequestContext,
): PageResult =
    if (context.csrf != CsrfVerification.VERIFIED) {
        failure(FailureCategory.FORBIDDEN, context.correlationId)
    } else if (!validBoardContent(command) ||
        !validBoardOrdering(command.revision, command.interaction) ||
        !validBoardEpoch(command.epoch)
    ) {
        failure(FailureCategory.BAD_REQUEST, context.correlationId)
    } else {
        redirect(TaskBoardRoute.url(command.presentation()))
    }

private fun validBoardEpoch(value: String): Boolean =
    try {
        PageEpoch.of(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

private fun validBoardContent(command: AddBoardTask): Boolean =
    command.title.isNotBlank() &&
        command.title.length <= MAX_TASK_TITLE_LENGTH &&
        validBoardDetails(command) &&
        validBoardInput(command.presentation())

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
    val details: Map<String, BoardTaskDetails> = emptyMap(),
    val edit: String = "",
)

/** In-memory reference workflow; one lock protects the mutation and its authoritative render inputs. */
public class TaskBoard {
    private val secret = RenderIdentitySecret.random()
    private var titles = listOf("Review the project")
    private var details = emptyMap<String, BoardTaskDetails>()
    private var version = 0L
    private val changes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    public val page: PageUseCase<TaskBoardInput> =
        PageUseCase { request ->
            val snapshot = synchronized(this) { snapshot(0) }
            if (!validBoardInput(request.input)) {
                failure(FailureCategory.BAD_REQUEST, request.context.correlationId)
            } else if (request.input.edit != null && request.input.edit !in snapshot.titles) {
                failure(FailureCategory.NOT_FOUND, request.context.correlationId)
            } else {
                htmlPage {
                    renderTaskBoard(
                        snapshot.copy(edit = request.input.edit.orEmpty()),
                        request.input.view,
                        request.input,
                    )
                }
            }
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
                            BoardTaskRows(
                                titles.filter { it.contains(input.query.orEmpty(), ignoreCase = true) },
                                details,
                            ),
                            TargetRevision.of(input.revision),
                            InteractionSequence.of(input.interaction),
                        )
                    }
                }
            }
        }

    /**
     * Tells open pages that the board changed. The stream sends only the activity region id; the
     * browser loads the notice through [activity], so normal pages never depend on it.
     */
    public val live: LiveUseCase<BoardLiveInput> =
        LiveUseCase { request ->
            if (!validBoardEpoch(request.input.epoch)) {
                liveRefused(FailureCategory.BAD_REQUEST, request.context.correlationId)
            } else {
                val target = BoardActivityRegion.target(PageIdentity(PageEpoch.of(request.input.epoch), secret))
                liveSubscription(listOf(target), changes.map { target })
            }
        }

    /** Read-only notice of tasks added after the version the page shows. */
    public val activity: PageUseCase<BoardActivityInput> =
        PageUseCase { request ->
            val input = request.input
            val validContext =
                validBoardEpoch(input.epoch) &&
                    validBoardOrdering(input.revision, input.interaction) &&
                    input.revision != Long.MAX_VALUE &&
                    (input.since ?: 0) >= 0
            if (!validContext) {
                failure(FailureCategory.BAD_REQUEST, request.context.correlationId)
            } else {
                synchronized(this) {
                    val target = BoardActivityRegion.target(PageIdentity(PageEpoch.of(input.epoch), secret))
                    if (target.target.region.value != input.target) {
                        failure(FailureCategory.NOT_FOUND, request.context.correlationId)
                    } else {
                        regionRefresh(
                            TaskBoardRoute.url(TaskBoardInput()),
                            target,
                            (version - (input.since ?: version)).coerceAtLeast(0),
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
            val original = command.edit.takeIf { it.isNotEmpty() }
            if (original != null && original !in titles) {
                return@synchronized failure(FailureCategory.NOT_FOUND, request.context.correlationId)
            }
            val title = command.title.trim()
            if (title in titles && title != original) {
                return@synchronized failure(FailureCategory.CONFLICT, request.context.correlationId)
            }
            val newTitles = if (original == null) titles + title else titles.map { if (it == original) title else it }
            val newDetails =
                (details - command.edit) +
                    (title to BoardTaskDetails(command.owner.trim(), command.priority, command.status))
            val next =
                TaskBoardSnapshot(
                    newTitles,
                    version + 1,
                    command.revision + 1,
                    PageIdentity(PageEpoch.of(command.epoch), secret),
                    newDetails,
                    if (original == null) "" else title,
                )
            val revision = TargetRevision.of(command.revision)
            val destination = command.presentation(original?.let { title })
            val result =
                actionRegionUpdates(
                    TaskBoardRoute.url(destination),
                    InteractionSequence.of(command.interaction),
                ) {
                    replace(BoardSummaryRegion.target(next.page), next.titles.size, revision)
                    replace(
                        BoardTasksRegion.target(next.page),
                        BoardTaskRows(next.titles, newDetails, destination),
                        revision,
                    )
                    replace(BoardStateRegion.target(next.page), next, revision)
                    replace(
                        BoardStatusRegion.target(next.page),
                        if (original == null) "Task added" else "Task updated",
                        revision,
                    )
                }
            titles = newTitles
            details = newDetails
            version = next.version
            changes.tryEmit(Unit)
            result
        }

    private fun snapshot(revision: Long): TaskBoardSnapshot =
        TaskBoardSnapshot(
            titles,
            version,
            revision,
            PageIdentity(PageEpoch.of("board-${UUID.randomUUID()}"), secret),
            details,
        )
}
