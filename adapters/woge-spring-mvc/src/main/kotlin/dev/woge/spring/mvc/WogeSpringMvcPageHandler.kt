package dev.woge.spring.mvc

import dev.woge.host.PageRequest
import dev.woge.host.PageUseCase
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
        val pageRequest = PageRequest(input.decode(request), contexts.create(request))
        request.launchWogeResponse(response, dispatcher, asyncTimeoutMillis) {
            page.open(pageRequest).writeToServlet(request, response)
        }
    }

    private companion object {
        val PAGE_METHODS: Set<String> = setOf("GET", "HEAD")
    }
}
