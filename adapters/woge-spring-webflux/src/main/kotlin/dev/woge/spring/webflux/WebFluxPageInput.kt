package dev.woge.spring.webflux

import org.springframework.web.reactive.function.server.ServerRequest

/** Decodes route-specific page input at the WebFlux adapter boundary. */
public fun interface WebFluxPageInput<Input : Any> {
    public suspend fun decode(request: ServerRequest): Input
}
