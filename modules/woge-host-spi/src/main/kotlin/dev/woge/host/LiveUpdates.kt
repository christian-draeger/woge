package dev.woge.host

import dev.woge.protocol.RegionTargetId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Opens a live-update stream (Server-Sent Events) for one page.
 *
 * Authorize [request] here, exactly like a page GET. Return [liveSubscription] to allow the stream
 * or [liveRefused] with a failure status. The browser reconnects automatically, so this runs again
 * at least once per [LiveLimits.maxLifetime].
 */
public fun interface LiveUseCase<Input : Any> {
    public suspend fun subscribe(request: PageRequest<Input>): LiveResult
}

/** The outcome of [LiveUseCase.subscribe]. */
public sealed interface LiveResult {
    /** The stream is refused with a normal failure status, for example 403. */
    public class Refused internal constructor(
        public val failure: PageResult.Failure,
    ) : LiveResult

    /** An accepted stream. Create it with [liveSubscription]. */
    public class Subscription internal constructor(
        public val targets: Set<RegionTargetId>,
        public val session: LiveSessionKey?,
        public val invalidations: Flow<RegionTargetId>,
    ) : LiveResult
}

/**
 * Accepts a live stream for [targets]: the regions this page lets the browser refresh.
 *
 * [invalidations] emits a target whenever its content changed. The stream sends only the target
 * id; the browser then loads the region with its normal, authorized region GET. Emitting a target
 * that is not in [targets] ends the stream. When the flow completes, the stream ends and the
 * browser reconnects, which runs authorization again.
 *
 * [session] groups streams for the per-session limit, for example the login session id.
 */
public fun liveSubscription(
    targets: Collection<RegionTarget<*>>,
    invalidations: Flow<RegionTarget<*>>,
    session: LiveSessionKey? = null,
): LiveResult.Subscription {
    val ids = targets.mapTo(linkedSetOf()) { it.target.region }
    require(ids.isNotEmpty()) { "A live subscription needs at least one region target" }
    require(ids.size <= LiveLimits.MAX_TARGETS) {
        "A live subscription may declare at most ${LiveLimits.MAX_TARGETS} region targets"
    }
    return LiveResult.Subscription(ids, session, invalidations.map { it.target.region })
}

/** Refuses a live stream with a normal failure status. */
public fun liveRefused(
    category: FailureCategory,
    correlationId: CorrelationId,
): LiveResult.Refused = LiveResult.Refused(failure(category, correlationId))

/** An opaque key that groups live streams for [LiveLimits.maxSubscriptionsPerSession]. Never sent to the browser. */
@JvmInline
public value class LiveSessionKey private constructor(
    public val value: String,
) {
    override fun toString(): String = "LiveSessionKey(***)"

    public companion object {
        private const val MAX_LENGTH = 256

        public fun of(value: String): LiveSessionKey {
            require(value.length in 1..MAX_LENGTH) { "Live session key must contain 1 to $MAX_LENGTH characters" }
            return LiveSessionKey(value)
        }
    }
}

/**
 * Bounds for live streams. One owner (the host's handler factory) enforces the subscription limits
 * for the whole application.
 */
public data class LiveLimits(
    public val maxSubscriptions: Int = DEFAULT_MAX_SUBSCRIPTIONS,
    public val maxSubscriptionsPerSession: Int = DEFAULT_MAX_SUBSCRIPTIONS_PER_SESSION,
    public val heartbeat: Duration = 15.seconds,
    public val maxLifetime: Duration = 30.minutes,
    public val retry: Duration = 3.seconds,
) {
    init {
        require(maxSubscriptions > 0) { "Live subscription limit must be positive" }
        require(maxSubscriptionsPerSession in 1..maxSubscriptions) {
            "Per-session live subscription limit must be positive and not above the application limit"
        }
        require(heartbeat.isPositive() && heartbeat.isFinite()) { "Live heartbeat must be positive and finite" }
        require(maxLifetime.isPositive() && maxLifetime.isFinite()) { "Live lifetime must be positive and finite" }
        require(heartbeat < maxLifetime) { "Live heartbeat must be shorter than the maximum lifetime" }
        require(retry.isPositive() && retry.isFinite()) { "Live retry hint must be positive and finite" }
    }

    public companion object {
        public const val MAX_TARGETS: Int = 128
        public const val DEFAULT_MAX_SUBSCRIPTIONS: Int = 1024
        public const val DEFAULT_MAX_SUBSCRIPTIONS_PER_SESSION: Int = 8
    }
}
