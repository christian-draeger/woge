package dev.woge.ktor

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
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import java.util.Locale
import java.util.UUID

/** Maps adapter-owned Ktor request state to an immutable Woge request snapshot. */
public fun interface KtorRequestContextFactory {
    public fun create(call: ApplicationCall): RequestContext
}

/**
 * Safe default context mapping for GET, HEAD and OPTIONS page endpoints.
 *
 * It creates request-local trace IDs, copies non-sensitive headers and parsed cookies, and marks the
 * caller anonymous. Applications using authentication or unsafe methods must install a factory that
 * translates their Ktor authentication and CSRF decisions explicitly.
 */
public object DefaultKtorRequestContextFactory : KtorRequestContextFactory {
    override fun create(call: ApplicationCall): RequestContext {
        val method = RequestMethod.of(call.request.httpMethod.value)
        require(method in SAFE_METHODS) {
            "The default Ktor context supports safe page methods only; install an explicit security context factory"
        }
        val traceValue = UUID.randomUUID().toString()
        val headers =
            buildList<HttpHeader> {
                call.request.headers.forEach { name, values ->
                    if (name.lowercase(Locale.ROOT) !in SENSITIVE_REQUEST_HEADERS) {
                        values.forEach { value -> add(httpHeader(name, value)) }
                    }
                }
            }
        val cookies =
            call.request.cookies.rawCookies
                .keys
                .mapNotNull { name -> call.request.cookies[name]?.let { value -> requestCookie(name, value) } }
        val language = preferredLanguage(call.request.headers[HttpHeaders.AcceptLanguage])

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

private fun preferredLanguage(header: String?): String =
    header
        .orEmpty()
        .split(',')
        .asSequence()
        .map { candidate ->
            val parts = candidate.trim().split(';')
            val quality =
                parts
                    .drop(1)
                    .firstOrNull { it.trim().startsWith("q=", ignoreCase = true) }
                    ?.substringAfter('=')
                    ?.toDoubleOrNull()
                    ?: 1.0
            parts.first().trim() to quality
        }.filter { (tag, quality) -> tag.isNotEmpty() && quality > 0.0 }
        .maxByOrNull { (_, quality) -> quality }
        ?.first
        ?.lowercase(Locale.ROOT)
        .orEmpty()

private val SAFE_METHODS: Set<RequestMethod> = setOf(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS)
private val SENSITIVE_REQUEST_HEADERS: Set<String> =
    setOf(
        "authorization",
        "cookie",
        "proxy-authorization",
        "x-csrf-token",
        "x-xsrf-token",
    )
