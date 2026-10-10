package dev.woge.ktor

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond

/** POST-only entry point sharing the page response, cancellation and failure mapping. */
public class WogeKtorActionHandler<Command : Any> internal constructor(
    private val delegate: WogeKtorPageHandler<Command>,
) {
    public suspend fun handle(call: ApplicationCall) {
        if (call.request.httpMethod == HttpMethod.Post) {
            delegate.handle(call)
        } else {
            call.response.headers.append("Allow", "POST")
            call.respond(HttpStatusCode.MethodNotAllowed)
        }
    }
}
