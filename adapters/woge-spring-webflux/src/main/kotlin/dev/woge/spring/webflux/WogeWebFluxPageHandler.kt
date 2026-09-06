package dev.woge.spring.webflux

import dev.woge.host.PageRequest
import dev.woge.host.PageUseCase
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.runtime.observationOutcome
import dev.woge.runtime.observeOperation
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/** Executes one portable [PageUseCase] from a WebFlux functional route. */
public class WogeWebFluxPageHandler<Input : Any>(
    private val page: PageUseCase<Input>,
    private val input: WebFluxPageInput<Input>,
    private val contexts: WebFluxRequestContextFactory = DefaultWebFluxRequestContextFactory,
    private val observer: WogeObserver = WogeObserver.NONE,
) {
    /** Decodes, executes and maps the page without an application-owned controller. */
    public suspend fun handle(request: ServerRequest): ServerResponse {
        val context = contexts.create(request)
        val observationContext = WogeObservationContext(requestTrace = context.trace)
        val pageRequest = PageRequest(input.decode(request), context)
        val result =
            observer.observeOperation(
                operation = WogeOperation.PAGE_REQUEST,
                context = observationContext,
                successfulOutcome = { it.observationOutcome() },
            ) {
                page.open(pageRequest)
            }
        return result.toWebFluxResponse(observer, observationContext)
    }
}
