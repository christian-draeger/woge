package dev.woge.spring.webflux

import dev.woge.host.RequestContext
import dev.woge.host.WebSocketLimits
import dev.woge.host.WebSocketPeer
import dev.woge.host.WebSocketUseCase
import dev.woge.host.serveWebSocket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession

internal suspend fun serveReactiveSocket(
    session: WebSocketSession,
    context: RequestContext,
    useCase: WebSocketUseCase,
    limits: WebSocketLimits,
): Unit =
    coroutineScope {
        val peer = ReactiveSocketPeer(session, limits)
        val receiver =
            launch {
                try {
                    // Copy inside onNext, before Netty releases its reference-counted payload.
                    session
                        .receive()
                        .map { message ->
                            require(message.type == WebSocketMessage.Type.TEXT)
                            message.payloadAsText
                        }.asFlow()
                        .buffer(0)
                        .collect { peer.commands.send(it) }
                } catch (invalid: IllegalArgumentException) {
                    peer.commands.close(invalid)
                } finally {
                    peer.commands.close()
                }
            }
        val sender = launch { session.send(peer.outgoing.receiveAsFlow().asFlux()).awaitSingleOrNull() }
        try {
            serveWebSocket(peer, context, useCase, limits)
        } finally {
            peer.outgoing.close()
            receiver.cancel()
            sender.cancel()
        }
    }

private class ReactiveSocketPeer(
    private val session: WebSocketSession,
    limits: WebSocketLimits,
) : WebSocketPeer {
    val outgoing = Channel<WebSocketMessage>(Channel.RENDEZVOUS)
    val commands = Channel<String>(limits.messagesPerSecond)
    override val incoming: Flow<String> = commands.receiveAsFlow()

    override suspend fun binary(bytes: ByteArray) {
        outgoing.send(session.binaryMessage { it.wrap(bytes) })
    }

    override suspend fun text(value: String) {
        outgoing.send(session.textMessage(value))
    }

    override suspend fun close(
        code: Int,
        reason: String,
    ) {
        session.close(CloseStatus(code, reason)).awaitSingleOrNull()
    }
}
