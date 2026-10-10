package dev.woge.spring.webflux

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
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/** Executes one portable [PageUseCase] from a WebFlux functional route. */
public class WogeWebFluxPageHandler<Input : Any>(
    private val page: PageUseCase<Input>,
    private val input: WebFluxPageInput<Input>,
    private val contexts: WebFluxRequestContextFactory = DefaultWebFluxRequestContextFactory,
    private val observer: WogeObserver = WogeObserver.NONE,
    private val failurePages: FailurePages = FailurePages.NONE,
) {
    /** Decodes, executes and maps the page without an application-owned controller. */
    public suspend fun handle(request: ServerRequest): ServerResponse = handle(request, actionAccept = null)

    internal suspend fun handleAction(request: ServerRequest): ServerResponse =
        handle(request, request.headers().firstHeader("Accept").orEmpty())

    private suspend fun handle(
        request: ServerRequest,
        actionAccept: String?,
    ): ServerResponse {
        val context = contexts.create(request)
        val observationContext = WogeObservationContext(requestTrace = context.trace)
        val decoded =
            runCatching {
                val actionContext =
                    if (actionAccept != null) {
                        context.requireActionSecurity()
                        context.withMutationRequestIdentity(request.headers().header(MUTATION_REQUEST_IDENTITY_HEADER))
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
        val accept =
            actionAccept
                ?: if (result is PageResult.RegionUpdates) request.headers().firstHeader("Accept").orEmpty() else null
        return result
            .withFailurePages(failurePages)
            .forHttpRequest(
                RequestMethod.of(request.method().name()),
                request.headers().header("If-None-Match"),
                request.headers().header("If-Modified-Since"),
            ).toWebFluxResponse(observer, observationContext, accept)
    }
}
