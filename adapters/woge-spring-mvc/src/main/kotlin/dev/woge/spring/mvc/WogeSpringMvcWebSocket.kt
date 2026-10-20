package dev.woge.spring.mvc

import dev.woge.host.RequestContext
import dev.woge.host.WEB_SOCKET_PROTOCOL
import dev.woge.host.WebSocketLimits
import dev.woge.host.WebSocketPeer
import dev.woge.host.WebSocketUpgradePolicy
import dev.woge.host.WebSocketUseCase
import dev.woge.host.serveWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.SubProtocolCapable
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.AbstractWebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/** Register as both handler and handshake interceptor in WebSocketConfigurer. No endpoint is auto-installed. */
public class WogeSpringMvcWebSocket(
    private val policy: WebSocketUpgradePolicy,
    private val useCase: WebSocketUseCase,
    private val contextFactory: SpringMvcRequestContextFactory = DefaultSpringMvcRequestContextFactory,
    private val limits: WebSocketLimits = WebSocketLimits(),
) : AbstractWebSocketHandler(),
    HandshakeInterceptor,
    SubProtocolCapable {
    private val connections = ConcurrentHashMap<String, ServletSocketPeer>()

    override fun getSubProtocols(): List<String> = listOf(WEB_SOCKET_PROTOCOL)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        require(request is ServletServerHttpRequest)
        val context = contextFactory.create(request.servletRequest)
        val protocols =
            request.headers["Sec-WebSocket-Protocol"]
                .orEmpty()
                .flatMap { it.split(',') }
                .map { it.trim() }
        val accepted = policy.accepts(context, request.headers["Origin"]?.singleOrNull(), protocols)
        if (accepted) attributes[SOCKET_CONTEXT] = context
        return accepted
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ): Unit = Unit

    override fun afterConnectionEstablished(session: WebSocketSession) {
        session.textMessageSizeLimit = limits.inboundBytes
        session.binaryMessageSizeLimit = limits.inboundBytes
        val peer = ServletSocketPeer(session, limits)
        connections[session.id] = peer
        peer.scope.launch {
            try {
                serveWebSocket(peer, session.attributes[SOCKET_CONTEXT] as RequestContext, useCase, limits)
            } finally {
                if (session.isOpen) peer.close(CloseStatus.NORMAL.code, "complete")
                connections.remove(session.id)
                peer.scope.cancel()
            }
        }
    }

    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        if (connections[session.id]?.commands?.trySend(message.payload)?.isSuccess != true) {
            session.close(CloseStatus(CloseStatus.POLICY_VIOLATION.code, "inbound_backpressure"))
        }
    }

    override fun handleBinaryMessage(
        session: WebSocketSession,
        message: BinaryMessage,
    ) {
        session.close(CloseStatus(CloseStatus.POLICY_VIOLATION.code, "text_commands_required"))
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        connections.remove(session.id)?.let { peer ->
            peer.commands.close()
            peer.scope.cancel()
        }
    }

    override fun handleTransportError(
        session: WebSocketSession,
        exception: Throwable,
    ) {
        session.close(CloseStatus.SERVER_ERROR)
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR)
    }
}

private const val SOCKET_CONTEXT = "woge.socket.context"

private class ServletSocketPeer(
    private val session: WebSocketSession,
    private val limits: WebSocketLimits,
) : WebSocketPeer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val commands = Channel<String>(limits.messagesPerSecond)
    override val incoming: Flow<String> = commands.receiveAsFlow()

    override suspend fun binary(bytes: ByteArray): Unit =
        completingWrite(limits.idleTimeout) {
            session.sendMessage(BinaryMessage(bytes))
        }

    override suspend fun text(value: String): Unit =
        completingWrite(limits.heartbeat) {
            session.sendMessage(TextMessage(value))
        }

    override suspend fun close(
        code: Int,
        reason: String,
    ): Unit =
        completingWrite(limits.idleTimeout) {
            session.close(CloseStatus(code, reason))
        }

    private suspend fun completingWrite(
        timeout: Duration,
        write: () -> Unit,
    ): Unit =
        withContext(NonCancellable) {
            withTimeout(timeout) {
                // Finish an in-flight frame before connection cleanup; interruption can corrupt the transport.
                runInterruptible(Dispatchers.IO, write)
            }
        }
}
