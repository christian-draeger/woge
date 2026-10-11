package dev.woge.spring.mvc

import dev.woge.host.FailureCategory
import dev.woge.host.FailurePages
import dev.woge.host.FormDecodingException
import dev.woge.host.MUTATION_REQUEST_IDENTITY_HEADER
import dev.woge.host.MutationRequestIdentityException
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestMethod
import dev.woge.host.RouteValueException
import dev.woge.host.UnverifiedActionSecurityException
import dev.woge.host.UploadDecodingException
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.failure
import dev.woge.host.forHttpRequest
import dev.woge.host.requireActionSecurity
import dev.woge.host.withFailurePages
import dev.woge.host.withMutationRequestIdentity
import dev.woge.host.withUploadCleanup
import dev.woge.runtime.observationOutcome
import dev.woge.runtime.observeOperation
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.CoroutineDispatcher
import org.springframework.web.HttpRequestHandler

/** Executes one portable [PageUseCase] from a Spring MVC URL handler mapping. */
@Suppress("LongParameterList")
public class WogeSpringMvcPageHandler<Input : Any> internal constructor(
    private val page: PageUseCase<Input>,
    private val input: SpringMvcPageInput<Input>,
    private val contexts: SpringMvcRequestContextFactory,
    private val dispatcher: CoroutineDispatcher,
    private val asyncTimeoutMillis: Long,
    private val observer: WogeObserver,
    private val failurePages: FailurePages,
    private val allowedMethods: Set<String> = setOf("GET", "HEAD"),
) : HttpRequestHandler {
    /** Snapshots the request, releases its Servlet thread and streams the page asynchronously. */
    override fun handleRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (rejectRequest(request, response)) return
        val context = contexts.create(request)
        request.launchWogeResponse(response, dispatcher, asyncTimeoutMillis) {
            val decoded =
                runCatching {
                    val actionContext =
                        if (allowedMethods == setOf("POST")) {
                            context.requireActionSecurity()
                            context.withMutationRequestIdentity(
                                request.getHeaders(MUTATION_REQUEST_IDENTITY_HEADER).toList(),
                            )
                        } else {
                            context
                        }
                    PageRequest(input.decode(request), actionContext)
                }
            val invalid =
                decoded.exceptionOrNull()?.let {
                    when (it) {
                        is RouteValueException -> it.category
                        is FormDecodingException -> it.category
                        is UploadDecodingException -> it.category
                        is MutationRequestIdentityException -> FailureCategory.BAD_REQUEST
                        is UnverifiedActionSecurityException -> FailureCategory.FORBIDDEN
                        else -> throw it
                    }
                }
            val observationContext = WogeObservationContext(requestTrace = context.trace)
            val result =
                if (invalid != null) {
                    failure(invalid, context.correlationId)
                } else {
                    val pageRequest = decoded.getOrThrow()
                    pageRequest.withUploadCleanup {
                        observer.observeOperation(
                            operation = WogeOperation.PAGE_REQUEST,
                            context = observationContext,
                            successfulOutcome = { it.observationOutcome() },
                        ) {
                            page.open(pageRequest)
                        }
                    }
                }
            result
                .withFailurePages(failurePages)
                .finalizeCache(request)
                .writeToServlet(
                    request,
                    response,
                    observer,
                    observationContext,
                    actionAccept = actionAccept(request, result),
                )
        }
    }

    private fun rejectRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): Boolean =
        when {
            request.method !in allowedMethods -> {
                response.writeMethodNotAllowed(allowedMethods)
                true
            }
            allowedMethods == setOf("POST") &&
                dev.woge.host.requestsUnsupportedActionPatchVersion(request.getHeader("Accept")) -> {
                response.status = HttpServletResponse.SC_NOT_ACCEPTABLE
                response.setHeader("Woge-Protocol-Error", "unsupported-version")
                response.setHeader("Cache-Control", "no-store")
                true
            }
            else -> false
        }

    private fun PageResult.finalizeCache(request: HttpServletRequest): PageResult =
        forHttpRequest(
            RequestMethod.of(request.method),
            request.getHeaders("If-None-Match").toList(),
            request.getHeaders("If-Modified-Since").toList(),
        )

    private fun actionAccept(
        request: HttpServletRequest,
        result: PageResult,
    ): String? =
        if (allowedMethods == setOf("POST") || result is PageResult.RegionUpdates) {
            request.getHeader("Accept").orEmpty()
        } else {
            null
        }
}
