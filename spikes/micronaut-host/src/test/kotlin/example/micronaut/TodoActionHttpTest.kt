package example.micronaut

import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.UUID

internal class TodoActionHttpTest {
    @Test
    fun `native and enhanced submissions write through the same action`() = withServer { fixture ->
        val writer = fixture.writer
        val initial = fixture.get("/").document()
        assertEquals("/woge-actions/add-todo", initial.selectFirst("#add-todo")?.attr("action"))

        val invalid = fixture.post("   ", writer)
        assertEquals(400, invalid.statusCode())
        assertEquals("   ", invalid.document().selectFirst("#todo-title")?.attr("value"))
        assertEquals("#todo-title", invalid.document().selectFirst("#todo-errors a")?.attr("href"))

        val native = fixture.post("Native <Todo>", writer)
        assertEquals(303, native.statusCode())
        val location = native.headers().firstValue("Location").orElseThrow()
        assertEquals("/?filter=active", location)
        val afterNative = fixture.get(location).document()
        assertEquals(1, afterNative.select("[data-todo-id]").count { it.text().contains("Native <Todo>") })

        val epoch = requireNotNull(afterNative.selectFirst("meta[name=woge-page-epoch]")).attr("content")
        val enhancedHeaders = mapOf(
            "Accept" to PatchStreamV1.MEDIA_TYPE,
            "Woge-Page-Epoch" to epoch,
            "Woge-Region-Revisions" to afterNative.select("[data-woge-region]").joinToString(",") {
                it.attr("data-woge-region") + ":" + it.attr("data-woge-revision")
            },
            "Woge-Interaction" to "1",
            "Woge-Request-Identity" to UUID.randomUUID().toString(),
        )
        val enhanced = fixture.post("Enhanced <Todo>", writer, headers = enhancedHeaders)
        assertEquals(200, enhanced.statusCode())
        assertTrue(enhanced.headers().firstValue("Content-Type").orElseThrow().contains("application/vnd.woge.patch-stream"))
        val decoder = PatchStreamV1.decoder()
        val events = decoder.feed(enhanced.body())
        decoder.finish()
        val patches = events.filterIsInstance<PatchStreamEvent.PatchFrame>().map { it.patch as ReplacePatch }
        assertEquals(3, patches.size)
        assertTrue(patches.all { it.target.pageEpoch.value == epoch })
        assertTrue(patches.any { it.html.value.contains("Enhanced &lt;Todo&gt;") })
        assertEquals(PatchStreamEvent.Complete(3), events.last())
        val fresh = fixture.get(location).document()
        assertEquals(1, fresh.select("[data-todo-id]").count { it.text().contains("Enhanced <Todo>") })
    }

    @Test
    fun `identity role and trusted request origin gate writes`() = withServer { fixture ->
        val prior = fixture.get("/").document().select("[data-todo-id]").eachText()
        val rejected = "Forbidden <Todo>"
        assertEquals(401, fixture.post(rejected, auth = null).statusCode())
        assertEquals(403, fixture.post(rejected, auth = fixture.reader).statusCode())
        assertEquals(403, fixture.post(rejected, auth = fixture.writer, origin = null).statusCode())
        val spoofedOrigin = fixture.post(
            rejected,
            fixture.writer,
            origin = "https://other.example",
            headers = mapOf("Forwarded" to "proto=https;host=other.example", "X-Forwarded-Host" to "other.example"),
        )
        assertEquals(403, spoofedOrigin.statusCode())
        val unsupported = fixture.post(
            rejected,
            fixture.writer,
            headers = mapOf("Accept" to "application/vnd.woge.patch-stream; version=2"),
        )
        assertEquals(406, unsupported.statusCode())
        assertEquals("unsupported-version", unsupported.headers().firstValue("Woge-Protocol-Error").orElseThrow())
        val oversized = "x".repeat(17_000)
        assertEquals(413, fixture.post(oversized, fixture.writer).statusCode())
        assertEquals(413, fixture.post(oversized, fixture.writer, chunked = true).statusCode())
        assertEquals(prior, fixture.get("/").document().select("[data-todo-id]").eachText())
    }

    private fun HttpResponse<ByteArray>.document() = Jsoup.parse(body().toString(Charsets.UTF_8))

    private fun withServer(test: (Fixture) -> Unit) {
        val writerPassword = UUID.randomUUID().toString()
        val readerPassword = UUID.randomUUID().toString()
        val config = mapOf(
            "micronaut.server.port" to -1,
            "woge.spike.writer-password" to writerPassword,
            "woge.spike.reader-password" to readerPassword,
        )
        ApplicationContext.run(EmbeddedServer::class.java, config).use { server ->
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
                test(Fixture(client, "http://127.0.0.1:${server.port}", writerPassword, readerPassword))
            }
        }
    }

    private class Fixture(
        private val client: HttpClient,
        private val base: String,
        writerPassword: String,
        readerPassword: String,
    ) {
        val writer = basic("writer", writerPassword)
        val reader = basic("reader", readerPassword)

        fun get(path: String): HttpResponse<ByteArray> = send(request(path).GET())

        fun post(
            title: String,
            auth: String?,
            origin: String? = base,
            headers: Map<String, String> = emptyMap(),
            chunked: Boolean = false,
        ): HttpResponse<ByteArray> {
            val request = request("/woge-actions/add-todo").header("Content-Type", "application/x-www-form-urlencoded")
            auth?.let { request.header("Authorization", it) }
            origin?.let { request.header("Origin", it) }
            headers.forEach { (key, value) -> request.header(key, value) }
            val body = "title=${URLEncoder.encode(title, Charsets.UTF_8)}&filter=active"
            val publisher = if (chunked) {
                HttpRequest.BodyPublishers.ofInputStream { body.byteInputStream() }
            } else {
                HttpRequest.BodyPublishers.ofString(body)
            }
            return send(request.POST(publisher))
        }

        private fun request(path: String): HttpRequest.Builder =
            HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))

        private fun send(request: HttpRequest.Builder): HttpResponse<ByteArray> =
            client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())

        private fun basic(user: String, password: String): String =
            "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
    }
}
