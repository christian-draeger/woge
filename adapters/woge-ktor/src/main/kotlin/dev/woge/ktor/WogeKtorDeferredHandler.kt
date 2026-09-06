package dev.woge.ktor

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageRequest
import dev.woge.host.WogeObserver
import dev.woge.protocol.PatchId
import dev.woge.runtime.DeferredRegionExecutor
import dev.woge.runtime.DeferredRegionPolicy
import dev.woge.runtime.encodeDeferredPatchStream
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration

/** Executes page-scoped deferred work and streams patches through a Ktor route. */
public class WogeKtorDeferredHandler<Input : Any> internal constructor(
    private val regions: DeferredRegionsUseCase<Input>,
    private val input: KtorPageInput<Input>,
    private val contexts: KtorRequestContextFactory,
    maxConcurrency: Int,
    regionTimeout: Duration,
    observer: WogeObserver,
) {
    private val executor = DeferredRegionExecutor(DeferredRegionPolicy(maxConcurrency, regionTimeout), observer)
    private val observer = observer

    /** Re-authorizes the request before returning an incrementally flushed patch response. */
    @Suppress("TooGenericExceptionCaught")
    public suspend fun handle(call: ApplicationCall) {
        val context = contexts.create(call)
        val pageRequest = PageRequest(input.decode(call), context)
        val declaredRegions =
            try {
                regions.regions(pageRequest).toList()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                call.respondWogePreStreamFailure(failure)
                return
            }
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
        call.respondWogePatches(chunks)
    }
}
