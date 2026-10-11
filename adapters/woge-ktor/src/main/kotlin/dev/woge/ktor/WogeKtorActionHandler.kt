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
        if (call.request.httpMethod == HttpMethod.Post &&
            dev.woge.host.requestsUnsupportedActionPatchVersion(call.request.headers["Accept"])
        ) {
            call.response.headers.append("Woge-Protocol-Error", "unsupported-version")
            call.response.headers.append("Cache-Control", "no-store")
            call.respond(HttpStatusCode.NotAcceptable)
        } else if (call.request.httpMethod == HttpMethod.Post) {
            delegate.handleAction(call)
        } else {
            call.response.headers.append("Allow", "POST")
            call.response.headers.append("Cache-Control", "no-store")
            call.respond(HttpStatusCode.MethodNotAllowed)
        }
    }
}
