package dev.woge.spring.webflux

import dev.woge.host.DeferredRegion
import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.FailureCategory
import dev.woge.host.HeaderName
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.RequestMethod
import dev.woge.host.ResponseHeaders
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.deferredRegion
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.httpHeader
import dev.woge.host.redirect
import dev.woge.host.responseCookie
import dev.woge.host.streamingHtmlPage
import dev.woge.html.applicationUrl
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.htmlFrame
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ResponseStatusException
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.seconds

class WogeWebFluxAdapterTest {
    @Test
    fun `one shared application object backs both HTML and patch routes`() =
        runBlocking {
            val application = SharedProjectPage()
            val input = WebFluxPageInput<String> { it.pathVariable("project") }
            val pageHandler = WogeWebFluxPageHandler(application, input)
            val deferredHandler = WogeWebFluxDeferredHandler(application, input)
            val routes =
                coRouter {
                    GET("/projects/{project}", pageHandler::handle)
                    GET("/projects/{project}/patches", deferredHandler::handle)
                }

            TestWebFluxServer(routes).use { server ->
                assertEquals("<h1>Project woge</h1>", server.text("/projects/woge").body())

                val stream = server.open("/projects/woge/patches").body().readAllBytes()
                val events = PatchStreamV1.decoder().let { decoder -> decoder.feed(stream).also { decoder.finish() } }
                val patch = events.filterIsInstance<PatchStreamEvent.PatchFrame>().single().patch
                assertEquals("summary", patch.target.region.value)
            }
        }

    @Test
    fun `page handler flushes frames and maps request plus response metadata on a real server`() =
        runBlocking {
            val releaseSecondFrame = CompletableDeferred<Unit>()
            val capturedRequest = CompletableDeferred<PageRequest<String>>()
            val page =
                PageUseCase<String> { request ->
                    capturedRequest.complete(request)
                    streamingHtmlPage(
                        frames =
                            flow {
                                emit(htmlFrame { element("main") { text("Shell for ${request.input}") } })
                                releaseSecondFrame.await()
                                emit(htmlFrame { element("footer") { text("Deferred document frame") } })
                            },
                        metadata =
                            ResponseMetadata(
                                status = ResponseStatus.of(202),
                                headers = ResponseHeaders.of(httpHeader("x-woge-test", "mapped")),
                                cookies = listOf(responseCookie("woge-session", "safe")),
                            ),
                    )
                }
            val handler = WogeWebFluxPageHandler(page, WebFluxPageInput { it.pathVariable("project") })

            TestWebFluxServer(coRouter { GET("/projects/{project}", handler::handle) }).use { server ->
                val response =
                    server.open(
                        "/projects/woge",
                        mapOf(
                            "Accept-Language" to "de-DE,de;q=0.8",
                            "Authorization" to "Bearer not-portable",
                            "Cookie" to "theme=dark",
                            "X-Visible" to "yes",
                        ),
                    )
                assertPageMetadata(response)

                val first = response.body().readThrough("</main>".toByteArray())
                assertEquals("<main>Shell for woge</main>", first.toString(StandardCharsets.UTF_8))
                assertFalse(releaseSecondFrame.isCompleted)

                assertMappedContext(capturedRequest.await().context)

                releaseSecondFrame.complete(Unit)
                val rest = response.body().readAllBytes().toString(StandardCharsets.UTF_8)
                assertEquals("<footer>Deferred document frame</footer>", rest)
            }
        }

    @Test
    fun `redirect controlled failure and pre-stream host error preserve status contracts`() =
        runBlocking {
            val input = WebFluxPageInput<Unit> { _ -> }
            val redirectHandler =
                WogeWebFluxPageHandler(
                    PageUseCase<Unit> { redirect(applicationUrl("/target")) },
                    input,
                )
            val failureHandler =
                WogeWebFluxPageHandler(
                    PageUseCase<Unit> { request -> failure(FailureCategory.NOT_FOUND, request.context.correlationId) },
                    input,
                )
            val errorHandler =
                WogeWebFluxPageHandler(
                    PageUseCase<Unit> { throw ResponseStatusException(HttpStatus.BAD_GATEWAY) },
                    input,
                )
            val routes =
                coRouter {
                    GET("/redirect", redirectHandler::handle)
                    GET("/missing", failureHandler::handle)
                    GET("/error", errorHandler::handle)
                }

            TestWebFluxServer(routes).use { server ->
                val redirect = server.text("/redirect")
                assertEquals(303, redirect.statusCode())
                assertEquals("/target", redirect.headers().firstValue("location").orElseThrow())
                assertEquals("", redirect.body())

                val missing = server.text("/missing")
                assertEquals(404, missing.statusCode())
                assertEquals("", missing.body())
                assertTrue(missing.headers().firstValue("content-type").isEmpty)

                val error = server.text("/error")
                assertEquals(502, error.statusCode())
            }
        }

    @Test
    fun `deferred handler flushes a fast patch while a slow sibling is still pending`() =
        runBlocking {
            val slow = CompletableDeferred<dev.woge.protocol.PatchHtml>()
            val regions =
                DeferredRegionsUseCase<String> { request ->
                    listOf(
                        region("slow") { slow.await() },
                        region("fast") { patch("Fast result for ${request.input}") },
                    )
                }
            val handler =
                WogeWebFluxDeferredHandler(
                    regions = regions,
                    input = WebFluxPageInput { it.pathVariable("project") },
                )

            TestWebFluxServer(coRouter { GET("/projects/{project}/patches", handler::handle) }).use { server ->
                val response = server.open("/projects/woge/patches")
                assertEquals(200, response.statusCode())
                assertTrue(
                    response
                        .headers()
                        .firstValue("content-type")
                        .orElseThrow()
                        .startsWith("application/vnd.woge.patch-stream"),
                )
                assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow())

                val first = response.body().readThrough("Fast result for woge".toByteArray())
                assertFalse(first.toString(StandardCharsets.UTF_8).contains("Slow result"))
                assertFalse(slow.isCompleted)

                slow.complete(patch("Slow result"))
                val all = first + response.body().readAllBytes()
                val decoder = PatchStreamV1.decoder()
                val events = decoder.feed(all)
                decoder.finish()
                assertEquals(
                    listOf("fast", "slow"),
                    events.filterIsInstance<PatchStreamEvent.PatchFrame>().map { it.patch.target.region.value },
                )
                assertEquals(PatchStreamEvent.Complete(2), events.last())
            }
        }

    @Test
    fun `closing a committed response cancels outstanding deferred work`() =
        runBlocking {
            val cancelled = CompletableDeferred<Unit>()
            val regions =
                DeferredRegionsUseCase<Unit> {
                    listOf(
                        region("waiting") {
                            try {
                                awaitCancellation()
                            } finally {
                                cancelled.complete(Unit)
                            }
                        },
                        region("ready") { patch("Ready") },
                    )
                }
            val handler = WogeWebFluxDeferredHandler(regions, WebFluxPageInput { _ -> })

            TestWebFluxServer(coRouter { GET("/patches", handler::handle) }).use { server ->
                val response = server.open("/patches")
                response.body().readThrough("Ready".toByteArray())
                response.body().close()

                withTimeout(5.seconds) { cancelled.await() }
            }
        }
}

private fun assertPageMetadata(response: HttpResponse<InputStream>) {
    assertEquals(202, response.statusCode())
    assertEquals("mapped", response.headers().firstValue("x-woge-test").orElseThrow())
    val contentType =
        response
            .headers()
            .firstValue("content-type")
            .orElseThrow()
            .lowercase()
    assertTrue(contentType.startsWith("text/html"))
    assertTrue(contentType.contains("charset=utf-8"))
    assertTrue(
        response
            .headers()
            .firstValue("set-cookie")
            .orElseThrow()
            .contains("woge-session=safe"),
    )
}

private class SharedProjectPage :
    PageUseCase<String>,
    DeferredRegionsUseCase<String> {
    override suspend fun open(request: PageRequest<String>): PageResult =
        htmlPage { element("h1") { text("Project ${request.input}") } }

    override suspend fun regions(request: PageRequest<String>): Iterable<DeferredRegion> =
        listOf(region("summary") { patch("Summary for ${request.input}") })
}

private fun assertMappedContext(context: RequestContext) {
    assertEquals(RequestMethod.GET, context.method)
    assertEquals("de-de", context.language.value)
    assertEquals("yes", context.header("x-visible"))
    assertNull(context.header("authorization"))
    assertNull(context.header("cookie"))
    assertEquals("dark", context.cookie("theme"))
}

private fun RequestContext.header(name: String): String? = headers.values(HeaderName.of(name)).firstOrNull()?.value

private fun RequestContext.cookie(name: String): String? =
    cookies
        .first(
            dev.woge.host.CookieName
                .of(name),
        )?.value

private fun region(
    id: String,
    content: suspend () -> dev.woge.protocol.PatchHtml,
): DeferredRegion =
    deferredRegion(
        target = PatchTarget(PageEpoch.of("page-1"), RegionTargetId.of(id)),
        loading = { element("p") { text("Loading $id") } },
        onFailure = { patchHtml { element("p") { text("Unavailable") } } },
        content = content,
    )

private fun patch(text: String): dev.woge.protocol.PatchHtml = patchHtml { element("p") { text(text) } }

private class TestWebFluxServer(
    routes: RouterFunction<ServerResponse>,
) : AutoCloseable {
    private val server: DisposableServer =
        HttpServer
            .create()
            .host("127.0.0.1")
            .port(0)
            .handle(ReactorHttpHandlerAdapter(RouterFunctions.toHttpHandler(routes)))
            .bindNow()
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    fun open(
        path: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<InputStream> {
        val builder = HttpRequest.newBuilder(uri(path)).GET()
        headers.forEach(builder::header)
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
    }

    fun text(path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(uri(path)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    override fun close() {
        server.disposeNow()
    }

    private fun uri(path: String): URI = URI.create("http://127.0.0.1:${server.port()}$path")
}

private fun InputStream.readThrough(marker: ByteArray): ByteArray {
    val output = ByteArrayOutputStream()
    while (!output.toByteArray().endsWith(marker)) {
        val next = read()
        check(next >= 0) { "Response ended before marker '${marker.toString(StandardCharsets.UTF_8)}'" }
        output.write(next)
        check(output.size() <= MAX_STREAM_PREFIX_BYTES) { "Response marker was not found within the test bound" }
    }
    return output.toByteArray()
}

private fun ByteArray.endsWith(suffix: ByteArray): Boolean =
    size >= suffix.size && copyOfRange(size - suffix.size, size).contentEquals(suffix)

private const val MAX_STREAM_PREFIX_BYTES: Int = 64 * 1024
