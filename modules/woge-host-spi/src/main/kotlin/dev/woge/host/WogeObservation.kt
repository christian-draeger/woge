package dev.woge.host

import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchTarget
import kotlin.time.Duration

/** Process-local identity used to pair overlapping started and finished events. */
@JvmInline
public value class WogeObservationId private constructor(
    public val value: Long,
) {
    public companion object {
        public fun of(value: Long): WogeObservationId {
            require(value > 0) { "Observation ID must be positive" }
            return WogeObservationId(value)
        }
    }
}

/** Stable, low-cardinality semantic operations shared by Woge hosts and tooling. */
public enum class WogeOperation(
    public val semanticName: String,
) {
    PAGE_REQUEST("page.request"),
    SHELL_RENDER("shell.render"),
    DEFERRED_REGION("deferred.region"),
    ACTION("action"),
    PATCH_ENCODE("patch.encode"),
    PATCH_APPLY("patch.apply"),
    LIVE_SUBSCRIPTION("live.subscription"),
    RECOVERY("recovery"),
}

/** Stable terminal outcomes. These values are safe to use as metric dimensions. */
public enum class WogeOutcome(
    public val semanticName: String,
) {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    TIMED_OUT("timed_out"),
    REJECTED("rejected"),
    STALE("stale"),
}

/**
 * Safe correlation carried by semantic events.
 *
 * Every field is high-cardinality and must be attached to traces or diagnostics, never used as a
 * metric label. There is deliberately no route, user input, HTML, command, token, or exception
 * message field.
 */
public data class WogeObservationContext(
    public val requestTrace: RequestTrace? = null,
    public val target: PatchTarget? = null,
    public val patchId: PatchId? = null,
)

/** One structured semantic lifecycle event emitted by a Woge runtime boundary. */
public interface WogeObservationEvent {
    public val observationId: WogeObservationId
    public val operation: WogeOperation
    public val context: WogeObservationContext
}

/** Marks the beginning of one semantic operation. */
public data class WogeOperationStarted(
    override val observationId: WogeObservationId,
    override val operation: WogeOperation,
    override val context: WogeObservationContext = WogeObservationContext(),
) : WogeObservationEvent

/** Marks the single terminal outcome of one previously started semantic operation. */
public data class WogeOperationFinished(
    override val observationId: WogeObservationId,
    override val operation: WogeOperation,
    public val outcome: WogeOutcome,
    public val duration: Duration,
    override val context: WogeObservationContext = WogeObservationContext(),
) : WogeObservationEvent {
    init {
        require(duration.isFinite() && duration >= Duration.ZERO) {
            "Observed duration must be non-negative and finite"
        }
    }
}

/**
 * Framework-neutral destination for Woge semantic events.
 *
 * Implementations should return quickly and must not throw. Woge runtime call sites additionally
 * isolate observer failures so diagnostics cannot change an application response.
 */
public fun interface WogeObserver {
    public fun onEvent(event: WogeObservationEvent)

    public companion object {
        /** Shared no-op default used when an application installs no observation adapter. */
        public val NONE: WogeObserver = WogeObserver { }
    }
}
