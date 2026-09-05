package dev.woge.example.project

import dev.woge.host.DeferredRegion
import dev.woge.host.DeferredRegionFailure
import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.FailureCategory
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.deferredRegion
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
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

/** Host-neutral project page used unchanged by Spring WebFlux and future MVC/Ktor launchers. */
public class ProjectPage :
    PageUseCase<ProjectPageInput>,
    DeferredRegionsUseCase<ProjectPageInput> {
    override suspend fun open(request: PageRequest<ProjectPageInput>): PageResult {
        val project =
            findProject(request.input.project)
                ?: return failure(FailureCategory.NOT_FOUND, request.context.correlationId)
        return htmlPage { renderProjectDocument(project, request.input.view) }
    }

    override suspend fun regions(request: PageRequest<ProjectPageInput>): Iterable<DeferredRegion> {
        val project = findProject(request.input.project) ?: return emptyList()
        return deferredRegions(project)
    }
}

private fun findProject(slug: String): ProjectSnapshot? = REFERENCE_PROJECT.takeIf { it.slug == slug }

internal fun deferredRegions(project: ProjectSnapshot): List<DeferredRegion> =
    listOf(
        projectRegion(project, "summary", "Project summary", SUMMARY_DELAY_MILLIS) { renderSummary(project) },
        projectRegion(project, "tasks", "Tasks", TASKS_DELAY_MILLIS) { renderTasks(project) },
        projectRegion(project, "activity", "Recent activity", ACTIVITY_DELAY_MILLIS) { renderActivity(project) },
    )

private fun projectRegion(
    project: ProjectSnapshot,
    region: String,
    title: String,
    delayMillis: Long,
    content: dev.woge.html.HtmlWriter.() -> Unit,
): DeferredRegion =
    deferredRegion(
        target = projectTarget(project, region),
        loading = { renderRegionLoading(region, title) },
        onFailure = { failure ->
            patchHtml { renderRegionFailure(project, region, title, failure) }
        },
        content = {
            delay(delayMillis.milliseconds)
            patchHtml(content)
        },
    )

private fun projectTarget(
    project: ProjectSnapshot,
    region: String,
): PatchTarget =
    PatchTarget(
        pageEpoch = projectEpoch(project),
        region = RegionTargetId.of(region),
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
