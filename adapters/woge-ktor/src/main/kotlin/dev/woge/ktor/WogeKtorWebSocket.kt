package dev.woge.ktor

import dev.woge.host.WEB_SOCKET_PROTOCOL
import dev.woge.host.WebSocketLimits
import dev.woge.host.WebSocketPeer
import dev.woge.host.WebSocketUpgradePolicy
import dev.woge.host.WebSocketUseCase
import dev.woge.host.serveWebSocket
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

/** Install Ktor WebSockets, then bind this route. Upgrade policy runs before the WebSocket handler upgrades. */
public fun Route.wogeWebSocket(
    path: String,
    policy: WebSocketUpgradePolicy,
    useCase: WebSocketUseCase,
    contextFactory: KtorRequestContextFactory = DefaultKtorRequestContextFactory,
    limits: WebSocketLimits = WebSocketLimits(),
) {
    route(path) {
        install(
            io.ktor.server.application.createRouteScopedPlugin("WogeWebSocketUpgrade") {
                onCall { call ->
                    val context = contextFactory.create(call)
                    val protocols =
                        call.request.headers
                            .getAll("Sec-WebSocket-Protocol")
                            .orEmpty()
                            .flatMap { it.split(',') }
                            .map { it.trim() }
                    if (!policy.accepts(
                            context,
                            call.request.headers
                                .getAll("Origin")
                                ?.singleOrNull(),
                            protocols,
                        )
                    ) {
                        call.respond(HttpStatusCode.Forbidden)
                    } else {
                        call.attributes.put(SOCKET_CONTEXT, context)
                    }
                }
            },
        )
        webSocket(protocol = WEB_SOCKET_PROTOCOL) {
            maxFrameSize = limits.inboundBytes.toLong()
            serveWebSocket(KtorSocketPeer(this), call.attributes[SOCKET_CONTEXT], useCase, limits)
        }
    }
}

private val SOCKET_CONTEXT = io.ktor.util.AttributeKey<dev.woge.host.RequestContext>("woge.socket.context")

private class KtorSocketPeer(
    private val session: DefaultWebSocketSession,
) : WebSocketPeer {
    override val incoming: Flow<String> =
        session.incoming.receiveAsFlow().map { frame ->
            require(frame is Frame.Text) { "Only text commands are supported" }
            frame.readText()
        }

    override suspend fun binary(bytes: ByteArray) {
        session.send(Frame.Binary(true, bytes))
    }

    override suspend fun text(value: String) {
        session.send(Frame.Text(value))
    }

    override suspend fun close(
        code: Int,
        reason: String,
    ) {
        session.close(CloseReason(code.toShort(), reason))
    }
}
