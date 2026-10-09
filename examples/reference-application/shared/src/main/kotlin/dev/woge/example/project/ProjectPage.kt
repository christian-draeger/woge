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
import dev.woge.host.deferredRegion
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Selects the immediate enhanced shell or a complete full-navigation response. */
public enum class ProjectPageView {
    SHELL,
    COMPLETE,
}

/** Route input decoded by a host adapter before portable page code runs. */
public data class ProjectPageInput(
    public val project: String,
    public val view: ProjectPageView = ProjectPageView.SHELL,
)

/**
 * Host-neutral project page used unchanged by the Spring MVC, WebFlux and Ktor launchers.
 *
 * [identitySecret] turns region names into opaque IDs. Use one shared secret per deployment.
 */
public class ProjectPage(
    private val identitySecret: RenderIdentitySecret = RenderIdentitySecret.random(),
) : PageUseCase<ProjectPageInput>,
    DeferredRegionsUseCase<ProjectPageInput> {
    override suspend fun open(request: PageRequest<ProjectPageInput>): PageResult {
        val project =
            findProject(request.input.project)
                ?: return failure(FailureCategory.NOT_FOUND, request.context.correlationId)
        val view = request.input.view
        val regions = if (view == ProjectPageView.SHELL) projectRegions(project, identitySecret) else emptyList()
        return htmlPage { renderProjectDocument(project, view, regions) }
    }

    override suspend fun regions(request: PageRequest<ProjectPageInput>): Iterable<DeferredRegion> {
        val project = findProject(request.input.project) ?: return emptyList()
        return projectRegions(project, identitySecret).map(ProjectRegion::deferred)
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
): List<ProjectRegion> {
    val page = PageIdentity(projectEpoch(project), secret)
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

internal fun projectEpoch(project: ProjectSnapshot): PageEpoch = PageEpoch.of("quickstart-${project.slug}")

internal fun failureLabel(failure: DeferredRegionFailure): String =
    when (failure) {
        DeferredRegionFailure.TIMED_OUT -> "took too long"
        DeferredRegionFailure.FAILED -> "could not be loaded"
    }

private const val SUMMARY_DELAY_MILLIS: Long = 120
private const val TASKS_DELAY_MILLIS: Long = 360
private const val ACTIVITY_DELAY_MILLIS: Long = 220
