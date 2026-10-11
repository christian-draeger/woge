package dev.woge.tck

import dev.woge.host.FailureCategory
import dev.woge.host.LiveRegionPage
import dev.woge.host.PageRegion
import dev.woge.host.PageRoute
import dev.woge.host.PageUseCase
import dev.woge.host.RequestMethod
import dev.woge.host.ResponseStatus
import dev.woge.host.RouteParameters
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.liveRegion
import dev.woge.html.ApplicationUrl
import dev.woge.html.HtmlWriter
import kotlinx.coroutines.flow.flowOf

public data class AdapterTckManagedInput(
    val deny: Boolean = false,
)

public object AdapterTckManagedRoute : PageRoute<AdapterTckManagedInput>("/woge-tck/managed-live") {
    override fun url(input: AdapterTckManagedInput): ApplicationUrl =
        buildUrl(emptyMap(), listOf("deny" to if (input.deny) "true" else null))

    override fun decode(parameters: RouteParameters): AdapterTckManagedInput =
        AdapterTckManagedInput(queryValue(parameters, "deny") { it.toBooleanStrictOrNull() } ?: false)
}

internal fun managedLivePage(): LiveRegionPage<AdapterTckManagedInput, String> {
    val region =
        object : PageRegion<String>("tck.managed") {
            override fun render(
                writer: HtmlWriter,
                input: String,
            ) = writer.text(input)
        }
    val page =
        PageUseCase<AdapterTckManagedInput> { request ->
            htmlPage {
                element("html") {
                    element("head") { request.context.render.metadata(this) }
                    element("body") {
                        liveRegion(
                            request.context.render,
                            region,
                            "Initial data",
                            AdapterTckManagedRoute.url(request.input),
                        )
                    }
                }
            }
        }
    return LiveRegionPage(
        page,
        region,
        load = { "Current live data" },
        changes = { flowOf(Unit) },
        authorize = { request ->
            if (request.input.deny) failure(FailureCategory.FORBIDDEN, request.context.correlationId) else null
        },
    )
}

internal suspend fun AdapterTckHttpClient.verifyManagedLive(expect: (Boolean, String, String) -> Unit) {
    val contract = "managed-live-region"
    val path = AdapterTckManagedRoute.path
    val document = open(RequestMethod.GET, path)
    val html = document.body().use { String(it.readAllBytes(), Charsets.UTF_8) }
    val epoch = Regex("name=\"woge-page-epoch\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
    val target = Regex("data-woge-region=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
    expect(
        document.statusCode() == ResponseStatus.OK.code && epoch != null && target != null,
        contract,
        "document lacks managed identity",
    )
    val stream = open(RequestMethod.GET, "$path?_woge_live=$epoch&_woge_protocol_version=1")
    val events = stream.body().use { String(it.readAllBytes(), Charsets.UTF_8) }
    expect(
        stream.statusCode() == ResponseStatus.OK.code && events.contains("data: $target"),
        contract,
        "same route did not stream its target",
    )
    val query = "_woge_refresh=1&_woge_epoch=$epoch&_woge_target=$target&_woge_revision=5&_woge_interaction=7"
    val refresh =
        open(
            RequestMethod.GET,
            "$path?$query",
            mapOf(
                "Accept" to "application/vnd.woge.patch-stream; version=1",
            ),
        )
    val patch = refresh.body().use { String(it.readAllBytes(), Charsets.UTF_8) }
    expect(
        refresh.statusCode() == ResponseStatus.OK.code && patch.contains("Current live data"),
        contract,
        "safe refresh did not render data",
    )
    expect(
        patch.contains("\"baseRevision\":5") && patch.contains("\"interactionSequence\":7"),
        contract,
        "refresh lost ordering state",
    )
    for (mode in listOf("", "&_woge_live=$epoch", "&$query")) {
        val denied = open(RequestMethod.GET, "$path?deny=true$mode")
        denied.body().close()
        expect(
            denied.statusCode() == ResponseStatus.FORBIDDEN.code,
            contract,
            "authorization must apply to every managed mode",
        )
    }
}
