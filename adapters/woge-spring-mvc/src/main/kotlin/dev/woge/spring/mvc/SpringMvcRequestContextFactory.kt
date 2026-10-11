package dev.woge.spring.mvc

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
import jakarta.servlet.http.HttpServletRequest
import java.net.URI
import java.util.Locale
import java.util.UUID

/** Maps adapter-owned Servlet request state to an immutable Woge request snapshot. */
public fun interface SpringMvcRequestContextFactory {
    public fun create(request: HttpServletRequest): RequestContext
}

/**
 * Safe default context mapping for GET, HEAD and OPTIONS page endpoints.
 *
 * It creates request-local trace IDs, copies non-sensitive headers and parsed cookies, and marks the
 * caller anonymous. Applications using authentication or token-based CSRF must install their own
 * factory; action bindings default to the corresponding same-origin factory.
 */
public object DefaultSpringMvcRequestContextFactory : SpringMvcRequestContextFactory {
    override fun create(request: HttpServletRequest): RequestContext {
        val method = RequestMethod.of(request.method)
        require(method in SAFE_METHODS) {
            "The default Spring MVC context supports safe page methods only; " +
                "install an explicit security context factory"
        }
        return mapSpringMvcRequestContext(request, RequestSecurity())
    }
}

/** Maps browser requests using the built-in same-origin CSRF check for unsafe methods. */
public object SameOriginSpringMvcRequestContextFactory : SpringMvcRequestContextFactory {
    override fun create(request: HttpServletRequest): RequestContext {
        val method = RequestMethod.of(request.method)
        val requestUrl = URI(request.requestURL.toString())
        val security =
            RequestSecurity(
                csrf =
                    sameOriginCsrf(
                        method,
                        request.getHeaders("Origin").toList().joinRequestHeaderValues(),
                        request.getHeaders("Sec-Fetch-Site").toList().joinRequestHeaderValues(),
                        "${requestUrl.scheme}://${requestUrl.rawAuthority}",
                    ),
            )
        return mapSpringMvcRequestContext(request, security)
    }
}

internal fun mapSpringMvcRequestContext(
    request: HttpServletRequest,
    security: RequestSecurity,
): RequestContext {
    val method = RequestMethod.of(request.method)
    val traceValue = UUID.randomUUID().toString()
    val headers =
        buildList<HttpHeader> {
            request.headerNames?.asSequence()?.forEach { name ->
                if (name.lowercase(Locale.ROOT) !in SENSITIVE_REQUEST_HEADERS) {
                    request.getHeaders(name).asSequence().forEach { value -> add(httpHeader(name, value)) }
                }
            }
        }
    val cookies = request.cookies.orEmpty().map { cookie -> requestCookie(cookie.name, cookie.value) }
    val language =
        request.locales
            ?.asSequence()
            ?.firstOrNull()
            ?.toLanguageTag()
            ?.lowercase(Locale.ROOT)
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
