package dev.woge.runtime

import dev.woge.host.FailureCategory
import dev.woge.host.PageResult
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObservationId
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.WogeOperationFinished
import dev.woge.host.WogeOperationStarted
import dev.woge.host.WogeOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.TimeSource

/** One failure-isolated semantic operation span used by server adapters and runtimes. */
public class WogeOperationObservation internal constructor(
    private val observer: WogeObserver,
    private val observationId: WogeObservationId,
    private val operation: WogeOperation,
    private val context: WogeObservationContext,
) {
    private val started = TimeSource.Monotonic.markNow()
    private var finished = false

    init {
        observer.emitSafely(WogeOperationStarted(observationId, operation, context))
    }

    /** Emits this operation's terminal event exactly once. */
    public fun finish(outcome: WogeOutcome) {
        if (finished) return
        finished = true
        observer.emitSafely(WogeOperationFinished(observationId, operation, outcome, started.elapsedNow(), context))
    }
}

/** Starts one operation. Observer failures are isolated from application work. */
public fun WogeObserver.startOperation(
    operation: WogeOperation,
    context: WogeObservationContext = WogeObservationContext(),
): WogeOperationObservation = WogeOperationObservation(this, nextObservationId(), operation, context)

/** Runs suspending work with one started event and exactly one classified terminal event. */
@Suppress("TooGenericExceptionCaught")
public suspend fun <Result> WogeObserver.observeOperation(
    operation: WogeOperation,
    context: WogeObservationContext = WogeObservationContext(),
    successfulOutcome: (Result) -> WogeOutcome = { WogeOutcome.SUCCEEDED },
    block: suspend () -> Result,
): Result {
    val observation = startOperation(operation, context)
    return try {
        block().also { observation.finish(successfulOutcome(it)) }
    } catch (cancelled: CancellationException) {
        observation.finish(WogeOutcome.CANCELLED)
        throw cancelled
    } catch (failure: Throwable) {
        observation.finish(WogeOutcome.FAILED)
        throw failure
    }
}

/** Observes actual cold-flow collection rather than flow construction. */
public fun <Value> Flow<Value>.observeCollection(
    observer: WogeObserver,
    operation: WogeOperation,
    context: WogeObservationContext = WogeObservationContext(),
): Flow<Value> =
    flow {
        observer.observeOperation(operation, context) {
            collect(::emit)
        }
    }

/** Classifies an explicit page outcome without inspecting application payloads. */
public fun PageResult.observationOutcome(): WogeOutcome =
    when (this) {
        is PageResult.Document,
        is PageResult.Redirect,
        -> WogeOutcome.SUCCEEDED

        is PageResult.Failure ->
            when (failure.category) {
                FailureCategory.INTERNAL,
                FailureCategory.UNAVAILABLE,
                -> WogeOutcome.FAILED

                else -> WogeOutcome.REJECTED
            }
    }

@Suppress("SwallowedException", "TooGenericExceptionCaught")
private fun WogeObserver.emitSafely(event: dev.woge.host.WogeObservationEvent) {
    try {
        onEvent(event)
    } catch (_: Exception) {
        // Observability is best effort and must never alter application behavior.
    }
}

private fun nextObservationId(): WogeObservationId {
    val value = observationSequence.incrementAndGet()
    check(value > 0) { "Woge observation ID space exhausted" }
    return WogeObservationId.of(value)
}

private val observationSequence: AtomicLong = AtomicLong()
