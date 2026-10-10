package dev.woge.tck

import dev.woge.host.PageResult
import dev.woge.host.RequestMethod
import dev.woge.host.ResponseHeaders
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.htmlPage
import dev.woge.host.httpHeader

internal const val TCK_MODIFIED_DATE = "Sat, 10 Oct 2026 16:00:00 GMT"

internal fun cacheablePage(render: () -> Unit): PageResult =
    htmlPage(
        ResponseMetadata(
            headers =
                ResponseHeaders.of(
                    httpHeader("Cache-Control", "private, no-cache"),
                    httpHeader("ETag", "\"tck,one\""),
                    httpHeader("Last-Modified", TCK_MODIFIED_DATE),
                    httpHeader("Vary", "Accept-Language"),
                ),
        ),
    ) {
        render()
        text("Cacheable document")
    }

internal fun AdapterTckHttpClient.verifyHttpCaching(
    fixture: AdapterTckFixtureState,
    expect: (Boolean, String, String) -> Unit,
) {
    val contract = "http-caching"
    val path = AdapterTckRoutes.page(AdapterTckPageScenario.CACHEABLE)
    val initial = open(RequestMethod.GET, path)
    initial.body().use { it.readAllBytes() }
    expect(initial.statusCode() == ResponseStatus.OK.code, contract, "initial cacheable page failed")
    expect(initial.header("etag") == "\"tck,one\"", contract, "validator missing")
    val rendered = fixture.cacheRenders.get()
    for (method in listOf(RequestMethod.GET, RequestMethod.HEAD)) {
        for (headers in listOf(
            mapOf("If-None-Match" to "W/\"tck,one\""),
            mapOf("If-None-Match" to "\"other\", \"tck,one\""),
            mapOf("If-None-Match" to "*"),
            mapOf("If-Modified-Since" to TCK_MODIFIED_DATE),
        )) {
            val unchanged = open(method, path, headers)
            unchanged.body().use {
                expect(it.readAllBytes().isEmpty(), contract, "304 emitted a body")
            }
            expect(unchanged.statusCode() == ResponseStatus.NOT_MODIFIED.code, contract, "condition did not return 304")
            expect(unchanged.header("cache-control") == "private, no-cache", contract, "304 lost cache policy")
            expect(unchanged.header("etag") == "\"tck,one\"", contract, "304 lost validator")
            expect(unchanged.header("vary") == "Accept-Language", contract, "304 lost Vary")
            expect(unchanged.header("content-type") == null, contract, "304 carried document body metadata")
        }
    }
    expect(fixture.cacheRenders.get() == rendered, contract, "conditional response collected document frames")
    for (method in listOf(RequestMethod.GET, RequestMethod.HEAD)) {
        val denied = open(method, path, mapOf("If-None-Match" to "\"tck,one\"", "X-Tck-Deny" to "true"))
        denied.body().close()
        expect(
            denied.statusCode() == ResponseStatus.FORBIDDEN.code,
            contract,
            "validator bypassed domain authorization",
        )
        expect(denied.header("cache-control") == "no-store", contract, "authorization failure is cacheable")
    }
    val changed =
        open(
            RequestMethod.GET,
            path,
            mapOf(
                "If-None-Match" to "\"other\"",
                "If-Modified-Since" to TCK_MODIFIED_DATE,
            ),
        )
    changed.body().use { it.readAllBytes() }
    expect(changed.statusCode() == ResponseStatus.OK.code, contract, "date overrode nonmatching entity condition")
    for (scenario in listOf(AdapterTckPageScenario.CONTROLLED_FAILURE, AdapterTckPageScenario.REDIRECT)) {
        val response = open(RequestMethod.GET, AdapterTckRoutes.page(scenario))
        response.body().close()
        expect(response.header("cache-control") == "no-store", contract, "implicit dynamic response is cacheable")
    }
}
