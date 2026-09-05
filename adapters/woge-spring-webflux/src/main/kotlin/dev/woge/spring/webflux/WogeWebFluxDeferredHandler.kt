package dev.woge.spring.webflux

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageRequest
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
) {
    private val executor = DeferredRegionExecutor(DeferredRegionPolicy(maxConcurrency, regionTimeout))

    /** Re-authorizes the request before returning an incrementally flushed patch response. */
    public suspend fun handle(request: ServerRequest): ServerResponse {
        val pageRequest = PageRequest(input.decode(request), contexts.create(request))
        val declaredRegions = regions.regions(pageRequest).toList()
        var patchNumber = 0
        val chunks =
            executor.execute(declaredRegions).encodeDeferredPatchStream {
                patchNumber += 1
                PatchId.of("deferred-$patchNumber")
            }
        return chunks.toWebFluxPatchResponse()
    }
}
