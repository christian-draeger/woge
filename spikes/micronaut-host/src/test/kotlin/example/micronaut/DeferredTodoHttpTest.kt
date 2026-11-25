package example.micronaut

import dev.woge.host.AuthenticationFacts
import dev.woge.host.RequestMethod
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamException
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import io.micronaut.context.ApplicationContext
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.EOFException
import java.net.SocketException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

class DeferredTodoHttpTest {
    @Test
    fun `shell flushes and a sibling patch completes independently with the original Woge context`() =
        runBlocking {
            server().use { server ->
                val probe = server.applicationContext.getBean(StreamProofController::class.java).deferred
                val eventLoops = server.applicationContext
                    .getBean(EventLoopGroupRegistry::class.java).defaultEventLoopGroup
                assertEquals(1, eventLoops.count())

                response(server).use { response ->
                    val body = DocumentWire(response)
                    val shell = body.awaitFrames(1)
                    assertTrue(shell.startsWith("<!doctype html><html><head>"))
                    assertTrue(shell.contains("Todo counts"))
                    assertTrue(shell.contains("Loading active todos"))
                    assertTrue(shell.contains("Loading completed todos"))
                    assertEquals("/", Jsoup.parse(shell).selectFirst("a")?.attr("href"))
                    val epoch = Jsoup.parse(shell).selectFirst("meta[name=woge-page-epoch]")?.attr("content")
                    assertEquals(emptyList<ReplacePatch>(), body.patches(complete = false))

                    val route = withTimeout(5000) { probe.routeRead.await() }
                    assertEquals(3, route.count)
                    assertFalse(eventLoops.any { it.inEventLoop(route.thread) })
                    val active = withTimeout(5000) { probe.active.started.await() }
                    val completed = withTimeout(5000) { probe.completed.started.await() }
                    assertSame(active.context, completed.context)
                    assertEquals(RequestMethod.GET, completed.context.method)
                    assertEquals(epoch, completed.context.render.epoch)
                    val identity = completed.context.authentication as AuthenticationFacts.Authenticated
                    assertEquals("reader", identity.principal.subject.value)
                    assertEquals(completed.context.requestId.value, completed.context.correlationId.value)
                    assertFalse(eventLoops.any { it.inEventLoop(active.thread) })
                    assertFalse(eventLoops.any { it.inEventLoop(completed.thread) })

                    probe.completed.release.complete(Unit)
                    body.awaitFrames(2)
                    assertFalse(probe.active.release.isCompleted)
                    val first = body.patches(complete = false).single()
                    assertEquals("completed-todos", first.target.region.value)
                    assertEquals(epoch, first.target.pageEpoch.value)
                    assertTrue(first.html.value.contains("completed todos: 2"))
                    val resumed = withTimeout(5000) { probe.completed.resumed.await() }
                    assertSame(completed.context, resumed.context)
                    assertFalse(eventLoops.any { it.inEventLoop(resumed.thread) })

                    probe.active.release.completeExceptionally(IllegalStateException("private loader error"))
                    body.drain()
                    assertTrue(body.markup.endsWith("</body></html>"))
                    val patches = body.patches(complete = true)
                    assertEquals(listOf("completed-todos", "active-todos"), patches.map { it.target.region.value })
                    assertTrue(patches.last().html.value.contains("Could not load active todos"))
                    assertFalse(patches.last().html.value.contains("private loader error"))
                }
            }
        }

    @Test
    fun `late Woge patch limit preserves the visible shell without a successful patch terminator`() {
        server().use { server ->
            val probe = server.applicationContext.getBean(StreamProofController::class.java).deferred
            response(server, "?limited=true").use { response ->
                val body = DocumentWire(response)
                assertTrue(body.awaitFrames(1).contains("Loading active todos"))
                probe.completed.release.complete(Unit)
                body.awaitFrames(2)
                assertEquals("completed-todos", body.patches(complete = false).single().target.region.value)

                probe.active.release.complete(Unit)
                try {
                    body.drain()
                } catch (_: EOFException) {
                    // An already committed HTTP response can end before its next chunk.
                } catch (_: SocketException) {
                    // Netty can close the connection on an error after committing the response.
                }
                assertFalse(body.markup.contains("data-woge-dpu-frame=\"end\""))
                assertFalse(body.markup.contains("WOGE_RESOURCE_LIMIT_EXCEEDED"))
                assertEquals("completed-todos", body.patches(complete = false).single().target.region.value)
                assertThrows(PatchStreamException::class.java) { body.patches(complete = true) }
            }
        }
    }

    @Test
    fun `TCP reset cancels both suspended Woge regions without opening their work gates`() =
        runBlocking {
            server().use { server ->
                val probe = server.applicationContext.getBean(StreamProofController::class.java).deferred
                response(server).use { response ->
                    assertTrue(DocumentWire(response).awaitFrames(1).contains("Todo counts"))
                    withTimeout(5000) {
                        probe.active.started.await()
                        probe.completed.started.await()
                    }
                    response.resetOnClose()
                }

                withTimeout(5000) {
                    probe.active.cleanedUp.await()
                    probe.completed.cleanedUp.await()
                }
                assertFalse(probe.active.release.isCompleted)
                assertFalse(probe.completed.release.isCompleted)
                assertFalse(probe.active.resumed.isCompleted)
                assertFalse(probe.completed.resumed.isCompleted)
            }
        }

    @Test
    fun `admission refusal responds before any Woge bytes or region work`() {
        server().use { server ->
            val probe = server.applicationContext.getBean(StreamProofController::class.java).deferred
            HttpClient.newHttpClient().use { client ->
                val reply = client.send(
                    request(server, StreamProofController.DEFERRED_PATH + "?denied=true").build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                assertEquals(503, reply.statusCode())
                assertEquals("no-store", reply.headers().firstValue("Cache-Control").orElseThrow())
                assertFalse(reply.body().contains("WOGE_RESOURCE_LIMIT_EXCEEDED"))
                assertFalse(reply.body().contains("Todo counts"))
                assertFalse(probe.active.started.isCompleted)
                assertFalse(probe.completed.started.isCompleted)
            }
        }
    }

    @Test
    fun `GET and HEAD preserve finalized Woge metadata and host-only cookie`() {
        server().use { server ->
            HttpClient.newHttpClient().use { client ->
                for (method in listOf("GET", "HEAD")) {
                    val request = request(server, "/stream-proof/metadata")
                        .method(method, HttpRequest.BodyPublishers.noBody()).build()
                    val reply = client.send(request, HttpResponse.BodyHandlers.ofString())
                    assertEquals(200, reply.statusCode())
                    assertEquals(listOf("first", "second"), reply.headers().allValues("X-Woge-Proof"))
                    assertEquals("text/html; charset=utf-8", reply.headers().firstValue("Content-Type").orElseThrow().lowercase())
                    val cookie = reply.headers().allValues("Set-Cookie").single().lowercase().split(';').map(String::trim)
                    assertTrue(cookie.containsAll(listOf("woge-spike=fixture", "path=/stream-proof", "secure", "httponly", "samesite=strict", "max-age=120")))
                    assertFalse(cookie.any { it.startsWith("domain=") })
                    if (method == "HEAD") assertEquals("", reply.body())
                    else assertTrue(reply.body().contains("Metadata proof"))
                }
            }
        }
    }

    private fun request(server: EmbeddedServer, path: String): HttpRequest.Builder =
        HttpRequest.newBuilder(server.url.toURI().resolve(path)).timeout(Duration.ofSeconds(5))

    private fun server(): EmbeddedServer =
        ApplicationContext.run(
            EmbeddedServer::class.java,
            mapOf(
                "micronaut.server.port" to -1,
                "micronaut.netty.event-loops.default.num-threads" to 1,
                "woge.stream-proof" to true,
                "woge.spike.reader-password" to "deferred-fixture-password",
            ),
        )

    private fun response(server: EmbeddedServer, query: String = ""): ChunkedResponse =
        ChunkedResponse.get(
            server,
            StreamProofController.DEFERRED_PATH + query,
            mapOf("Authorization" to "Basic " + Base64.getEncoder().encodeToString("reader:deferred-fixture-password".toByteArray())),
        ).also { response ->
            assertEquals("HTTP/1.1 200 OK", response.status)
            assertEquals("text/html; charset=utf-8", response.headers["content-type"]?.lowercase())
            assertEquals("no-store", response.headers["cache-control"])
            assertTrue(response.headers.getValue("vary").split(',').any { it.trim().equals("Cookie", ignoreCase = true) })
        }

    private class DocumentWire(private val response: ChunkedResponse) {
        private val output = StringBuilder()
        val markup: String get() = output.toString()

        fun awaitFrames(count: Int): String {
            while (frames().count() < count) {
                check(readChunk()) { "Response ended before Woge frame $count" }
            }
            return markup
        }

        fun drain() {
            while (readChunk()) {
                // Preserve all received text for the patch decoder and the shell assertions.
            }
        }

        fun patches(complete: Boolean): List<ReplacePatch> {
            val decoder = PatchStreamV1.decoder()
            val events = frames().flatMap { decoder.feed(Base64.getDecoder().decode(it.groupValues[1])) }.toList()
            if (complete) {
                decoder.finish()
                assertEquals(PatchStreamEvent.Complete(2), events.last())
            }
            return events.filterIsInstance<PatchStreamEvent.PatchFrame>().map { it.patch as ReplacePatch }
        }

        private fun frames() = envelope.findAll(markup)

        private fun readChunk(): Boolean {
            val bytes = response.readChunk()
            output.append(bytes.toString(Charsets.UTF_8))
            return bytes.isNotEmpty()
        }

        companion object {
            val envelope = Regex("<template data-woge-dpu-frame=\"(?:chunk|end)\">([^<]+)</template>")
        }
    }
}
