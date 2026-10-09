package dev.woge.tck

import dev.woge.host.PageRoute
import dev.woge.host.PageUseCase
import dev.woge.host.RouteParameters
import dev.woge.host.htmlPage
import dev.woge.html.ApplicationUrl

/** Typed input of the TCK route: one path value and two optional query values. */
public data class AdapterTckRouteInput(
    val item: Int,
    val note: String? = null,
    val count: Int? = null,
)

/**
 * The route every adapter binds with `handlers.page(application.routePages, AdapterTckRoute)`.
 *
 * It has the same shape as a route the KSP processor generates for `@WogeRoute`.
 */
public object AdapterTckRoute : PageRoute<AdapterTckRouteInput>("/woge-tck/routes/{item}") {
    override fun url(input: AdapterTckRouteInput): ApplicationUrl =
        buildUrl(
            path = mapOf("item" to input.item.toString()),
            query = listOf("note" to input.note, "count" to input.count?.toString()),
        )

    override fun decode(parameters: RouteParameters): AdapterTckRouteInput =
        AdapterTckRouteInput(
            item = pathValue(parameters, "item") { it.toIntOrNull() },
            note = queryValue(parameters, "note") { it },
            count = queryValue(parameters, "count") { it.toIntOrNull() },
        )
}

internal val ROUTE_PAGE: PageUseCase<AdapterTckRouteInput> =
    PageUseCase { request ->
        val input = request.input
        htmlPage { element("p") { text("item=${input.item} note=${input.note} count=${input.count}") } }
    }
