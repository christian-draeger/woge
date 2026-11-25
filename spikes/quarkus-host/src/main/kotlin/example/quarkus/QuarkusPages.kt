package example.quarkus

import dev.woge.examples.gradient.t01staticpage.StaticPage
import dev.woge.examples.gradient.t01staticpage.StaticPageInput
import dev.woge.host.CorrelationId
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestTrace
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.html.HtmlByteBudget
import dev.woge.runtime.observeCollection
import dev.woge.runtime.renderByteChunks
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.Cookie
import io.vertx.core.http.CookieSameSite
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jboss.logging.Logger
import java.util.UUID

@ApplicationScoped
class QuarkusPages {
    fun register(@Observes router: Router) {
        val site = StaticPage()
        router.get("/").handler { call -> serve(call, site, StaticPageInput) }
        router.head("/").handler { call -> serve(call, site, StaticPageInput) }
    }
}

private val logger = Logger.getLogger("example.quarkus.QuarkusPages")

internal fun <Input : Any> serve(
    call: RoutingContext,
    useCase: PageUseCase<Input>,
    input: Input,
    observer: WogeObserver = WogeObserver.NONE,
) {
    exchange(call) { response, context ->
        val result = withContext(Dispatchers.Default) { useCase.open(PageRequest(input, context)) }
        reply(response, result, context.method, observer, context.trace)
    }
}

internal fun exchange(
    call: RoutingContext,
    handle: suspend (HttpServerResponse, RequestContext) -> Unit,
) {
    val response = call.response()
    val method = RequestMethod.of(call.request().method().name())
    val requestId = UUID.randomUUID().toString()
    val trace = RequestTrace(RequestId.of(requestId), CorrelationId.of(requestId))
    val context = RequestContext(method = method, trace = trace)
    val eventLoop = checkNotNull(Vertx.currentContext())
    val job = CoroutineScope(eventLoop.dispatcher()).launch(start = CoroutineStart.LAZY) {
        try {
            handle(response, context)
        } catch (cancelled: CancellationException) {
            if (!response.closed() && !response.ended()) response.reset()
            throw cancelled
        } catch (_: Exception) {
            logger.debug("Woge document failed (correlationId=$requestId)")
            if (!response.headWritten() && !response.closed()) {
                response.headers().clear()
                response.setStatusCode(500).end().coAwait()
            } else if (!response.closed()) {
                response.reset()
            }
        }
    }
    response.closeHandler { job.cancel() }
    job.start()
}

private suspend fun reply(
    response: HttpServerResponse,
    result: PageResult,
    method: RequestMethod,
    observer: WogeObserver,
    trace: RequestTrace,
) {
    response.setStatusCode(result.metadata.status.code)
    result.metadata.headers.forEach { response.headers().add(it.name.value, it.value.value) }
    result.metadata.contentType?.let { response.putHeader("Content-Type", it.value) }
    result.metadata.cookies.forEach { cookie ->
        response.addCookie(Cookie.cookie(cookie.name.value, cookie.value.value).apply {
            path = cookie.path.value
            isSecure = cookie.secure
            isHttpOnly = cookie.httpOnly
            sameSite = CookieSameSite.valueOf(cookie.sameSite.name)
            cookie.maxAgeSeconds?.let { maxAge = it }
        })
    }
    when (result) {
        is PageResult.Redirect -> response.putHeader("Location", result.location.value)
        is PageResult.Document -> if (method != RequestMethod.HEAD) {
            val chunks = flow {
                val budget = HtmlByteBudget(result.maxBytes)
                val observation = WogeObservationContext(requestTrace = trace)
                result.frames.observeCollection(observer, WogeOperation.SHELL_RENDER, observation).collect { frame ->
                    frame.renderByteChunks(budget, observer, observation).forEach { emit(it) }
                }
            }.flowOn(Dispatchers.Default).buffer(0)
            response.setChunked(true)
            chunks.collect { bytes ->
                response.write(Buffer.buffer(bytes)).coAwait()
            }
        }
        is PageResult.Failure, is PageResult.NotModified -> Unit
        is PageResult.RegionUpdates -> error("The read-only Quarkus proof does not serve action results")
    }
    response.end().coAwait()
}
