package dev.woge.ktor

import dev.woge.host.PageRoute
import dev.woge.host.RouteParameters
import io.ktor.server.application.ApplicationCall

/** Decodes route-specific page input at the Ktor adapter boundary. */
public fun interface KtorPageInput<Input : Any> {
    public suspend fun decode(call: ApplicationCall): Input
}

/** Reads the typed input of a generated route from the Ktor path parameters and query string. */
public fun <Input : Any> PageRoute<Input>.ktorInput(): KtorPageInput<Input> =
    KtorPageInput { call ->
        decode(
            object : RouteParameters {
                override fun path(name: String): String? = call.parameters[name]

                override fun query(name: String): String? = call.request.queryParameters[name]
            },
        )
    }
