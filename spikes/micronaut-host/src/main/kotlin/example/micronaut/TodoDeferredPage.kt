package example.micronaut

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.deferredHtmlPage
import dev.woge.host.deferredRegion
import dev.woge.html.applicationUrl
import dev.woge.html.moduleScript
import dev.woge.html.title
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.patchHtml

internal class TodoDeferredPage(
    private val count: suspend (RequestContext, String) -> Int,
) : PageUseCase<Unit> {
    override suspend fun open(request: PageRequest<Unit>): PageResult.Document {
        val context = request.context
        val render = context.render
        val regions = listOf("active", "completed").map { filter ->
            deferredRegion(
                target = PatchTarget(PageEpoch.of(render.epoch), RegionTargetId.of("$filter-todos")),
                loading = { element("p") { text("Loading $filter todos") } },
                onFailure = { patchHtml { element("p") { text("Could not load $filter todos") } } },
                content = {
                    val total = count(context, filter)
                    patchHtml { element("p") { text("$filter todos: $total") } }
                },
            )
        }
        return deferredHtmlPage(
            regions,
            head = {
                title("Todo counts · Woge")
                render.metadata(this)
                moduleScript(applicationUrl("/assets/woge/dpu.js"), async = true)
            },
            shell = {
                element("h1") { text("Todo counts") }
                element("a", { url("href", applicationUrl("/")) }) { text("View the complete Todo list") }
            },
        )
    }
}
