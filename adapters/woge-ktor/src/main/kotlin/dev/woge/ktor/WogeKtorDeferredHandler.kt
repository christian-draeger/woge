package dev.woge.ktor

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.FailureCategory
import dev.woge.host.PageRequest
import dev.woge.host.PatchStreamLimits
import dev.woge.host.RouteValueException
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.failure
import dev.woge.protocol.PatchId
import dev.woge.runtime.DeferredRegionExecutor
import dev.woge.runtime.DeferredRegionLimitException
import dev.woge.runtime.DeferredRegionPolicy
import dev.woge.runtime.encodeDeferredPatchStream
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration

/** Executes page-scoped deferred work and streams patches through a Ktor route. */
@Suppress("LongParameterList")
public class WogeKtorDeferredHandler<Input : Any> internal constructor(
    private val regions: DeferredRegionsUseCase<Input>,
    private val input: KtorPageInput<Input>,
    private val contexts: KtorRequestContextFactory,
    maxConcurrency: Int,
    regionTimeout: Duration,
    observer: WogeObserver,
    maxRegions: Int,
    private val patchStreamLimits: PatchStreamLimits,
) {
    private val executor =
        DeferredRegionExecutor(DeferredRegionPolicy(maxConcurrency, regionTimeout, maxRegions), observer)
    private val observer = observer

    /** Re-authorizes the request before returning an incrementally flushed patch response. */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    public suspend fun handle(call: ApplicationCall) {
        val context = contexts.create(call)
        val decoded =
            try {
                input.decode(call)
            } catch (invalid: RouteValueException) {
                call.respondWogePage(
                    failure(invalid.category, context.correlationId),
                    observer,
                    WogeObservationContext(requestTrace = context.trace),
                )
                return
            }
        val declaredRegions =
            try {
                val pageRequest = PageRequest(decoded, context)
                executor.prepare(regions.regions(pageRequest), context.trace)
            } catch (_: DeferredRegionLimitException) {
                call.respondWogePage(
                    failure(FailureCategory.UNAVAILABLE, context.correlationId),
                    observer,
                    WogeObservationContext(requestTrace = context.trace),
                )
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                call.respondWogePreStreamFailure(cause)
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
                    limits = patchStreamLimits,
                )
        call.respondWogePatches(chunks)
    }
}
