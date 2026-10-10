package dev.woge.tck

import dev.woge.host.RequestMethod
import java.net.http.HttpResponse

/**
 * Decides whether a shared cache such as a reverse proxy or CDN may store a response.
 *
 * This follows the storage rules of RFC 9111, section 3. Like most CDNs, it also refuses responses
 * that set cookies. Heuristic freshness lets a shared cache store a plain 200 response without
 * Cache-Control, so only `no-store` or `private` reliably keep one out.
 */
internal fun sharedCacheMayStore(
    method: RequestMethod,
    status: Int,
    requestHeaders: Map<String, String>,
    responseHeaders: Map<String, List<String>>,
): Boolean {
    val directives = cacheDirectives(responseHeaders)
    val authorized = requestHeaders.keys.any { it.equals("Authorization", ignoreCase = true) }
    return (method == RequestMethod.GET || method == RequestMethod.HEAD) &&
        status in STORABLE_STATUS_CODES &&
        "no-store" !in directives &&
        "private" !in directives &&
        responseHeaders.keys.none { it.equals("Set-Cookie", ignoreCase = true) } &&
        (!authorized || directives.any { it in AUTHORIZED_STORAGE_DIRECTIVES })
}

private fun cacheDirectives(headers: Map<String, List<String>>): Set<String> =
    headers
        .filterKeys { it.equals("Cache-Control", ignoreCase = true) }
        .values
        .flatten()
        .flatMap { it.split(',') }
        .map { it.substringBefore('=').trim().lowercase() }
        .toSet()

// Status codes RFC 9110 marks as heuristically cacheable, plus 304 for updating stored responses.
@Suppress("MagicNumber")
private val STORABLE_STATUS_CODES = setOf(200, 203, 204, 206, 300, 301, 304, 308, 404, 405, 410, 414, 501)
private val AUTHORIZED_STORAGE_DIRECTIVES = setOf("public", "s-maxage", "must-revalidate")

/**
 * Sends every safe-method Woge response through the shared-cache rules; none may be stored by default.
 * Unsafe methods such as POST are never storable, so action results need no probe.
 */
internal fun AdapterTckHttpClient.verifySharedCaching(expect: (Boolean, String, String) -> Unit) {
    val contract = "shared-cache-safety"
    val credentials = mapOf("Authorization" to "Bearer tck")
    val requests =
        AdapterTckPageScenario.entries.flatMap { scenario ->
            listOf(
                SharedCacheProbe(RequestMethod.GET, AdapterTckRoutes.page(scenario)),
                SharedCacheProbe(RequestMethod.HEAD, AdapterTckRoutes.page(scenario), credentials),
            )
        } +
            listOf(
                SharedCacheProbe(
                    RequestMethod.GET,
                    AdapterTckRoutes.page(AdapterTckPageScenario.CACHEABLE),
                    mapOf("If-None-Match" to "\"tck,one\""),
                ),
                SharedCacheProbe(RequestMethod.GET, AdapterTckRoute.url(AdapterTckRouteInput(item = 1)).value),
                SharedCacheProbe(
                    RequestMethod.GET,
                    AdapterTckRoutes.deferred(AdapterTckDeferredScenario.COMPLETION_ORDER),
                ),
                SharedCacheProbe(RequestMethod.GET, TckSubmitAction.path),
            )
    requests.forEach { probe ->
        val response = open(probe.method, probe.path, probe.headers)
        response.body().close()
        expect(
            !response.mayBeStoredFor(probe),
            contract,
            "${probe.method.value} ${probe.path} (${response.statusCode()}) is storable by a shared cache",
        )
    }
}

private class SharedCacheProbe(
    val method: RequestMethod,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
)

private fun HttpResponse<*>.mayBeStoredFor(probe: SharedCacheProbe): Boolean =
    sharedCacheMayStore(probe.method, statusCode(), probe.headers, headers().map())
