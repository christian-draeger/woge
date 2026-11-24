package example.micronaut

import dev.woge.host.PatchStreamLimits
import dev.woge.host.RequestContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.security.authentication.Authentication
import kotlinx.coroutines.CompletableDeferred
import reactor.core.publisher.Flux

internal class DeferredTodoProbe {
    val active = Region()
    val completed = Region()
    private val endpoint = TodoDeferredEndpoint(::count)
    private val limited = TodoDeferredEndpoint(::count, PatchStreamLimits(maxPatches = 1))

    fun render(
        request: HttpRequest<*>,
        authentication: Authentication?,
        limited: Boolean,
    ): HttpResponse<Flux<ByteArray>> = (if (limited) this.limited else endpoint).render(request, authentication)

    class Region {
        val started = CompletableDeferred<Execution>()
        val release = CompletableDeferred<Int>()
        val resumed = CompletableDeferred<Execution>()
        val cleanedUp = CompletableDeferred<Unit>()
    }

    data class Execution(val context: RequestContext, val thread: Thread)

    private suspend fun count(context: RequestContext, filter: String): Int {
        val region = if (filter == "active") active else completed
        region.started.complete(Execution(context, Thread.currentThread()))
        try {
            val count = region.release.await()
            region.resumed.complete(Execution(context, Thread.currentThread()))
            return count
        } finally {
            region.cleanedUp.complete(Unit)
        }
    }
}
