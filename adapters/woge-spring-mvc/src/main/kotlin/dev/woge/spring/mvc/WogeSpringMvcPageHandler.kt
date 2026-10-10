package dev.woge.spring.mvc

import dev.woge.host.PageRequest
import dev.woge.host.PageUseCase
import dev.woge.host.RouteValueException
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.failure
import dev.woge.runtime.observationOutcome
import dev.woge.runtime.observeOperation
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.CoroutineDispatcher
import org.springframework.web.HttpRequestHandler

/** Executes one portable [PageUseCase] from a Spring MVC URL handler mapping. */
public class WogeSpringMvcPageHandler<Input : Any> internal constructor(
    private val page: PageUseCase<Input>,
    private val input: SpringMvcPageInput<Input>,
    private val contexts: SpringMvcRequestContextFactory,
    private val dispatcher: CoroutineDispatcher,
    private val asyncTimeoutMillis: Long,
    private val observer: WogeObserver,
) : HttpRequestHandler {
    /** Snapshots the request, releases its Servlet thread and streams the page asynchronously. */
    override fun handleRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (request.method !in PAGE_METHODS) {
            response.writeMethodNotAllowed(PAGE_METHODS)
            return
        }
        val context = contexts.create(request)
        val decoded = runCatching { input.decode(request) }
        val invalid = decoded.exceptionOrNull()?.let { it as? RouteValueException ?: throw it }
        val observationContext = WogeObservationContext(requestTrace = context.trace)
        request.launchWogeResponse(response, dispatcher, asyncTimeoutMillis) {
            val result =
                if (invalid != null) {
                    failure(invalid.category, context.correlationId)
                } else {
                    observer.observeOperation(
                        operation = WogeOperation.PAGE_REQUEST,
                        context = observationContext,
                        successfulOutcome = { it.observationOutcome() },
                    ) {
                        page.open(PageRequest(decoded.getOrThrow(), context))
                    }
                }
            result.writeToServlet(request, response, observer, observationContext)
        }
    }

    private companion object {
        val PAGE_METHODS: Set<String> = setOf("GET", "HEAD")
    }
}
