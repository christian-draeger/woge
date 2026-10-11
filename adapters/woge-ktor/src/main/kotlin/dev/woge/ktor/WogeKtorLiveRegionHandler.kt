package dev.woge.ktor

import dev.woge.host.LiveRegionPage
import dev.woge.host.LiveUseCase
import dev.woge.host.PageRoute
import dev.woge.host.PageUseCase
import io.ktor.server.application.ApplicationCall

/** One route entry point for a typed live page, using the existing bounded response handlers. */
public class WogeKtorLiveRegionHandler<Input : Any, Value> internal constructor(
    private val handlers: WogeKtorHandlers,
    private val useCase: LiveRegionPage<Input, Value>,
    private val route: PageRoute<Input>,
) {
    public suspend fun handle(request: ApplicationCall) {
        val parameters =
            request.request.queryParameters.entries().associate {
                it.key to
                    it.value.singleOrNull().orEmpty()
            }
        if ("_woge_live" in parameters) {
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
