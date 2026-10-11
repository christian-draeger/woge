package dev.woge.host

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Bounded resources per connection. Overflow closes with 1013; malformed input closes with 1008. */
public data class WebSocketLimits(
    public val subscriptions: Int = DEFAULT_SUBSCRIPTIONS,
    public val inboundBytes: Int = DEFAULT_INBOUND_BYTES,
    public val messagesPerSecond: Int = DEFAULT_MESSAGES_PER_SECOND,
    public val queuedFrames: Int = DEFAULT_QUEUE,
    public val frameBytes: Int = DEFAULT_FRAME_BYTES,
    public val heartbeat: Duration = HEARTBEAT_SECONDS.seconds,
    public val idleTimeout: Duration = IDLE_SECONDS.seconds,
) {
    /** Forced reconnect reruns upgrade authorization even when a client keeps answering heartbeats. */
    public val maxLifetime: Duration = LIFETIME_MINUTES.minutes

    init {
        require(
            subscriptions in 1..SOCKET_MAX_TARGETS &&
                inboundBytes in 1..MAX_INBOUND_BYTES &&
                messagesPerSecond in 1..MAX_MESSAGES_PER_SECOND,
        )
        require(queuedFrames in 1..SOCKET_MAX_TARGETS && frameBytes in MIN_FRAME_BYTES..MAX_FRAME_BYTES)
        require(heartbeat.inWholeMilliseconds > 0 && heartbeat.isFinite())
        require(idleTimeout.isFinite() && idleTimeout > heartbeat)
    }
}

private const val DEFAULT_SUBSCRIPTIONS = 16
private const val DEFAULT_INBOUND_BYTES = 4096
private const val DEFAULT_MESSAGES_PER_SECOND = 64
private const val DEFAULT_QUEUE = 8
private const val DEFAULT_FRAME_BYTES = 512 * 1024
private const val MAX_INBOUND_BYTES = 65536
private const val MAX_MESSAGES_PER_SECOND = 1024
private const val MIN_FRAME_BYTES = 1024
private const val MAX_FRAME_BYTES = 8 * 1024 * 1024
private const val HEARTBEAT_SECONDS = 15
private const val IDLE_SECONDS = 45
private const val LIFETIME_MINUTES = 30
