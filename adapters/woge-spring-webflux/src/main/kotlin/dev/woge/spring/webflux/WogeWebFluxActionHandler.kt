package dev.woge.spring.webflux

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/** POST-only entry point sharing the page response, cancellation and failure mapping. */
public class WogeWebFluxActionHandler<Command : Any> internal constructor(
    private val delegate: WogeWebFluxPageHandler<Command>,
) {
    public suspend fun handle(request: ServerRequest): ServerResponse =
        if (request.method() == HttpMethod.POST) {
            delegate.handle(request)
        } else {
            ServerResponse
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .header("Allow", "POST")
                .build()
                .awaitSingle()
        }
}
