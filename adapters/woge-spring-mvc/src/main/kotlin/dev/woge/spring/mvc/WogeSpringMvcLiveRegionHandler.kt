package dev.woge.spring.mvc

import dev.woge.host.LiveRegionPage
import dev.woge.host.LiveUseCase
import dev.woge.host.PageRoute
import dev.woge.host.PageUseCase
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.HttpRequestHandler

/** One route entry point for a typed live page, using the existing bounded response handlers. */
public class WogeSpringMvcLiveRegionHandler<Input : Any, Value> internal constructor(
    private val handlers: WogeSpringMvcHandlers,
    private val useCase: LiveRegionPage<Input, Value>,
    private val route: PageRoute<Input>,
) : HttpRequestHandler {
    override fun handleRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val parameters = request.parameterMap.mapValues { it.value.singleOrNull().orEmpty() }
        if ("_woge_live" in parameters) {
            handlers
                .live(LiveUseCase { useCase.subscribe(it, parameters.getValue("_woge_live")) }, route)
                .handleRequest(request, response)
        } else {
            val page =
                PageUseCase<Input> { request ->
                    if ("_woge_refresh" in parameters) {
                        useCase.refresh(request, route, parameters)
                    } else {
                        useCase.document(request)
                    }
                }
            handlers.page(page, route).handleRequest(request, response)
        }
    }
}
