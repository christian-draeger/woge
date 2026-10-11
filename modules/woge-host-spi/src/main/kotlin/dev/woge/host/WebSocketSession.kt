package dev.woge.host

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Runs one bounded connection; completion, disconnect or policy failure cancels every subscription. */
public suspend fun serveWebSocket(
    peer: WebSocketPeer,
    context: RequestContext,
    useCase: WebSocketUseCase,
    limits: WebSocketLimits = WebSocketLimits(),
    clockMillis: () -> Long = { System.nanoTime() / SOCKET_NANOS_PER_MILLI },
) {
    val failure =
        runCatching {
            withTimeout(limits.maxLifetime) { SocketSession(peer, context, useCase, limits, clockMillis).run() }
        }.exceptionOrNull()
    when (failure) {
        null -> Unit
        is SocketClosed -> peer.close(failure.code, failure.reason)
        is IllegalArgumentException -> peer.close(SOCKET_POLICY, "invalid_message_or_revision")
        is TimeoutCancellationException -> peer.close(SOCKET_GOING_AWAY, "connection_timeout")
        is CancellationException -> throw failure
        else -> if (failure is Exception) peer.close(SOCKET_FAILURE, "subscription_failed") else throw failure
    }
}

private class SocketSession(
    val peer: WebSocketPeer,
    val context: RequestContext,
    val useCase: WebSocketUseCase,
    val limits: WebSocketLimits,
    val clock: () -> Long,
) {
    private val sending = Mutex()
    private val outgoing = Channel<SocketFrame>(limits.queuedFrames)
    private val requests = ConcurrentHashMap<String, WebSocketSubscription>()
    private val jobs = mutableMapOf<String, Job>()
    private val lastPong = AtomicLong(clock())
    private var window = clock()
    private var messages = 0

    suspend fun run(): Unit =
        coroutineScope {
            val writer =
                launch {
                    for (frame in outgoing) {
                        if (requests[frame.request.id] === frame.request) {
                            withTimeout(limits.idleTimeout) { sending.withLock { peer.binary(frame.bytes) } }
                        }
                    }
                }
            val heartbeat =
                launch {
                    while (true) {
                        delay(limits.heartbeat)
                        if (clock() - lastPong.get() >=
                            limits.idleTimeout.inWholeMilliseconds
                        ) {
                            throw SocketClosed(SOCKET_GOING_AWAY, "idle_timeout")
                        }
                        withTimeout(limits.heartbeat) { sending.withLock { peer.text("{\"op\":\"ping\"}") } }
                    }
                }
            try {
                peer.incoming.collect { text ->
                    rateLimit()
                    val command = socketCommand(text, limits)
                    when (command.op) {
                        "pong" -> lastPong.set(clock())
                        "subscribe" -> subscribe(command)
                        "unsubscribe" -> {
                            jobs.remove(command.id)?.cancelAndJoin()
                            requests.remove(command.id)
                        }
                        "message" ->
                            useCase.message(
                                requests[command.id] ?: throw SocketClosed(SOCKET_POLICY, "unknown_subscription"),
                                command.payload,
                                context,
                            )
                    }
                }
            } finally {
                jobs.values.forEach { it.cancel() }
                writer.cancel()
                heartbeat.cancel()
                outgoing.close()
            }
        }

    private suspend fun CoroutineScope.subscribe(command: SocketCommand) {
        if (requests.size >= limits.subscriptions) throw SocketClosed(SOCKET_OVERLOAD, "subscription_limit")
        require(!requests.containsKey(command.id)) { "Duplicate subscription" }
        val request = command.subscription()
        val patches = useCase.subscribe(request, context) ?: throw SocketClosed(SOCKET_POLICY, "subscription_refused")
        requests[command.id] = request
        jobs[command.id] =
            launch {
                val revisions = request.revisions.toMutableMap()
                patches.collect { patch ->
                    val frame = socketPatchFrame(request, revisions, patch, limits)
                    enqueue(SocketFrame(request, frame))
                }
            }
    }

    private fun enqueue(frame: SocketFrame) {
        if (!outgoing.trySend(frame).isSuccess) throw SocketClosed(SOCKET_OVERLOAD, "slow_consumer")
    }

    private fun rateLimit() {
        if (clock() - window >= SOCKET_MILLIS_PER_SECOND) {
            window = clock()
            messages = 0
        }
        if (++messages > limits.messagesPerSecond) throw SocketClosed(SOCKET_POLICY, "rate_limit")
    }
}

private class SocketFrame(
    val request: WebSocketSubscription,
    val bytes: ByteArray,
)
