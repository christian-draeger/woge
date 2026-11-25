package dev.woge.quarkus

import dev.woge.host.PageUseCase
import dev.woge.host.htmlPage
import dev.woge.host.streamingHtmlPage
import dev.woge.protocol.htmlFrame
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class QuarkusStreamingTest {
    @Test
    @Timeout(15)
    fun `one paused client neither drains the renderer nor blocks a second client`() {
        runBlocking {
            val vertx = Vertx.vertx(VertxOptions().setEventLoopPoolSize(1))
            try {
                val completed = CompletableDeferred<Unit>()
                val released = CompletableDeferred<Unit>()
                val stream =
                    PageUseCase<Unit> {
                        streamingHtmlPage(
                            flow {
                                try {
                                    repeat(FRAME_COUNT) {
                                        emit(htmlFrame { element("p") { text(LARGE_TEXT) } })
                                    }
                                    completed.complete(Unit)
                                } finally {
                                    released.complete(Unit)
                                }
                            },
                            maxBytes = MAX_BYTES,
                        )
                    }
                val handlers = WogeQuarkusHandlers()
                val router = Router.router(vertx)
                router.get("/slow").handler(handlers.page(stream, QuarkusPageInput { Unit }))
                router.get("/ready").handler(
                    handlers.page(PageUseCase { htmlPage { text("ready") } }, QuarkusPageInput { Unit }),
                )
                val options = HttpServerOptions().setSendBufferSize(SOCKET_BUFFER)
                val server =
                    vertx
                        .createHttpServer(options)
                        .requestHandler(router)
                        .listen(0, "127.0.0.1")
                        .coAwait()
                Socket().use { socket ->
                    socket.receiveBufferSize = SOCKET_BUFFER
                    socket.soTimeout = READ_TIMEOUT
                    socket.setSoLinger(true, 0)
                    socket.connect(InetSocketAddress("127.0.0.1", server.actualPort()), READ_TIMEOUT)
                    socket.getOutputStream().write("GET /slow HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                    assertEquals('H'.code, socket.getInputStream().read())
                    HttpClient.newHttpClient().use { client ->
                        val ready =
                            HttpRequest
                                .newBuilder(URI.create("http://127.0.0.1:${server.actualPort()}/ready"))
                                .timeout(Duration.ofSeconds(5))
                                .build()
                        assertEquals("ready", client.send(ready, HttpResponse.BodyHandlers.ofString()).body())
                    }
                    assertFalse(completed.isCompleted)
                }
                withTimeout(5.seconds) { released.await() }
            } finally {
                vertx.close().coAwait()
            }
        }
    }
}

private val LARGE_TEXT = "bounded stream".repeat(1_024)
private const val FRAME_COUNT = 256
private const val MAX_BYTES = 4L * 1024 * 1024
private const val SOCKET_BUFFER = 4_096
private const val READ_TIMEOUT = 5_000
