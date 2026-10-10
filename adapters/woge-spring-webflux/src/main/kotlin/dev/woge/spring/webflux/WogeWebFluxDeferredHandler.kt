package dev.woge.spring.webflux

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageRequest
import dev.woge.host.RouteValueException
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.failure
import dev.woge.protocol.PatchId
import dev.woge.runtime.DeferredRegionExecutor
import dev.woge.runtime.DeferredRegionPolicy
import dev.woge.runtime.encodeDeferredPatchStream
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Executes page-scoped deferred work and streams patches through a WebFlux functional route. */
public class WogeWebFluxDeferredHandler<Input : Any>(
    private val regions: DeferredRegionsUseCase<Input>,
    private val input: WebFluxPageInput<Input>,
    private val contexts: WebFluxRequestContextFactory = DefaultWebFluxRequestContextFactory,
    maxConcurrency: Int = DeferredRegionPolicy.DEFAULT_MAX_CONCURRENCY,
    regionTimeout: Duration = 30.seconds,
    observer: WogeObserver = WogeObserver.NONE,
) {
    private val executor = DeferredRegionExecutor(DeferredRegionPolicy(maxConcurrency, regionTimeout), observer)
    private val observer = observer

    /** Re-authorizes the request before returning an incrementally flushed patch response. */
    public suspend fun handle(request: ServerRequest): ServerResponse {
        val context = contexts.create(request)
        val decoded =
            try {
                input.decode(request)
            } catch (invalid: RouteValueException) {
                return failure(invalid.category, context.correlationId)
                    .toWebFluxResponse(observer, WogeObservationContext(requestTrace = context.trace))
            }
        val pageRequest = PageRequest(decoded, context)
        val declaredRegions = regions.regions(pageRequest).toList()
        var patchNumber = 0
        val chunks =
            executor
                .execute(declaredRegions, context.trace)
                .encodeDeferredPatchStream(
                    patchId = {
                        patchNumber += 1
                        PatchId.of("deferred-$patchNumber")
                    },
                    observer = observer,
                    requestTrace = context.trace,
                )
        return chunks.toWebFluxPatchResponse()
    }
}
