package dev.woge.spring.webflux

import dev.woge.host.LiveRegionPage
import dev.woge.host.LiveUseCase
import dev.woge.host.PageRoute
import dev.woge.host.PageUseCase
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/** One route entry point for a typed live page, using the existing bounded response handlers. */
public class WogeWebFluxLiveRegionHandler<Input : Any, Value> internal constructor(
    private val handlers: WogeWebFluxHandlers,
    private val useCase: LiveRegionPage<Input, Value>,
    private val route: PageRoute<Input>,
) {
    public suspend fun handle(request: ServerRequest): ServerResponse {
        val parameters = request.queryParams().mapValues { it.value.singleOrNull().orEmpty() }
        return if ("_woge_live" in parameters) {
            handlers
                .live(
                    LiveUseCase { useCase.subscribe(it, parameters.getValue("_woge_live")) },
                    route,
                ).handle(request)
        } else {
            val page =
                PageUseCase<Input> { request ->
                    if ("_woge_refresh" in parameters) {
                        useCase.refresh(request, route, parameters)
                    } else {
                        useCase.document(request)
                    }
                }
            handlers.page(page, route).handle(request)
        }
    }
}
