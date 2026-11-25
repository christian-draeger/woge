package dev.woge.quarkus

import dev.woge.host.ActionExecutor
import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.FailureCategory
import dev.woge.host.FailurePages
import dev.woge.host.FormDecodingException
import dev.woge.host.LiveLimits
import dev.woge.host.LiveRegionPage
import dev.woge.host.LiveUseCase
import dev.woge.host.MUTATION_REQUEST_IDENTITY_HEADER
import dev.woge.host.MutationRequestIdentityException
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageRoute
import dev.woge.host.PageUseCase
import dev.woge.host.PatchStreamLimits
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.RenderSessions
import dev.woge.host.RequestContext
import dev.woge.host.RequestMethod
import dev.woge.host.ResponseStatus
import dev.woge.host.RouteValueException
import dev.woge.host.UnverifiedActionSecurityException
import dev.woge.host.UploadDecodingException
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.failure
import dev.woge.host.forHttpRequest
import dev.woge.host.page
import dev.woge.host.requestsUnsupportedActionPatchVersion
import dev.woge.host.requestsUnsupportedLiveProtocolVersion
import dev.woge.host.requireActionSecurity
import dev.woge.host.withFailurePages
import dev.woge.host.withMutationRequestIdentity
import dev.woge.host.withRenderSession
import dev.woge.host.withUploadCleanup
import dev.woge.protocol.PatchId
import dev.woge.runtime.DeferredRegionExecutor
import dev.woge.runtime.DeferredRegionLimitException
import dev.woge.runtime.DeferredRegionPolicy
import dev.woge.runtime.InitialDocumentPolicy
import dev.woge.runtime.InitialDocumentRuntime
import dev.woge.runtime.LiveAdmission
import dev.woge.runtime.LiveEventStream
import dev.woge.runtime.LiveResponse
import dev.woge.runtime.NativeDpuMode
import dev.woge.runtime.encodeDeferredPatchStream
import dev.woge.runtime.observationOutcome
import dev.woge.runtime.observeOperation
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Binds portable Woge handlers to the Quarkus-managed Vert.x router. */
@Suppress("LongParameterList", "TooManyFunctions")
public class WogeQuarkusHandlers(
    private val contexts: QuarkusRequestContextFactory = QuarkusRequestContextFactory { quarkusRequestContext(it) },
    private val observer: WogeObserver = WogeObserver.NONE,
    private val failurePages: FailurePages = FailurePages.NONE,
    deferredPolicy: DeferredRegionPolicy = DeferredRegionPolicy(),
    private val patchLimits: PatchStreamLimits = PatchStreamLimits(),
    liveLimits: LiveLimits = LiveLimits(),
    renderIdentitySecret: RenderIdentitySecret = RenderIdentitySecret.random(),
    nativeDpuMode: NativeDpuMode = NativeDpuMode.OFF,
) {
    private val sessions = RenderSessions(renderIdentitySecret)
    private val deferredRegions = DeferredRegionExecutor(deferredPolicy, observer)
    private val liveAdmission = LiveAdmission(liveLimits)
    private val initialDocuments =
        InitialDocumentRuntime(InitialDocumentPolicy(nativeDpuMode, deferredPolicy, patchLimits), observer)

    /** Handles a typed document or HEAD request. */
    public fun <Input : Any> page(
        useCase: PageUseCase<Input>,
        input: QuarkusPageInput<Input>,
    ): Handler<RoutingContext> = document(useCase, input, action = false)

    /** Handles a generated route without moving its inputs into host code. */
    public fun <Input : Any> page(
        useCase: PageUseCase<Input>,
        route: PageRoute<Input>,
    ): Handler<RoutingContext> = page(useCase, route.quarkusInput())

    /** Checks the configured request security before decoding and invoking a POST action. */
    public fun <Command : Any> action(
        executor: ActionExecutor<Command>,
        input: QuarkusPageInput<Command>,
    ): Handler<RoutingContext> = document(PageUseCase(executor::execute), input, action = true)

    /** Resolves independently scheduled regions on a worker and writes framed patches incrementally. */
    public fun <Input : Any> deferred(
        useCase: DeferredRegionsUseCase<Input>,
        input: QuarkusPageInput<Input>,
    ): Handler<RoutingContext> =
        Handler { call ->
            exchange(call) { response, context ->
                if (requestsUnsupportedActionPatchVersion(call.headerValues("Accept"))) {
                    response.unsupportedProtocol()
                    return@exchange
                }
                val decoded = input.decode(call)
                val regions =
                    try {
                        withContext(Dispatchers.Default) {
                            deferredRegions.prepare(useCase.regions(PageRequest(decoded, context)), context.trace)
                        }
                    } catch (_: DeferredRegionLimitException) {
                        response.respondWoge(
                            failure(FailureCategory.UNAVAILABLE, context.correlationId),
                            context.method,
                            observer,
                            context.observation(),
                        )
                        return@exchange
                    }
                var patchNumber = 0
                response.respondPatches(
                    deferredRegions.execute(regions, context.trace).encodeDeferredPatchStream(
                        patchId = { PatchId.of("deferred-${++patchNumber}") },
                        observer = observer,
                        requestTrace = context.trace,
                        limits = patchLimits,
                    ),
                )
                response.end().coAwait()
            }
        }

    /** Streams bounded SSE invalidations after portable admission and authorization. */
    public fun <Input : Any> live(
        useCase: LiveUseCase<Input>,
        route: PageRoute<Input>,
    ): Handler<RoutingContext> =
        Handler { call ->
            exchange(call) { response, context ->
                if (requestsUnsupportedLiveProtocolVersion(call.queryParam("_woge_protocol_version").singleOrNull())) {
                    response.unsupportedProtocol()
                    return@exchange
                }
                val decoded = route.quarkusInput().decode(call)
                val lastEventId = call.headerValues(LiveEventStream.LAST_EVENT_ID_HEADER)
                val live =
                    withContext(Dispatchers.Default) {
                        liveAdmission.open(useCase, PageRequest(decoded, context), lastEventId, observer)
                    }
                when (live) {
                    is LiveResponse.Refused -> {
                        response.respondWoge(live.failure, context.method, observer, context.observation())
                    }
                    is LiveResponse.Stream -> {
                        response.respondLive(live.events)
                        response.end().coAwait()
                    }
                }
            }
        }

    /** Binds document, safe refresh and live SSE to one generated page route. */
    public fun <Input : Any, Value> liveRegion(
        useCase: LiveRegionPage<Input, Value>,
        route: PageRoute<Input>,
    ): Handler<RoutingContext> =
        Handler { call ->
            val parameters = call.queryParams().names().associateWith { call.queryParam(it).singleOrNull().orEmpty() }
            val epoch = parameters["_woge_live"]
            if (epoch != null) {
                live(LiveUseCase { useCase.subscribe(it, epoch) }, route).handle(call)
            } else {
                page(
                    PageUseCase { request ->
                        if ("_woge_refresh" in parameters) {
                            useCase.refresh(request, route, parameters)
                        } else {
                            useCase.document(request)
                        }
                    },
                    route,
                ).handle(call)
            }
        }

    @Suppress("LongMethod")
    private fun <Input : Any> document(
        useCase: PageUseCase<Input>,
        input: QuarkusPageInput<Input>,
        action: Boolean,
    ): Handler<RoutingContext> {
        val page = sessions.page(useCase)
        return Handler { call ->
            if (action && call.request().method() == HttpMethod.POST) call.request().pause()
            exchange(call) { response, context ->
                val accept = call.headerValues("Accept")
                val method = RequestMethod.of(call.request().method().name())
                if (action && !acceptAction(response, method, accept)) return@exchange
                val actionContext =
                    if (action) {
                        context.requireActionSecurity()
                        context.withMutationRequestIdentity(
                            call.request().headers().getAll(MUTATION_REQUEST_IDENTITY_HEADER),
                        )
                    } else {
                        context
                    }
                val request = PageRequest(input.decode(call), actionContext)
                val condition = call.request().headers().getAll("If-None-Match")
                val modifiedSince = call.request().headers().getAll("If-Modified-Since")
                val cookies =
                    call
                        .request()
                        .headers()
                        .getAll("Cookie")
                        .joinToString(";")
                val result =
                    withContext(Dispatchers.Default) {
                        request.withUploadCleanup {
                            observer.observeOperation(
                                WogeOperation.PAGE_REQUEST,
                                context.observation(),
                                { it.observationOutcome() },
                            ) {
                                initialDocuments.prepare(page.open(request), cookies, context.trace)
                            }
                        }
                    }
                val finalized = result.withFailurePages(failurePages).forHttpRequest(method, condition, modifiedSince)
                val patchAccept = if (action || finalized is PageResult.RegionUpdates) accept.orEmpty() else null
                response.respondWoge(finalized, method, observer, context.observation(), patchAccept)
            }
        }
    }

    private suspend fun acceptAction(
        response: HttpServerResponse,
        method: RequestMethod,
        accept: String?,
    ): Boolean =
        when {
            method != RequestMethod.POST -> {
                response
                    .setStatusCode(HTTP_METHOD_NOT_ALLOWED)
                    .putHeader("Allow", "POST")
                    .putHeader("Cache-Control", "no-store")
                    .end()
                    .coAwait()
                false
            }
            requestsUnsupportedActionPatchVersion(accept) -> {
                response.unsupportedProtocol()
                false
            }
            else -> true
        }

    @Suppress("TooGenericExceptionCaught")
    private fun exchange(
        call: RoutingContext,
        run: suspend (HttpServerResponse, RequestContext) -> Unit,
    ) {
        val response = call.response()
        val dispatcher = checkNotNull(Vertx.currentContext()).dispatcher()
        val job =
            CoroutineScope(dispatcher).launch(start = CoroutineStart.LAZY) {
                var context: RequestContext? = null
                try {
                    context = resolveContext(call) ?: return@launch
                    run(response, context)
                } catch (cancelled: CancellationException) {
                    if (!response.closed() && !response.ended()) response.reset()
                    throw cancelled
                } catch (failure: Exception) {
                    if (response.closed() || response.ended()) return@launch
                    when {
                        response.headWritten() -> response.reset()
                        context != null -> respondFailure(response, context, failure)
                        else -> {
                            logger.error { "Woge request context creation failed" }
                            response.endNoStore(ResponseStatus.INTERNAL_SERVER_ERROR)
                        }
                    }
                } finally {
                    if (!call.request().isEnded && !response.closed()) call.request().resume()
                }
            }
        response.closeHandler { job.cancel() }
        job.start()
    }

    private suspend fun resolveContext(call: RoutingContext): RequestContext? =
        try {
            contexts.create(call).withRenderSession(sessions.create())
        } catch (_: IllegalArgumentException) {
            call.response().endNoStore(ResponseStatus.BAD_REQUEST)
            null
        }

    private suspend fun respondFailure(
        response: HttpServerResponse,
        context: RequestContext,
        exception: Exception,
    ) {
        val category =
            when (exception) {
                is RouteValueException -> exception.category
                is FormDecodingException -> exception.category
                is UploadDecodingException -> exception.category
                is UnverifiedActionSecurityException -> FailureCategory.FORBIDDEN
                is MutationRequestIdentityException -> FailureCategory.BAD_REQUEST
                else -> null
            }
        if (category == null) {
            logger.error { "Woge request failed before streaming (correlationId=${context.correlationId.value})" }
            response.headers().clear()
            response.endNoStore(ResponseStatus.INTERNAL_SERVER_ERROR)
        } else {
            val failure = failure(category, context.correlationId).withFailurePages(failurePages)
            response.respondWoge(failure, context.method, observer, context.observation())
        }
    }
}

private const val HTTP_METHOD_NOT_ALLOWED = 405
private const val HTTP_NOT_ACCEPTABLE = 406

private val logger = KotlinLogging.logger("dev.woge.quarkus.WogeQuarkusHandlers")

private fun RequestContext.observation(): WogeObservationContext = WogeObservationContext(requestTrace = trace)

private suspend fun HttpServerResponse.unsupportedProtocol() {
    setStatusCode(HTTP_NOT_ACCEPTABLE)
        .putHeader("Woge-Protocol-Error", "unsupported-version")
        .putHeader("Cache-Control", "no-store")
        .end()
        .coAwait()
}

private suspend fun HttpServerResponse.endNoStore(status: ResponseStatus) {
    setStatusCode(status.code).putHeader("Cache-Control", "no-store").end().coAwait()
}
