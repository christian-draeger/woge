package dev.woge.tck

import dev.woge.host.PageUseCase
import dev.woge.host.deferredHtmlPage
import dev.woge.host.deferredRegion
import dev.woge.html.UnsafeWogeHtmlApi
import dev.woge.html.applicationUrl
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.unsafeHtml
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.CompletableDeferred

/** One unchanged Kotlin page bound to native, automatic and fallback policies in every real adapter. */
public class InitialDocumentFixture {
    public val started: CompletableDeferred<Unit> = CompletableDeferred()
    public val release: CompletableDeferred<Unit> = CompletableDeferred()

    @OptIn(UnsafeWogeHtmlApi::class)
    public val pages: PageUseCase<String> =
        PageUseCase { request ->
            val region =
                deferredRegion(
                    PatchTarget(PageEpoch.of(request.context.render.epoch), RegionTargetId.of("results")),
                    loading = {
                        if (request.input == "loading-unsafe") {
                            raw(unsafeHtml("<p>Loading results</p>"))
                        } else {
                            element("p") { text("Loading results") }
                        }
                        element("a", attributes = { url("href", applicationUrl("/woge-tck/dpu/complete")) }) {
                            text("View complete results")
                        }
                    },
                    onFailure = { patchHtml { element("p") { text("Results unavailable; try again") } } },
                    content = {
                        if (request.input == "gated") {
                            started.complete(Unit)
                            release.await()
                        }
                        patchHtml {
                            if (request.input in setOf("late-unsafe", "mixed")) {
                                raw(unsafeHtml("<ul><li>Alpha</li><li>Beta</li><li>Gamma</li></ul>"))
                            } else {
                                element("ul") {
                                    listOf("Alpha", "Beta", "Gamma").forEach { element("li") { text(it) } }
                                }
                            }
                        }
                    },
                )
            val regions = mutableListOf(region)
            val siblings =
                when (request.input) {
                    "mixed" -> listOf("native-sibling")
                    "over-limit" -> listOf("sibling-one", "sibling-two")
                    else -> emptyList()
                }
            siblings.forEach { id ->
                regions.add(
                    deferredRegion(
                        PatchTarget(PageEpoch.of(request.context.render.epoch), RegionTargetId.of(id)),
                        loading = { element("p") { text("Loading sibling") } },
                        onFailure = { patchHtml { text("Sibling unavailable") } },
                        content = { patchHtml { element("p") { text("Native sibling complete") } } },
                    ),
                )
            }
            deferredHtmlPage(
                regions = regions,
                head = {
                    metadata("woge-page-epoch", request.context.render.epoch)
                    moduleScript(applicationUrl("/assets/woge/dpu.js"), async = true)
                },
                shell = { element("h1") { text("Initial document results") } },
                tail = { element("footer") { text("Original document footer") } },
            )
        }
}
