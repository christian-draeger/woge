package dev.woge.spring.webflux

import dev.woge.host.PageRequest
import dev.woge.host.PageUseCase
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/** Executes one portable [PageUseCase] from a WebFlux functional route. */
public class WogeWebFluxPageHandler<Input : Any>(
    private val page: PageUseCase<Input>,
    private val input: WebFluxPageInput<Input>,
    private val contexts: WebFluxRequestContextFactory = DefaultWebFluxRequestContextFactory,
) {
    /** Decodes, executes and maps the page without an application-owned controller. */
    public suspend fun handle(request: ServerRequest): ServerResponse {
        val pageRequest = PageRequest(input.decode(request), contexts.create(request))
        return page.open(pageRequest).toWebFluxResponse()
    }
}
