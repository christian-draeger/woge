package dev.woge.tck

import dev.woge.host.FailureCategory
import dev.woge.host.FailurePages
import dev.woge.host.PageRoute
import dev.woge.host.PageUseCase
import dev.woge.host.RouteParameters
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.html.ApplicationUrl
import dev.woge.protocol.htmlFrame

/** Route for testing configured failure HTML separately from the default bodyless contract. */
public object AdapterTckFailureRoute : PageRoute<Int>("/woge-tck/failures/{status}") {
    override fun url(input: Int): ApplicationUrl = buildUrl(mapOf("status" to input.toString()), emptyList())

    override fun decode(parameters: RouteParameters): Int = pathValue(parameters, "status") { it.toIntOrNull() }
}

internal val FAILURE_ROUTE_PAGE: PageUseCase<Int> =
    PageUseCase { request ->
        val category = FailureCategory.entries.firstOrNull { it.status.code == request.input }
        if (category != null) {
            failure(category, request.context.correlationId)
        } else {
            htmlPage { element("p") { text("OK") } }
        }
    }

internal val FAILURE_PAGES: FailurePages =
    FailurePages { failure ->
        if (failure.category in setOf(FailureCategory.NOT_FOUND, FailureCategory.INTERNAL)) {
            htmlFrame { element("p") { text("Page failure: ${failure.category}") } }
        } else {
            null
        }
    }
