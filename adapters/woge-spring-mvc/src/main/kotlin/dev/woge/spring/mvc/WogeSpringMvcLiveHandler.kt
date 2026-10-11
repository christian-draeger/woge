package dev.woge.spring.mvc

import dev.woge.host.LiveUseCase
import dev.woge.host.PageRequest
import dev.woge.host.RouteValueException
import dev.woge.host.WogeObserver
import dev.woge.host.failure
import dev.woge.runtime.LiveAdmission
import dev.woge.runtime.LiveEventStream
import dev.woge.runtime.LiveResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.springframework.web.HttpRequestHandler
import java.io.IOException

/** Streams live region invalidations as Server-Sent Events through one asynchronous Servlet response. */
public class WogeSpringMvcLiveHandler<Input : Any> internal constructor(
    private val useCase: LiveUseCase<Input>,
    private val input: SpringMvcPageInput<Input>,
    private val contexts: SpringMvcRequestContextFactory,
    private val dispatcher: CoroutineDispatcher,
    private val admission: LiveAdmission,
    private val observer: WogeObserver,
) : HttpRequestHandler {
    // The stream ends itself at the maximum lifetime; the Servlet timeout is only a backstop.
    private val asyncTimeoutMillis = (admission.limits.maxLifetime + admission.limits.heartbeat).inWholeMilliseconds

    /** Authorizes and admits the stream before the first byte, then flushes every event. */
    override fun handleRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (rejectRequest(request, response)) return
        val context = contexts.create(request)
        val decoded =
            try {
                input.decode(request)
            } catch (invalid: RouteValueException) {
                response.status = failure(invalid.category, context.correlationId).metadata.status.code
                return
            }
        val lastEventId = request.getHeader(LiveEventStream.LAST_EVENT_ID_HEADER)
        request.launchWogeResponse(response, dispatcher, asyncTimeoutMillis) {
            when (val live = admission.open(useCase, PageRequest(decoded, context), lastEventId, observer)) {
                is LiveResponse.Refused -> {
                    response.status = live.failure.metadata.status.code
                    response.setHeader("Cache-Control", "no-store")
                }
                is LiveResponse.Stream -> {
                    response.status = HttpServletResponse.SC_OK
                    response.contentType = LiveEventStream.MEDIA_TYPE
                    response.characterEncoding = "UTF-8"
                    LiveEventStream.HEADERS.forEach(response::setHeader)
                    val output = response.outputStream
                    output.flush()
                    try {
                        live.events.collect { chunk ->
                            currentCoroutineContext().ensureActive()
                            output.write(chunk)
                            output.flush()
                        }
                    } catch (_: IOException) {
                        // The browser went away. EventSource reconnects on its own when it is still open.
                    }
                }
            }
        }
    }

    private fun rejectRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): Boolean =
        when {
            request.method != "GET" -> {
                response.writeMethodNotAllowed(setOf("GET"))
                true
            }
            dev.woge.host.requestsUnsupportedLiveProtocolVersion(request.getParameter("_woge_protocol_version")) -> {
                response.status = HttpServletResponse.SC_NOT_ACCEPTABLE
                response.setHeader("Woge-Protocol-Error", "unsupported-version")
                response.setHeader("Cache-Control", "no-store")
                true
            }
            else -> false
        }
}
