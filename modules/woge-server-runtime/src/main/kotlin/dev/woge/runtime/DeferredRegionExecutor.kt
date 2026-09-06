package dev.woge.runtime

import dev.woge.host.DeferredRegion
import dev.woge.host.DeferredRegionFailure
import dev.woge.host.RequestTrace
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.WogeOutcome
import dev.woge.protocol.PatchHtml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Request-scoped concurrency and per-region timeout policy. */
public data class DeferredRegionPolicy(
    public val maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
    public val regionTimeout: Duration = DEFAULT_REGION_TIMEOUT,
) {
    init {
        require(maxConcurrency > 0) { "Deferred region concurrency must be positive" }
        require(regionTimeout.isFinite() && regionTimeout > Duration.ZERO) {
            "Deferred region timeout must be positive and finite"
        }
    }

    public companion object {
        public const val DEFAULT_MAX_CONCURRENCY: Int = 8
        public val DEFAULT_REGION_TIMEOUT: Duration = 30.seconds
    }
}

/** One rendered region update, ready for a transport-specific patch mapper. */
public sealed interface DeferredRegionUpdate {
    public val region: DeferredRegion
    public val html: PatchHtml

    /** The deferred content completed normally. */
    public class Resolved(
        override val region: DeferredRegion,
        override val html: PatchHtml,
    ) : DeferredRegionUpdate

    /** Deferred work failed, but the region supplied controlled replacement content. */
    public class Failed(
        override val region: DeferredRegion,
        public val failure: DeferredRegionFailure,
        override val html: PatchHtml,
        public val cause: Exception?,
    ) : DeferredRegionUpdate
}

/** Runs deferred regions as children of the collecting request scope. */
public class DeferredRegionExecutor(
    public val policy: DeferredRegionPolicy = DeferredRegionPolicy(),
    private val observer: WogeObserver = WogeObserver.NONE,
) {
    /**
     * Returns a cold flow that emits updates in completion order.
     *
     * Cancelling collection cancels active and waiting region children. A content failure is
     * isolated to its region; failure-fallback rendering itself remains fail-stop.
     */
    public fun execute(
        regions: Iterable<DeferredRegion>,
        requestTrace: RequestTrace? = null,
    ): Flow<DeferredRegionUpdate> =
        channelFlow {
            val declaredRegions = regions.toList()
            validateRegionSet(declaredRegions)
            val concurrency = Semaphore(policy.maxConcurrency)

            declaredRegions.forEach { region ->
                launch {
                    concurrency.withPermit {
                        send(resolve(region, requestTrace))
                    }
                }
            }
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolve(
        region: DeferredRegion,
        requestTrace: RequestTrace?,
    ): DeferredRegionUpdate {
        val observation =
            observer.startOperation(
                WogeOperation.DEFERRED_REGION,
                WogeObservationContext(requestTrace = requestTrace, target = region.target),
            )
        return resolveObserved(region, observation)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolveObserved(
        region: DeferredRegion,
        observation: WogeOperationObservation,
    ): DeferredRegionUpdate =
        try {
            val content = withTimeoutOrNull(policy.regionTimeout) { region.renderContent() }
            if (content == null) {
                failed(region, DeferredRegionFailure.TIMED_OUT, cause = null)
                    .also { observation.finish(WogeOutcome.TIMED_OUT) }
            } else {
                DeferredRegionUpdate
                    .Resolved(region, content)
                    .also { observation.finish(WogeOutcome.SUCCEEDED) }
            }
        } catch (cancelled: CancellationException) {
            observation.finish(WogeOutcome.CANCELLED)
            throw cancelled
        } catch (cause: Exception) {
            try {
                failed(region, DeferredRegionFailure.FAILED, cause)
                    .also { observation.finish(WogeOutcome.FAILED) }
            } catch (fallbackFailure: Throwable) {
                observation.finish(WogeOutcome.FAILED)
                throw fallbackFailure
            }
        }

    private fun failed(
        region: DeferredRegion,
        failure: DeferredRegionFailure,
        cause: Exception?,
    ): DeferredRegionUpdate.Failed =
        DeferredRegionUpdate.Failed(
            region = region,
            failure = failure,
            html = region.renderFailure(failure),
            cause = cause,
        )
}

private fun validateRegionSet(regions: List<DeferredRegion>) {
    val targets = regions.map { it.target }
    require(targets.distinct().size == targets.size) { "Deferred region targets must be unique" }
    require(targets.map { it.pageEpoch }.distinct().size <= 1) {
        "Deferred regions in one execution must belong to the same page epoch"
    }
}
