package dev.woge.example.ktor

import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class WogeKtorQuickstartTest {
    @Test
    fun `Ktor serves the same shell patches navigation and assets`() {
        val server =
            embeddedServer(Netty, host = "127.0.0.1", port = 0, module = Application::wogeReferenceModule)
                .start(wait = false)
        try {
            val port =
                runBlocking {
                    server.engine
                        .resolvedConnectors()
                        .single()
                        .port
                }
            verifyApplication("http://127.0.0.1:$port")
        } finally {
            server.stop(1_000, 1_000)
        }
    }

    private fun verifyApplication(origin: String) {
        val shell = get(origin, "/projects/woge")
        assertEquals(200, shell.statusCode())
        assertTrue(shell.header("content-type").startsWith("text/html"))
        assertTrue(shell.body().startsWith("<!doctype html><html lang=\"en\">"))
        assertTrue(shell.body().contains("data-woge-region=\"summary\""))
        assertTrue(shell.body().contains("action=\"/projects/woge\" method=\"get\""))
        assertFalse(shell.body().contains("Publish the first web-first guide"))

        val patchResponse = getBytes(origin, "/projects/woge/woge-patches")
        assertEquals(200, patchResponse.statusCode())
        assertTrue(patchResponse.header("content-type").startsWith("application/vnd.woge.patch-stream"))
        val events = decode(patchResponse.body())
        val frames = events.filterIsInstance<PatchStreamEvent.PatchFrame>()
        assertEquals(setOf("summary", "tasks", "activity"), frames.map { it.patch.target.region.value }.toSet())
        assertEquals(PatchStreamEvent.Complete(3), events.last())
        assertTrue(frames.any { (it.patch as ReplacePatch).html.value.contains("Publish the first web-first guide") })

        val complete = get(origin, "/projects/woge?view=complete")
        assertTrue(complete.body().contains("Publish the first web-first guide"))
        assertFalse(complete.body().contains("data-woge-region"))
        assertEquals(404, get(origin, "/projects/missing").statusCode())
        assertEquals(400, get(origin, "/projects/woge?view=unknown").statusCode())

        assertTrue(get(origin, "/assets/application.css").body().contains("@container (width >= 32rem)"))
        assertTrue(get(origin, "/assets/application.js").body().contains("createWogePatchRuntime"))
        assertTrue(get(origin, "/assets/woge/index.js").body().contains("export function createWogePatchRuntime"))
    }

    private fun decode(bytes: ByteArray): List<PatchStreamEvent> {
        val decoder = PatchStreamV1.decoder()
        val events = decoder.feed(bytes)
        decoder.finish()
        return events
    }

    private fun get(
        origin: String,
        path: String,
    ): HttpResponse<String> =
        CLIENT.send(
            HttpRequest.newBuilder(URI.create(origin + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun getBytes(
        origin: String,
        path: String,
    ): HttpResponse<ByteArray> =
        CLIENT.send(
            HttpRequest.newBuilder(URI.create(origin + path)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )

    private fun <T> HttpResponse<T>.header(name: String): String = headers().firstValue(name).orElseThrow()

    private companion object {
        private val CLIENT: HttpClient = HttpClient.newHttpClient()
    }
}
