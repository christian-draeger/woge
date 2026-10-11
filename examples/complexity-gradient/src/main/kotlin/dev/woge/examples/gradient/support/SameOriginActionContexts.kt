package dev.woge.examples.gradient.support

import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.spring.webflux.WebFluxRequestContextFactory
import org.springframework.web.reactive.function.server.ServerRequest
import java.util.UUID

/**
 * The application-wide security policy for native and enhanced form posts, written once per host.
 *
 * It is not part of any task's metrics: every application with a POST needs it exactly once. A request
 * is verified only when its `Origin` header names this server; anything else is rejected with 403
 * before the form is decoded.
 */
public object SameOriginActionContexts : WebFluxRequestContextFactory {
    override suspend fun create(request: ServerRequest): RequestContext {
        val uri = request.uri()
        val sameOrigin = request.headers().header("Origin") == listOf("${uri.scheme}://${uri.rawAuthority}")
        val id = UUID.randomUUID().toString()
        return RequestContext(
            RequestMethod.POST,
            RequestTrace(RequestId.of(id), CorrelationId.of(id)),
            security =
                RequestSecurity(
                    csrf = if (sameOrigin) CsrfVerification.VERIFIED else CsrfVerification.NOT_REQUIRED,
                ),
        )
    }
}
