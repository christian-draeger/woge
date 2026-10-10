package dev.woge.runtime

import dev.woge.host.FailureCategory
import dev.woge.host.LiveLimits
import dev.woge.host.LiveResult
import dev.woge.host.LiveSessionKey
import dev.woge.host.LiveUseCase
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.ResourceLimit
import dev.woge.host.ResourceLimitExceeded
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.WogeOutcome
import dev.woge.host.failure
import dev.woge.protocol.RegionTargetId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/** Wire constants of the live Server-Sent Events stream. Hosts set these headers on every stream. */
public object LiveEventStream {
    public const val MEDIA_TYPE: String = "text/event-stream"
    public const val LAST_EVENT_ID_HEADER: String = "Last-Event-ID"

    /** Response headers besides the content type. `X-Accel-Buffering` stops nginx-style proxy buffering. */
    public val HEADERS: Map<String, String> = mapOf("Cache-Control" to "no-store", "X-Accel-Buffering" to "no")
}

/** What a host writes for one live request. */
public sealed interface LiveResponse {
    /** A bodyless failure: refused by the application or by a subscription limit. */
    public class Refused internal constructor(
        public val failure: PageResult.Failure,
    ) : LiveResponse

    /** An admitted stream. Write each chunk and flush. The admission slot is released when collection ends. */
    public class Stream internal constructor(
        public val events: Flow<ByteArray>,
    ) : LiveResponse
}

/**
 * Admits live streams for one application. Create one instance per handler factory and share it by
 * every live route, so [LiveLimits.maxSubscriptions] counts the whole application.
 */
public class LiveAdmission(
    public val limits: LiveLimits = LiveLimits(),
) {
    private val lock = Any()
    private var total = 0
    private val perSession = HashMap<LiveSessionKey, Int>()

    /** Number of currently open streams. */
    public val openStreams: Int
        get() = synchronized(lock) { total }

    /**
     * Authorizes through [useCase], applies the subscription limits and prepares the stream.
     *
     * [lastEventId] is the browser's `Last-Event-ID` header. When present, the stream starts with a
     * `resync` event for every declared target.
     */
    public suspend fun <Input : Any> open(
        useCase: LiveUseCase<Input>,
        request: PageRequest<Input>,
        lastEventId: String?,
        observer: WogeObserver = WogeObserver.NONE,
    ): LiveResponse {
        val context = WogeObservationContext(requestTrace = request.context.trace)
        val result = useCase.subscribe(request)
        val refusal =
            when (result) {
                is LiveResult.Refused -> {
                    observer.startOperation(WogeOperation.LIVE_SUBSCRIPTION, context).finish(WogeOutcome.REJECTED)
                    result.failure
                }
                is LiveResult.Subscription -> refuseOverLimit(result.session, request, context, observer)
            }
        if (refusal != null) return LiveResponse.Refused(refusal)
        val subscription = result as LiveResult.Subscription
        val released = AtomicBoolean()
        val events =
            liveEvents(subscription, resumeId(lastEventId), limits)
                .onCompletion { if (released.compareAndSet(false, true)) release(subscription.session) }
                .observeCollection(observer, WogeOperation.LIVE_SUBSCRIPTION, context)
        return LiveResponse.Stream(events)
    }

    private fun refuseOverLimit(
        session: LiveSessionKey?,
        request: PageRequest<*>,
        context: WogeObservationContext,
        observer: WogeObserver,
    ): PageResult.Failure? {
        val exceeded = admit(session) ?: return null
        observer
            .startOperation(WogeOperation.LIVE_SUBSCRIPTION, context.copy(exceededLimit = exceeded))
            .finish(WogeOutcome.REJECTED)
        val category =
            if (exceeded.limit == ResourceLimit.LIVE_SUBSCRIPTIONS) {
                FailureCategory.UNAVAILABLE
            } else {
                FailureCategory.RATE_LIMITED
            }
        return failure(category, request.context.correlationId)
    }

    private fun admit(session: LiveSessionKey?): ResourceLimitExceeded? =
        synchronized(lock) {
            val sessionCount = session?.let { perSession[it] } ?: 0
            when {
                session != null && sessionCount >= limits.maxSubscriptionsPerSession ->
                    ResourceLimitExceeded(
                        ResourceLimit.LIVE_SESSION_SUBSCRIPTIONS,
                        limits.maxSubscriptionsPerSession.toLong(),
                    )
                total >= limits.maxSubscriptions ->
                    ResourceLimitExceeded(ResourceLimit.LIVE_SUBSCRIPTIONS, limits.maxSubscriptions.toLong())
                else -> {
                    total += 1
                    if (session != null) perSession[session] = sessionCount + 1
                    null
                }
            }
        }

    private fun release(session: LiveSessionKey?) {
        synchronized(lock) {
            total -= 1
            if (session != null) {
                val remaining = (perSession[session] ?: 1) - 1
                if (remaining == 0) perSession.remove(session) else perSession[session] = remaining
            }
        }
    }
}

/** The id the reconnecting browser saw last, or null for a first connection. Malformed ids still resync. */
private fun resumeId(lastEventId: String?): Long? =
    lastEventId?.let { it.toLongOrNull()?.takeIf { id -> id in 0..MAX_RESUME_ID } ?: 0L }

/**
 * Encodes one subscription as SSE bytes: a retry hint, an optional resync, then coalesced
 * invalidations and idle heartbeats until the flow completes or the maximum lifetime ends.
 *
 * Pending targets are a set bounded by the declared targets, and the output is not buffered, so a
 * slow browser makes the server coalesce instead of queue.
 */
internal fun liveEvents(
    subscription: LiveResult.Subscription,
    resumeAfter: Long?,
    limits: LiveLimits,
): Flow<ByteArray> =
    channelFlow {
        val declared = subscription.targets
        val pending = LinkedHashSet<RegionTargetId>()
        val wake = Channel<Unit>(Channel.CONFLATED)
        var nextId = (resumeAfter ?: 0L) + 1
        send("retry: ${limits.retry.inWholeMilliseconds}\n\n".encodeToByteArray())
        if (resumeAfter != null) send(event(nextId++, "resync", declared))
        val producer =
            launch {
                subscription.invalidations.collect { target ->
                    require(
                        target in declared,
                    ) { "Live invalidation targets a region the subscription did not declare" }
                    synchronized(pending) { pending += target }
                    wake.trySend(Unit)
                }
                wake.close()
            }
        withTimeoutOrNull(limits.maxLifetime) {
            var open = true
            while (open) {
                val signal = withTimeoutOrNull(limits.heartbeat) { wake.receiveCatching() }
                if (signal == null) {
                    send(HEARTBEAT)
                } else {
                    val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
                    if (batch.isNotEmpty()) send(event(nextId++, "invalidate", batch))
                    open = !signal.isClosed
                }
            }
        }
        producer.cancel()
    }.buffer(Channel.RENDEZVOUS)

private fun event(
    id: Long,
    name: String,
    targets: Collection<RegionTargetId>,
): ByteArray =
    buildString {
        append("id: ").append(id).append('\n')
        append("event: ").append(name).append('\n')
        targets.forEach { append("data: ").append(it.value).append('\n') }
        append('\n')
    }.encodeToByteArray()

private val HEARTBEAT = ": heartbeat\n\n".encodeToByteArray()
private const val MAX_RESUME_ID = Long.MAX_VALUE / 2
