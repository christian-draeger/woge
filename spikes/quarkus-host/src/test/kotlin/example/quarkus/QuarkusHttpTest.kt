package example.quarkus

import dev.woge.host.PageUseCase
import dev.woge.host.RequestTrace
import dev.woge.host.ResponseHeaders
import dev.woge.host.ResponseMetadata
import dev.woge.host.httpHeader
import dev.woge.host.responseCookie
import dev.woge.host.streamingHtmlPage
import dev.woge.protocol.htmlFrame
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val FIRST = "<p>Grüße</p>"
private const val LAST = "<p>Ready</p>"

@ApplicationScoped
class StreamProbe {
    @Volatile
    internal var plan = Plan()
        private set

    internal fun start(maxBytes: Long = 1024): Plan = Plan(maxBytes).also { plan = it }

    fun register(@Observes router: Router) {
        router.get("/test/stream").handler { call ->
            val current = plan
            serve(call, PageUseCase<Unit> { request ->
                current.ingress.complete(request.context.trace)
                val frames = flow {
                    try {
                        emit(htmlFrame { element("p") { text("Grüße") } })
                        current.worker.complete(Vertx.currentContext()?.isEventLoopContext == true)
                        current.release.await()
                        current.resumed.complete(request.context.trace)
                        emit(htmlFrame { element("p") { text("Ready") } })
                    } finally {
                        current.cleanedUp.complete(Unit)
                    }
                }
                streamingHtmlPage(
                    frames,
                    ResponseMetadata(
                        headers = ResponseHeaders.of(httpHeader("X-Woge-Fixture", "stream")),
                        cookies = listOf(responseCookie("woge_fixture", "one")),
                    ),
                    current.maxBytes,
                )
            }, Unit)
        }
    }
}

internal class Plan(val maxBytes: Long = 1024) {
    val ingress = CompletableDeferred<RequestTrace>()
    val worker = CompletableDeferred<Boolean>()
    val release = CompletableDeferred<Unit>()
    val resumed = CompletableDeferred<RequestTrace>()
    val cleanedUp = CompletableDeferred<Unit>()
}

@QuarkusTest
class QuarkusHttpTest {
    @TestHTTPResource
    lateinit var base: URL

    @Inject
    lateinit var probe: StreamProbe

    @Test
    fun `portable HTML HEAD and classpath stylesheet are hosted independently`() {
        newClient().use { client ->
            val page = client.send(request("/"), HttpResponse.BodyHandlers.ofString())
            assertEquals(200, page.statusCode())
            assertTrue(page.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"))
            assertTrue("Welcome to the garden club" in page.body())
            val head = client.send(
                HttpRequest.newBuilder(base.toURI()).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
            assertEquals(200, head.statusCode())
            assertEquals(page.headers().firstValue("Content-Type"), head.headers().firstValue("Content-Type"))
            assertEquals(0, head.body().size)
            val asset = client.send(request("/assets/static-page/site.css"), HttpResponse.BodyHandlers.ofString())
            assertEquals(200, asset.statusCode())
            assertTrue(asset.headers().firstValue("Content-Type").orElseThrow().startsWith("text/css"))
            assertTrue("font-family" in asset.body())
        }
    }

    @Test
    fun `HTML yields first bytes before suspended work and retains immutable ingress trace`() {
        val plan = probe.start()
        try {
            newClient().use { client ->
                val response = client.send(request("/test/stream"), HttpResponse.BodyHandlers.ofInputStream())
                assertEquals(200, response.statusCode())
                assertEquals("stream", response.headers().firstValue("X-Woge-Fixture").orElseThrow())
                val cookie = response.headers().firstValue("Set-Cookie").orElseThrow().lowercase()
                assertTrue("woge_fixture=one" in cookie && "secure" in cookie && "httponly" in cookie && "samesite=lax" in cookie)
                response.body().use { body ->
                    assertEquals(FIRST, String(body.readNBytes(FIRST.toByteArray().size), Charsets.UTF_8))
                    assertFalse(await(plan.worker))
                    assertFalse(plan.release.isCompleted)
                    val independent = client.send(request("/"), HttpResponse.BodyHandlers.discarding())
                    assertEquals(200, independent.statusCode())
                    plan.release.complete(Unit)
                    assertEquals(LAST, String(body.readAllBytes(), Charsets.UTF_8))
                    assertEquals(await(plan.ingress), await(plan.resumed))
                    await(plan.cleanedUp)
                }
            }
        } finally {
            plan.release.complete(Unit)
        }
    }

    @Test
    fun `a late document budget failure does not finish the chunked reply`() {
        val plan = probe.start(FIRST.toByteArray().size.toLong())
        try {
            newClient().use { client ->
                val response = client.send(request("/test/stream"), HttpResponse.BodyHandlers.ofInputStream())
                response.body().use { body ->
                    assertEquals(FIRST, String(body.readNBytes(FIRST.toByteArray().size), Charsets.UTF_8))
                    plan.release.complete(Unit)
                    assertThrows(IOException::class.java) { body.readAllBytes() }
                    await(plan.cleanedUp)
                }
            }
        } finally {
            plan.release.complete(Unit)
        }
    }

    @Test
    fun `a reset peer cancels suspended production without releasing its gate`() {
        val plan = probe.start()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(base.host, base.port), 5000)
                socket.soTimeout = 5000
                val request = "GET /test/stream HTTP/1.1\r\nHost: ${base.host}:${base.port}\r\n\r\n"
                socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                assertEquals("HTTP/1.1 200 OK", reader.readLine())
                var header = reader.readLine()
                while (header.isNotEmpty()) header = reader.readLine()
                assertEquals(FIRST.toByteArray().size, reader.readLine().toInt(16))
                assertEquals(FIRST, reader.readLine())
                assertFalse(await(plan.worker))
                socket.setSoLinger(true, 0)
            }
            await(plan.cleanedUp)
            assertFalse(plan.release.isCompleted)
            assertFalse(plan.resumed.isCompleted)
        } finally {
            plan.release.complete(Unit)
        }
    }

    private fun request(path: String): HttpRequest =
        HttpRequest.newBuilder(URI.create(base.toString()).resolve(path)).timeout(Duration.ofSeconds(5)).build()

    private fun newClient(): HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    private fun <T> await(result: CompletableDeferred<T>): T = runBlocking { withTimeout(5000) { result.await() } }
}
