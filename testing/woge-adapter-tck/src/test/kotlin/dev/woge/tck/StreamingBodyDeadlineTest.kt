package dev.woge.tck

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StreamingBodyDeadlineTest {
    @Test
    @Timeout(8)
    fun `a body blocked after its first byte fails independently of the header deadline`() {
        val release = CountDownLatch(1)
        val server =
            HttpServer
                .create(InetSocketAddress("127.0.0.1", 0), 0)
                .apply {
                    createContext("/") { exchange ->
                        exchange.use {
                            exchange.sendResponseHeaders(200, 2)
                            exchange.responseBody.write('a'.code)
                            exchange.responseBody.flush()
                            release.await(5, TimeUnit.SECONDS)
                        }
                    }
                    start()
                }
        try {
            HttpClient.newHttpClient().use { client ->
                val response = client.send(request(server), streamingBodyWithDeadline(Duration.ofSeconds(1)))
                response.body().use { body ->
                    assertEquals(200, response.statusCode())
                    assertEquals('a'.code, body.read())
                    val failure = assertThrows(IllegalStateException::class.java) { body.readAllBytes() }
                    assertTrue(failure.message!!.contains("read deadline"))
                    assertEquals(1L, release.count)
                }
            }
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    @Test
    fun `a completed response preserves its exact bytes and normal EOF`() {
        val server =
            HttpServer
                .create(InetSocketAddress("127.0.0.1", 0), 0)
                .apply {
                    createContext("/") { exchange ->
                        exchange.use {
                            val content = "Grüße".toByteArray()
                            exchange.sendResponseHeaders(200, content.size.toLong())
                            exchange.responseBody.write(content)
                        }
                    }
                    start()
                }
        try {
            HttpClient.newHttpClient().use { client ->
                val response = client.send(request(server), streamingBodyWithDeadline(Duration.ofSeconds(2)))
                response.body().use { body ->
                    assertEquals("Grüße", body.readAllBytes().toString(Charsets.UTF_8))
                    assertEquals(-1, body.read())
                }
            }
        } finally {
            server.stop(0)
        }
    }

    private fun request(server: HttpServer): HttpRequest =
        HttpRequest
            .newBuilder(URI.create("http://127.0.0.1:${server.address.port}/"))
            .timeout(Duration.ofSeconds(3))
            .build()
}
