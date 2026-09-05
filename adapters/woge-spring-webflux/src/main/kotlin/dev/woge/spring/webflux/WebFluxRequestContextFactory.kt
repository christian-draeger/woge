package dev.woge.spring.webflux

import dev.woge.host.CorrelationId
import dev.woge.host.HttpHeader
import dev.woge.host.LanguageTag
import dev.woge.host.RequestContext
import dev.woge.host.RequestCookies
import dev.woge.host.RequestHeaders
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestTrace
import dev.woge.host.httpHeader
import dev.woge.host.requestCookie
import org.springframework.web.reactive.function.server.ServerRequest
import java.util.Locale
import java.util.UUID

/** Maps adapter-owned WebFlux request state to an immutable Woge request snapshot. */
public fun interface WebFluxRequestContextFactory {
    public fun create(request: ServerRequest): RequestContext
}

/**
 * Safe default context mapping for GET, HEAD and OPTIONS page endpoints.
 *
 * It creates request-local trace IDs, copies non-sensitive headers and parsed cookies, and marks the
 * caller anonymous. Applications using authentication or unsafe methods must install a factory that
 * translates their Spring Security and CSRF decisions explicitly.
 */
public object DefaultWebFluxRequestContextFactory : WebFluxRequestContextFactory {
    override fun create(request: ServerRequest): RequestContext {
        val method = RequestMethod.of(request.method().name())
        require(method in SAFE_METHODS) {
            "The default WebFlux context supports safe page methods only; install an explicit security context factory"
        }
        val traceValue = UUID.randomUUID().toString()
        val headers =
            buildList<HttpHeader> {
                request.headers().asHttpHeaders().forEach { name, values ->
                    if (name.lowercase(Locale.ROOT) !in SENSITIVE_REQUEST_HEADERS) {
                        values.forEach { value -> add(httpHeader(name, value)) }
                    }
                }
            }
        val cookies =
            request.cookies().values.flatten().map { cookie ->
                requestCookie(cookie.name, cookie.value)
            }
        val language =
            request
                .headers()
                .acceptLanguage()
                .firstOrNull()
                ?.range
                .orEmpty()

        return RequestContext(
            method = method,
            trace = RequestTrace(RequestId.of(traceValue), CorrelationId.of(traceValue)),
            language =
                if (language.isEmpty() || language == "*") {
                    LanguageTag.UNDETERMINED
                } else {
                    LanguageTag.of(language)
                },
            headers = RequestHeaders.of(headers),
            cookies = RequestCookies.of(cookies),
        )
    }
}

private val SAFE_METHODS: Set<RequestMethod> = setOf(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS)
private val SENSITIVE_REQUEST_HEADERS: Set<String> =
    setOf(
        "authorization",
        "cookie",
        "proxy-authorization",
        "x-csrf-token",
        "x-xsrf-token",
    )
