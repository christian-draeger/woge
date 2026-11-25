package example.micronaut

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PatchStreamLimits
import dev.woge.host.RequestContext
import dev.woge.host.WogeObservationContext
import dev.woge.html.HtmlByteBudget
import dev.woge.runtime.DeferredRegionLimitException
import dev.woge.runtime.DeferredRegionPolicy
import dev.woge.runtime.InitialDocumentPolicy
import dev.woge.runtime.InitialDocumentRuntime
import dev.woge.runtime.renderByteChunks
import example.todo.todos
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.security.authentication.Authentication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Flux

internal class TodoDeferredEndpoint @JvmOverloads constructor(
    count: suspend (RequestContext, String) -> Int = { _, filter -> todos.snapshot(filter).visible.size },
    patchLimits: PatchStreamLimits = PatchStreamLimits(),
    deferredPolicy: DeferredRegionPolicy = DeferredRegionPolicy(maxConcurrency = 2),
) {
    private val page = TodoDeferredPage(count)
    private val runtime = InitialDocumentRuntime(
        InitialDocumentPolicy(deferred = deferredPolicy, patches = patchLimits),
    )

    // The controller opens the document on the Micronaut I/O executor. Collection has its own
    // bounded dispatcher so a subscriber running on Netty cannot run page work on its event loop.
    fun render(request: HttpRequest<*>, authentication: Authentication?): HttpResponse<Flux<ByteArray>> = runBlocking {
        val context = TodoEndpoints.requestContext(request, authentication)
        val document = try {
            runtime.prepare(page.open(PageRequest(Unit, context)), null, context.trace) as PageResult.Document
        } catch (_: DeferredRegionLimitException) {
            return@runBlocking HttpResponse.status<Flux<ByteArray>>(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Cache-Control", "no-store")
        }
        val observation = WogeObservationContext(requestTrace = context.trace)
        val body = flow {
            val budget = HtmlByteBudget(document.maxBytes)
            document.frames.collect { frame ->
                frame.renderByteChunks(budget, observationContext = observation).forEach { emit(it) }
            }
        }.asFlux(renderWorkers)
        document.metadata.micronautResponse<Flux<ByteArray>>().body(body)
    }

    private companion object {
        val renderWorkers = Dispatchers.IO.limitedParallelism(2)
    }
}
