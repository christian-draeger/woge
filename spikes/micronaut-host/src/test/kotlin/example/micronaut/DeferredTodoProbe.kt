package example.micronaut

import dev.woge.host.CookiePath
import dev.woge.host.PatchStreamLimits
import dev.woge.host.RequestContext
import dev.woge.host.ResponseHeaders
import dev.woge.host.ResponseMetadata
import dev.woge.host.SameSite
import dev.woge.host.httpHeader
import dev.woge.host.responseCookie
import dev.woge.runtime.DeferredRegionPolicy
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.security.authentication.Authentication
import kotlinx.coroutines.CompletableDeferred
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.nio.file.Path

internal class DeferredTodoProbe {
    val routeRead = CompletableDeferred<FileRead>()
    val active = Region()
    val completed = Region()
    private val endpoint = TodoDeferredEndpoint(::count)
    private val limited = TodoDeferredEndpoint(::count, PatchStreamLimits(maxPatches = 1))
    private val denied = TodoDeferredEndpoint(::count, deferredPolicy = DeferredRegionPolicy(maxRegions = 1))
    private val fixture = Path.of(requireNotNull(javaClass.getResource("/deferred-counts.txt")).toURI())

    fun render(
        request: HttpRequest<*>,
        authentication: Authentication?,
        limited: Boolean,
        denied: Boolean,
    ): HttpResponse<Flux<ByteArray>> {
        routeRead.complete(FileRead(Thread.currentThread(), readCount("active")))
        val selected = when {
            denied -> this.denied
            limited -> this.limited
            else -> endpoint
        }
        return selected.render(request, authentication)
    }

    fun metadata(): HttpResponse<ByteArray> = ResponseMetadata(
        headers = ResponseHeaders.of(listOf(httpHeader("X-Woge-Proof", "first"), httpHeader("X-Woge-Proof", "second"))),
        cookies = listOf(responseCookie("woge-spike", "fixture").copy(
            path = CookiePath.of("/stream-proof"), sameSite = SameSite.STRICT, maxAgeSeconds = 120,
        )),
    ).micronautResponse<ByteArray>().body("<!doctype html><p>Metadata proof</p>".toByteArray())

    class Region {
        val started = CompletableDeferred<Execution>()
        val release = CompletableDeferred<Unit>()
        val resumed = CompletableDeferred<Execution>()
        val cleanedUp = CompletableDeferred<Unit>()
    }

    data class Execution(val context: RequestContext, val thread: Thread)
    data class FileRead(val thread: Thread, val count: Int)

    private fun readCount(filter: String): Int =
        Files.readAllLines(fixture)[if (filter == "active") 0 else 1].toInt()

    private suspend fun count(context: RequestContext, filter: String): Int {
        val region = if (filter == "active") active else completed
        region.started.complete(Execution(context, Thread.currentThread()))
        try {
            region.release.await()
            val count = readCount(filter)
            region.resumed.complete(Execution(context, Thread.currentThread()))
            return count
        } finally {
            region.cleanedUp.complete(Unit)
        }
    }
}
