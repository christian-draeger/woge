package example.todo

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageUseCase
import dev.woge.host.RenderSession
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.WogeRegion
import dev.woge.host.WogeRoute
import dev.woge.host.deferredRegion
import dev.woge.host.htmlPage
import dev.woge.host.regionPlaceholder
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.p
import dev.woge.html.section
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.protocol.patchHtml
import dev.woge.spring.webflux.WogeWebFluxHandlers
import dev.woge.spring.webflux.webFluxInput
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter

@WogeRoute(TODO_SUMMARY_PATH)
internal data class TodoStudyOverviewInput(val complete: Boolean? = null, val fail: Boolean? = null)

@WogeRoute("$TODO_SUMMARY_PATH/summary/{epoch}")
internal data class TodoStudyPatchInput(val epoch: String, val fail: Boolean? = null)

@WogeRegion
internal fun HtmlWriter.todoStudySummary(count: Int) {
    p { text("$count todos on the board.") }
}

private fun HtmlWriter.unavailable() {
    p { text(TODO_SUMMARY_UNAVAILABLE) }
}

private fun RenderSession.todoStudyRegion(fail: Boolean) = deferredRegion(
    target = target(TodoStudySummaryRegion),
    loading = { p { text("Loading Todo summary…") } },
    onFailure = { patchHtml { unavailable() } },
    content = { TodoDeferredRead.load(fail) },
)

private val overview = PageUseCase<TodoStudyOverviewInput> { request ->
    val complete = request.input.complete == true
    val fail = request.input.fail == true
    val render = request.context.render
    val count = if (complete) TodoDeferredRead.loadForDocument(fail) else null
    val status = if (complete && count == null) ResponseStatus.SERVICE_UNAVAILABLE else ResponseStatus.OK
    htmlPage(ResponseMetadata(status = status)) {
        doctype()
        html(attributes = { attribute("lang", "en") }) {
            head {
                meta { attribute("charset", "utf-8") }
                metadata("viewport", "width=device-width, initial-scale=1")
                title("Todo overview")
                stylesheet(applicationUrl("/todo.css"))
                if (!complete) {
                    render.metadata(this)
                    moduleScript(applicationUrl("/todo-study-deferred.js"))
                }
            }
            body(attributes = {
                if (!complete) {
                    data("woge-patch-url", TodoStudyPatchRoute.url(TodoStudyPatchInput(render.epoch, request.input.fail)).value)
                }
            }) {
                main {
                    h1 { text("Todo overview") }
                    a(attributes = { url("href", applicationUrl("/")) }) { text("Open the Todo board") }
                    if (complete) {
                        section(attributes = { attribute("id", "todo-summary") }) {
                            if (count == null) unavailable() else todoStudySummary(count)
                        }
                    } else {
                        a(attributes = {
                            url("href", TodoStudyOverviewRoute.url(TodoStudyOverviewInput(complete = true, fail = request.input.fail)))
                        }) { text("Open the complete Todo overview") }
                        regionPlaceholder(render.todoStudyRegion(fail), elementName = "section") {
                            attribute("id", "todo-summary")
                        }
                    }
                }
            }
        }
    }
}

private val chunks = DeferredRegionsUseCase<TodoStudyPatchInput> { request ->
    listOf(request.context.render.resume(request.input.epoch).todoStudyRegion(request.input.fail == true))
}

@Configuration(proxyBeanMethods = false)
@Profile("todo-study-deferred")
internal class TodoStudyDeferredRoutes {
    @Bean
    fun studyRoutes(handlers: WogeWebFluxHandlers) = coRouter {
        val page = handlers.page(overview, TodoStudyOverviewRoute)
        val deferred = handlers.deferred(chunks, TodoStudyPatchRoute.webFluxInput())
        GET(TodoStudyOverviewRoute.path, page::handle)
        GET(TodoStudyPatchRoute.path, deferred::handle)
        GET("$TODO_SUMMARY_PATH/observations") { ServerResponse.ok().bodyValueAndAwait(TodoDeferredRead.observations()) }
    }
}
