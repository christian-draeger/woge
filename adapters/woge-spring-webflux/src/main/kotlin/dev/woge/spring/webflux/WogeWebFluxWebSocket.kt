package dev.woge.spring.webflux

import dev.woge.host.WEB_SOCKET_PROTOCOL
import dev.woge.host.WebSocketLimits
import dev.woge.host.WebSocketUpgradePolicy
import dev.woge.host.WebSocketUseCase
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.server.WebSocketService
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebHandler
import reactor.core.publisher.Mono

/** Explicit opt-in WebHandler. Map it to a path with SimpleUrlHandlerMapping; retain the security filter chain. */
public class WogeWebFluxWebSocket(
    private val policy: WebSocketUpgradePolicy,
    private val useCase: WebSocketUseCase,
    private val contextFactory: WebFluxRequestContextFactory = DefaultWebFluxRequestContextFactory,
    private val limits: WebSocketLimits = WebSocketLimits(),
    private val service: WebSocketService = HandshakeWebSocketService(),
) : WebHandler {
    override fun handle(exchange: ServerWebExchange): Mono<Void> =
        mono {
            val context = contextFactory.create(ServerRequest.create(exchange, emptyList()))
            val protocols =
                exchange.request.headers["Sec-WebSocket-Protocol"]
                    .orEmpty()
                    .flatMap { it.split(',') }
                    .map { it.trim() }
            if (policy.accepts(context, exchange.request.headers["Origin"]?.singleOrNull(), protocols)) {
                val handler =
                    object : WebSocketHandler {
                        override fun getSubProtocols(): List<String> = listOf(WEB_SOCKET_PROTOCOL)

                        override fun handle(session: WebSocketSession): Mono<Void> =
                            mono { serveReactiveSocket(session, context, useCase, limits) }.then()
                    }
                service.handleRequest(exchange, handler).awaitSingleOrNull()
            } else {
                exchange.response.statusCode = HttpStatus.FORBIDDEN
                exchange.response.setComplete().awaitSingleOrNull()
            }
        }.then()
}
