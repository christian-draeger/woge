package dev.woge.example.project

import dev.woge.host.DeferredRegion
import dev.woge.host.DeferredRegionFailure
import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.FailureCategory
import dev.woge.host.PageIdentity
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RegionTarget
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.WogeRoute
import dev.woge.host.deferredRegion
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/** Selects the immediate enhanced shell or a complete full-navigation response. */
public enum class ProjectPageView {
    SHELL,
    COMPLETE,
}

/**
 * The typed URL `/projects/{project}?view=complete` of the project page.
 *
 * KSP generates `ProjectPageRoute` from this class. Links use `ProjectPageRoute.url(...)` and every
 * host decodes requests with the same route. A missing `view` means the enhanced shell.
 */
@WogeRoute("/projects/{project}")
public data class ProjectPageInput(
    public val project: String,
    public val view: ProjectPageView? = null,
)

/** Carries the rendered document's epoch back to the public demo's deferred GET. Not authorization. */
@WogeRoute("/projects/{project}/woge-patches/{epoch}")
public data class ProjectPatchesInput(
    public val project: String,
    public val epoch: UUID,
)

/**
 * Host-neutral project page used unchanged by the Spring MVC, WebFlux and Ktor launchers.
 *
 * [identitySecret] turns region names into opaque IDs. Use one shared secret per deployment.
 */
public class ProjectPage(
    private val identitySecret: RenderIdentitySecret = RenderIdentitySecret.random(),
) : PageUseCase<ProjectPageInput>,
    DeferredRegionsUseCase<ProjectPatchesInput> {
    override suspend fun open(request: PageRequest<ProjectPageInput>): PageResult {
        val project =
            findProject(request.input.project)
                ?: return failure(FailureCategory.NOT_FOUND, request.context.correlationId)
        val view = request.input.view ?: ProjectPageView.SHELL
        val epoch = UUID.randomUUID()
        val regions = if (view == ProjectPageView.SHELL) projectRegions(project, identitySecret, epoch) else emptyList()
        return htmlPage { renderProjectDocument(project, view, regions, epoch) }
    }

    override suspend fun regions(request: PageRequest<ProjectPatchesInput>): Iterable<DeferredRegion> {
        val project = findProject(request.input.project) ?: return emptyList()
        return projectRegions(project, identitySecret, request.input.epoch).map(ProjectRegion::deferred)
    }
}

/** One deferred region plus the readable name its heading ID uses. */
internal class ProjectRegion(
    val name: String,
    val deferred: DeferredRegion,
)

private fun findProject(slug: String): ProjectSnapshot? = REFERENCE_PROJECT.takeIf { it.slug == slug }

private fun projectRegions(
    project: ProjectSnapshot,
    secret: RenderIdentitySecret,
    epoch: UUID,
): List<ProjectRegion> {
    val page = PageIdentity(PageEpoch.of(epoch.toString()), secret)
    return listOf(
        projectRegion(project, ProjectSummaryRegion.target(page), "summary", "Project summary", SUMMARY_DELAY_MILLIS),
        projectRegion(project, ProjectTasksRegion.target(page), "tasks", "Tasks", TASKS_DELAY_MILLIS),
        projectRegion(
            project,
            ProjectActivityRegion.target(page),
            "activity",
            "Recent activity",
            ACTIVITY_DELAY_MILLIS,
        ),
    )
}

private fun projectRegion(
    project: ProjectSnapshot,
    target: RegionTarget<ProjectSnapshot>,
    name: String,
    title: String,
    delayMillis: Long,
): ProjectRegion =
    ProjectRegion(
        name,
        deferredRegion(
            target = target,
            loading = { renderRegionLoading(name, title) },
            onFailure = { failure -> patchHtml { renderRegionFailure(project, name, title, failure) } },
            content = {
                delay(delayMillis.milliseconds)
                project
            },
        ),
    )

internal fun failureLabel(failure: DeferredRegionFailure): String =
    when (failure) {
        DeferredRegionFailure.TIMED_OUT -> "took too long"
        DeferredRegionFailure.FAILED -> "could not be loaded"
    }

private const val SUMMARY_DELAY_MILLIS: Long = 120
private const val TASKS_DELAY_MILLIS: Long = 360
private const val ACTIVITY_DELAY_MILLIS: Long = 220
