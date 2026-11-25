package dev.woge.quarkus

import dev.woge.host.AuthenticationFacts
import dev.woge.host.CorrelationId
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
import io.vertx.ext.web.RoutingContext
import java.net.URI
import java.util.Locale
import java.util.UUID

/** Copies host-authenticated facts on the router context before portable work starts. */
public fun interface QuarkusRequestContextFactory {
    public fun create(call: RoutingContext): RequestContext
}

/** Establishes browser same-origin facts using the URI resolved by the managed HTTP server. */
public fun quarkusRequestContext(
    call: RoutingContext,
    authentication: AuthenticationFacts = AuthenticationFacts.Anonymous,
): RequestContext {
    val request = call.request()
    val method = RequestMethod.of(request.method().name())
    val uri = runCatching { URI(request.absoluteURI()) }.getOrNull()
    val resolvedOrigin = if (uri?.host != null) "${uri.scheme}://${uri.rawAuthority}" else ""
    val security =
        RequestSecurity(
            authentication,
            sameOriginCsrf(
                method,
                call.headerValues("Origin"),
                call.headerValues("Sec-Fetch-Site"),
                resolvedOrigin,
            ),
        )
    return quarkusRequestContext(call, security)
}

/** Copies a security policy already verified by the application or host. */
public fun quarkusRequestContext(
    call: RoutingContext,
    security: RequestSecurity,
): RequestContext {
    val request = call.request()
    val trace = UUID.randomUUID().toString()
    val headers =
        request
            .headers()
            .filterNot { it.key.lowercase(Locale.ROOT) in SENSITIVE_HEADERS }
            .map { httpHeader(it.key, it.value) }
    val cookies = request.cookies().mapNotNull { runCatching { requestCookie(it.name, it.value) }.getOrNull() }
    val language =
        call
            .acceptableLanguages()
            .asSequence()
            .filter { it.weight() > 0 }
            .mapNotNull { runCatching { LanguageTag.of(it.value().lowercase(Locale.ROOT)) }.getOrNull() }
            .firstOrNull() ?: LanguageTag.UNDETERMINED
    return RequestContext(
        method = RequestMethod.of(request.method().name()),
        trace = RequestTrace(RequestId.of(trace), CorrelationId.of(trace)),
        language = language,
        headers = RequestHeaders.of(headers),
        cookies = RequestCookies.of(cookies),
        security = security,
    )
}

internal fun RoutingContext.headerValues(name: String): String? =
    request()
        .headers()
        .getAll(name)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(",")

private val SENSITIVE_HEADERS =
    setOf("authorization", "cookie", "proxy-authorization", "x-csrf-token", "x-xsrf-token")
