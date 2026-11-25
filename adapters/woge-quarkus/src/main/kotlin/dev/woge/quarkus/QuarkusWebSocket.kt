package dev.woge.quarkus

import dev.woge.host.ResponseStatus
import dev.woge.host.WEB_SOCKET_PROTOCOL
import dev.woge.host.WebSocketLimits
import dev.woge.host.WebSocketPeer
import dev.woge.host.WebSocketUpgradePolicy
import dev.woge.host.WebSocketUseCase
import dev.woge.host.serveWebSocket
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Binds an optional WebSocket route. Configure the host to negotiate woge.patch.v1 and to bound
 * transport message sizes before enabling the binding.
 */
public fun wogeQuarkusWebSocket(
    policy: WebSocketUpgradePolicy,
    useCase: WebSocketUseCase,
    contexts: QuarkusRequestContextFactory,
    limits: WebSocketLimits = WebSocketLimits(),
): Handler<RoutingContext> =
    Handler { call ->
        val headers = call.request().headers()
        val protocols =
            headers.getAll("Sec-WebSocket-Protocol").flatMap { values ->
                values.split(',').map(String::trim)
            }
        val context = contexts.create(call)
        if (policy.accepts(context, headers.getAll("Origin").singleOrNull(), protocols)) {
            val dispatcher = checkNotNull(Vertx.currentContext()).dispatcher()
            CoroutineScope(dispatcher).launch {
                val socket = call.request().toWebSocket().coAwait()
                if (socket.subProtocol() != WEB_SOCKET_PROTOCOL) {
                    socket.close(UNSUPPORTED_SUBPROTOCOL, "unsupported_subprotocol").coAwait()
                } else {
                    val peer = QuarkusSocketPeer(socket, dispatcher, limits.queuedFrames)
                    withContext(Dispatchers.Default) { serveWebSocket(peer, context, useCase, limits) }
                }
            }
        } else {
            call
                .response()
                .setStatusCode(ResponseStatus.FORBIDDEN.code)
                .putHeader("Cache-Control", "no-store")
                .end()
        }
    }

private class QuarkusSocketPeer(
    private val socket: ServerWebSocket,
    private val dispatcher: CoroutineDispatcher,
    capacity: Int,
) : WebSocketPeer {
    private val messages = Channel<String>(capacity)

    override val incoming: Flow<String> = messages.receiveAsFlow()

    init {
        socket.exceptionHandler { messages.close(it) }
        socket.closeHandler { messages.close() }
        socket.binaryMessageHandler { messages.close(IllegalArgumentException("Only text commands are supported")) }
        socket.textMessageHandler { message ->
            if (messages.trySend(message).isFailure) {
                messages.close(IllegalArgumentException("Too many pending messages"))
            }
        }
    }

    override suspend fun binary(bytes: ByteArray) {
        withContext(dispatcher) { socket.writeBinaryMessage(Buffer.buffer(bytes)).coAwait() }
    }

    override suspend fun text(value: String) {
        withContext(dispatcher) { socket.writeTextMessage(value).coAwait() }
    }

    override suspend fun close(
        code: Int,
        reason: String,
    ) {
        withContext(dispatcher) {
            if (!socket.isClosed) socket.close(code.toShort(), reason).coAwait()
        }
    }
}

private const val UNSUPPORTED_SUBPROTOCOL: Short = 1002
