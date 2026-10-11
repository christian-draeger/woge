package dev.woge.ktor

import dev.woge.host.LiveUseCase
import dev.woge.host.PageRequest
import dev.woge.host.RouteValueException
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.failure
import dev.woge.runtime.LiveAdmission
import dev.woge.runtime.LiveEventStream
import dev.woge.runtime.LiveResponse
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** Streams live region invalidations as Server-Sent Events through a Ktor route. */
public class WogeKtorLiveHandler<Input : Any> internal constructor(
    private val useCase: LiveUseCase<Input>,
    private val input: KtorPageInput<Input>,
    private val contexts: KtorRequestContextFactory,
    private val admission: LiveAdmission,
    private val observer: WogeObserver,
) {
    /** Authorizes and admits the stream before the first byte, then flushes every event. */
    public suspend fun handle(call: ApplicationCall) {
        if (dev.woge.host.requestsUnsupportedLiveProtocolVersion(
                call.request.queryParameters["_woge_protocol_version"],
            )
        ) {
            call.response.headers.append("Woge-Protocol-Error", "unsupported-version")
            call.response.headers.append("Cache-Control", "no-store")
            call.respond(io.ktor.http.HttpStatusCode.NotAcceptable)
            return
        }
        val context = contexts.create(call)
        val observation = WogeObservationContext(requestTrace = context.trace)
        val decoded =
            try {
                input.decode(call)
            } catch (invalid: RouteValueException) {
                call.respondWogePage(failure(invalid.category, context.correlationId), observer, observation)
                return
            }
        val lastEventId = call.request.headers[LiveEventStream.LAST_EVENT_ID_HEADER]
        when (val live = admission.open(useCase, PageRequest(decoded, context), lastEventId, observer)) {
            is LiveResponse.Refused -> call.respondWogePage(live.failure, observer, observation)
            is LiveResponse.Stream -> call.respondWogeLive(live.events)
        }
    }
}
