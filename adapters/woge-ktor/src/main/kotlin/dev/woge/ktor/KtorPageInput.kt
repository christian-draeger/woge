package dev.woge.ktor

import io.ktor.server.application.ApplicationCall

/** Decodes route-specific page input at the Ktor adapter boundary. */
public fun interface KtorPageInput<Input : Any> {
    public suspend fun decode(call: ApplicationCall): Input
}
