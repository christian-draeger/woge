package dev.woge.spring.webflux

import dev.woge.host.PageRoute
import dev.woge.host.RouteParameters
import org.springframework.web.reactive.function.server.ServerRequest

/** Decodes route-specific page input at the WebFlux adapter boundary. */
public fun interface WebFluxPageInput<Input : Any> {
    public suspend fun decode(request: ServerRequest): Input
}

/** Reads the typed input of a generated route from the WebFlux path variables and query string. */
public fun <Input : Any> PageRoute<Input>.webFluxInput(): WebFluxPageInput<Input> =
    WebFluxPageInput { request ->
        decode(
            object : RouteParameters {
                override fun path(name: String): String? = request.pathVariables()[name]

                override fun query(name: String): String? = request.queryParam(name).orElse(null)
            },
        )
    }
