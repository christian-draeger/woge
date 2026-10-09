package dev.woge.development.orchestrator

import dev.woge.development.DevelopmentEvent
import dev.woge.development.DevelopmentSessionState
import dev.woge.development.ExperimentalWogeDevelopmentApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** One applied lifecycle event with its monotonic position and the resulting state. */
@ExperimentalWogeDevelopmentApi
public class DevelopmentEventRecord internal constructor(
    public val sequence: Long,
    public val event: DevelopmentEvent,
    public val state: DevelopmentSessionState,
) {
    override fun toString(): String = "DevelopmentEventRecord(sequence=$sequence, event=${event::class.simpleName})"
}

/**
 * Tuning for one development session.
 *
 * @property quietPeriod collects rapid edits for this long before one build starts. Zero starts a
 * build for every change batch and supersedes a running build instead.
 * @property eventReplay how many recent events late subscribers can still see.
 * @property shutdownTimeout maximum time the host adapter gets to stop its child process.
 * @property internalErrorListener receives unexpected adapter exceptions. They never appear in
 * diagnostics because they may contain paths or secrets.
 */
@ExperimentalWogeDevelopmentApi
public class DevelopmentOrchestratorOptions(
    public val quietPeriod: Duration = Duration.ZERO,
    public val eventReplay: Int = DEFAULT_EVENT_REPLAY,
    public val shutdownTimeout: Duration = 10.seconds,
    public val timeSource: TimeSource = TimeSource.Monotonic,
    public val internalErrorListener: (Throwable) -> Unit = {},
) {
    init {
        require(quietPeriod.isFinite() && !quietPeriod.isNegative()) { "Quiet period must be finite and non-negative" }
        require(eventReplay > 0) { "Event replay must be positive" }
        require(shutdownTimeout.isFinite() && shutdownTimeout.isPositive()) {
            "Shutdown timeout must be finite and positive"
        }
    }

    private companion object {
        const val DEFAULT_EVENT_REPLAY: Int = 64
    }
}
