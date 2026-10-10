package dev.woge.spring.webflux

import dev.woge.host.LiveUseCase
import dev.woge.host.PageRequest
import dev.woge.host.RouteValueException
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.failure
import dev.woge.runtime.LiveAdmission
import dev.woge.runtime.LiveEventStream
import dev.woge.runtime.LiveResponse
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/** Streams live region invalidations as Server-Sent Events through a WebFlux functional route. */
public class WogeWebFluxLiveHandler<Input : Any> internal constructor(
    private val useCase: LiveUseCase<Input>,
    private val input: WebFluxPageInput<Input>,
    private val contexts: WebFluxRequestContextFactory,
    private val admission: LiveAdmission,
    private val observer: WogeObserver,
) {
    /** Authorizes and admits the stream before the first byte, then flushes every event. */
    public suspend fun handle(request: ServerRequest): ServerResponse {
        val context = contexts.create(request)
        val observation = WogeObservationContext(requestTrace = context.trace)
        val decoded =
            try {
                input.decode(request)
            } catch (invalid: RouteValueException) {
                return failure(invalid.category, context.correlationId).toWebFluxResponse(observer, observation)
            }
        val lastEventId = request.headers().firstHeader(LiveEventStream.LAST_EVENT_ID_HEADER)
        return when (val live = admission.open(useCase, PageRequest(decoded, context), lastEventId, observer)) {
            is LiveResponse.Refused -> live.failure.toWebFluxResponse(observer, observation)
            is LiveResponse.Stream -> live.events.toWebFluxLiveResponse()
        }
    }
}
