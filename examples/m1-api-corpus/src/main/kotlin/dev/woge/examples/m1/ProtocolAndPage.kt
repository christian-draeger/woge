package dev.woge.examples.m1

import dev.woge.host.DeferredRegion
import dev.woge.host.DeferredRegionFailure
import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.deferredRegion
import dev.woge.host.htmlPage
import dev.woge.host.regionPlaceholder
import dev.woge.html.h1
import dev.woge.html.p
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.TargetRevision
import dev.woge.protocol.TargetRevisionStep
import dev.woge.protocol.patchHtml

internal data class ProjectInput(
    val slug: String,
)

internal class ProjectPage :
    PageUseCase<ProjectInput>,
    DeferredRegionsUseCase<ProjectInput> {
    override suspend fun open(request: PageRequest<ProjectInput>): PageResult =
        htmlPage {
            h1 { text("Project ${request.input.slug}") }
            regionPlaceholder(projectSummaryRegion(request.input))
        }

    override suspend fun regions(request: PageRequest<ProjectInput>): Iterable<DeferredRegion> =
        listOf(projectSummaryRegion(request.input))
}

internal fun encodedProjectPatch(input: ProjectInput): ByteArray {
    val target = projectTarget(input)
    val patch =
        ReplacePatch(
            patchId = PatchId.of("summary-patch"),
            target = target,
            interactionSequence = InteractionSequence.INITIAL,
            revision = TargetRevisionStep.after(TargetRevision.INITIAL),
            html = patchHtml { p { text("Ready") } },
        )
    return PatchStreamV1.encode(listOf(patch))
}

private fun projectSummaryRegion(input: ProjectInput): DeferredRegion =
    deferredRegion(
        target = projectTarget(input),
        loading = { p { text("Loading project summary…") } },
        onFailure = { failure ->
            patchHtml {
                p {
                    text(
                        when (failure) {
                            DeferredRegionFailure.TIMED_OUT -> "Summary took too long"
                            DeferredRegionFailure.FAILED -> "Summary could not be loaded"
                        },
                    )
                }
            }
        },
        content = { patchHtml { p { text("Project summary") } } },
    )

private fun projectTarget(input: ProjectInput): PatchTarget =
    PatchTarget(
        pageEpoch = PageEpoch.of("project-${input.slug}"),
        region = RegionTargetId.of("project-summary"),
    )
