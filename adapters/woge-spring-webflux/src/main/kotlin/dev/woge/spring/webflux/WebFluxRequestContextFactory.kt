package dev.woge.spring.webflux

import dev.woge.host.CorrelationId
import dev.woge.host.HttpHeader
import dev.woge.host.LanguageTag
import dev.woge.host.RequestContext
import dev.woge.host.RequestCookies
import dev.woge.host.RequestHeaders
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.host.httpHeader
import dev.woge.host.requestCookie
import dev.woge.host.sameOriginCsrf
import org.springframework.web.reactive.function.server.ServerRequest
import java.util.Locale
import java.util.UUID

/** Maps WebFlux request state, awaiting reactive identity without blocking an event-loop thread. */
public fun interface WebFluxRequestContextFactory {
    public suspend fun create(request: ServerRequest): RequestContext
}

/**
 * Safe default context mapping for GET, HEAD and OPTIONS page endpoints.
 *
 * It creates request-local trace IDs, copies non-sensitive headers and parsed cookies, and marks the
 * caller anonymous. Applications using authentication or token-based CSRF must install their own
 * factory; action bindings default to the corresponding same-origin factory.
 */
public object DefaultWebFluxRequestContextFactory : WebFluxRequestContextFactory {
    override suspend fun create(request: ServerRequest): RequestContext {
        val method = RequestMethod.of(request.method().name())
        require(method in SAFE_METHODS) {
            "The default WebFlux context supports safe page methods only; install an explicit security context factory"
        }
        return mapWebFluxRequestContext(request, RequestSecurity())
    }
}

/** Maps browser requests using the built-in same-origin CSRF check for unsafe methods. */
public object SameOriginWebFluxRequestContextFactory : WebFluxRequestContextFactory {
    override suspend fun create(request: ServerRequest): RequestContext {
        val method = RequestMethod.of(request.method().name())
        val security =
            RequestSecurity(
                csrf =
                    sameOriginCsrf(
                        method,
                        request.headers().header("Origin").joinRequestHeaderValues(),
                        request.headers().header("Sec-Fetch-Site").joinRequestHeaderValues(),
                        request.uri().let { "${it.scheme}://${it.rawAuthority}" },
                    ),
            )
        return mapWebFluxRequestContext(request, security)
    }
}

internal suspend fun mapWebFluxRequestContext(
    request: ServerRequest,
    security: RequestSecurity,
): RequestContext {
    val method = RequestMethod.of(request.method().name())
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
        security = security,
    )
}

private fun List<String>.joinRequestHeaderValues(): String? = takeIf { it.isNotEmpty() }?.joinToString(",")

private val SAFE_METHODS: Set<RequestMethod> = setOf(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS)
private val SENSITIVE_REQUEST_HEADERS: Set<String> =
    setOf(
        "authorization",
        "cookie",
        "proxy-authorization",
        "x-csrf-token",
        "x-xsrf-token",
    )
