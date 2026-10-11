package dev.woge.host

import java.net.URI

/**
 * Applies a same-origin CSRF policy for browser actions.
 *
 * Safe methods do not need CSRF verification. For other methods, a present non-null Origin must
 * match the request origin; when Origin is absent, `Sec-Fetch-Site: same-origin` is accepted.
 * Otherwise verification is not established, so action handlers reject the request.
 */
public fun sameOriginCsrf(
    method: RequestMethod,
    origin: String?,
    fetchSite: String?,
    requestOrigin: String,
): CsrfVerification {
    val verified =
        when {
            method in SAFE_METHODS -> false
            origin != null && origin != "null" -> sameOrigin(origin, requestOrigin)
            else -> fetchSite.equals("same-origin", ignoreCase = true)
        }
    return if (verified) CsrfVerification.VERIFIED else CsrfVerification.NOT_REQUIRED
}

private fun sameOrigin(
    first: String,
    second: String,
): Boolean =
    parseOrigin(first)?.let { firstUri ->
        parseOrigin(second)?.let { secondUri ->
            firstUri.scheme.equals(secondUri.scheme, ignoreCase = true) &&
                firstUri.host.equals(secondUri.host, ignoreCase = true) &&
                effectivePort(firstUri) == effectivePort(secondUri)
        }
    } ?: false

private fun parseOrigin(value: String): URI? =
    runCatching { URI(value) }.getOrNull()?.takeIf { uri ->
        uri.isAbsolute &&
            uri.rawAuthority != null &&
            uri.rawUserInfo == null &&
            uri.host != null &&
            uri.rawPath.isNullOrEmpty() &&
            uri.rawQuery == null &&
            uri.rawFragment == null
    }

private fun effectivePort(uri: URI): Int =
    when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("http", ignoreCase = true) -> HTTP_DEFAULT_PORT
        uri.scheme.equals("https", ignoreCase = true) -> HTTPS_DEFAULT_PORT
        else -> NO_DEFAULT_PORT
    }

private val SAFE_METHODS: Set<RequestMethod> = setOf(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS)
private const val HTTP_DEFAULT_PORT: Int = 80
private const val HTTPS_DEFAULT_PORT: Int = 443
private const val NO_DEFAULT_PORT: Int = -1
