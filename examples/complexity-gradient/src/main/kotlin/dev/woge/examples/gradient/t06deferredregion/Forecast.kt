package dev.woge.examples.gradient.t06deferredregion

import dev.woge.host.DeferredRegion
import dev.woge.host.DeferredRegionFailure
import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RenderSession
import dev.woge.host.WogeRegion
import dev.woge.host.WogeRoute
import dev.woge.host.deferredRegion
import dev.woge.host.htmlPage
import dev.woge.host.regionHtml
import dev.woge.host.regionPlaceholder
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.noscript
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.html.ul
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@WogeRoute("/forecast")
public data object ForecastInput

/** The browser opens this URL to receive the slow region; [epoch] ties it to the rendered page. */
@WogeRoute("/forecast/woge-patches/{epoch}")
public data class ForecastPatchesInput(
    val epoch: String,
)

public class ForecastPage :
    PageUseCase<ForecastInput>,
    DeferredRegionsUseCase<ForecastPatchesInput> {
    override suspend fun open(request: PageRequest<ForecastInput>): PageResult {
        val page = request.context.render
        return htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    page.metadata(this)
                    title("Forecast · Deferred region")
                    stylesheet(applicationUrl("/assets/deferred-region/site.css"))
                    moduleScript(applicationUrl("/assets/deferred-region/app.js"))
                }
                body(attributes = {
                    data("woge-patch-url", ForecastPatchesRoute.url(ForecastPatchesInput(page.epoch)).value)
                }) {
                    main {
                        h1 { text("Weekend forecast") }
                        regionPlaceholder(forecastRegion(page), elementName = "section")
                        noscript { p { text("The forecast needs JavaScript.") } }
                    }
                }
            }
        }
    }

    override suspend fun regions(request: PageRequest<ForecastPatchesInput>): Iterable<DeferredRegion> =
        listOf(forecastRegion(request.context.render.resume(request.input.epoch)))

    private fun forecastRegion(page: RenderSession): DeferredRegion =
        deferredRegion(
            target = page.target(ForecastRegion),
            loading = { p { text("Loading the forecast…") } },
            onFailure = { failure -> regionHtml { p { text(failureMessage(failure)) } } },
            content = { loadForecast() },
        )
}

private fun failureMessage(failure: DeferredRegionFailure): String =
    if (failure == DeferredRegionFailure.TIMED_OUT) "The forecast took too long." else "The forecast is unavailable."

private suspend fun loadForecast(): List<String> {
    delay(50.milliseconds)
    return listOf("Saturday: sunny", "Sunday: light rain")
}

@WogeRegion
internal fun HtmlWriter.forecast(days: List<String>) {
    ul { days.forEach { li { text(it) } } }
}
