package dev.woge.example

import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class WogeQuickstartApplicationTest {
    @Test
    fun `fresh application serves shell deferred regions full navigation and ordinary assets`() {
        SpringApplication(WogeQuickstartApplication::class.java)
            .apply {
                setDefaultProperties(
                    mapOf(
                        "server.port" to "0",
                        "spring.main.banner-mode" to "off",
                        "logging.level.root" to "WARN",
                    ),
                )
            }.run()
            .use { context -> verifyApplication(context as ConfigurableWebServerApplicationContext) }
    }

    private fun verifyApplication(context: ConfigurableWebServerApplicationContext) {
        val origin = "http://127.0.0.1:${requireNotNull(context.webServer).port}"
        val shell = get(origin, "/projects/woge")

        assertEquals(200, shell.statusCode())
        assertTrue(shell.header("content-type").startsWith("text/html"))
        assertTrue(shell.body().startsWith("<!doctype html><html lang=\"en\">"))
        assertTrue(shell.body().contains("data-woge-region=\"summary\""))
        assertTrue(shell.body().contains("action=\"/projects/woge\" method=\"get\""))
        assertTrue(shell.body().contains("src=\"/assets/application.js\""))
        assertFalse(shell.body().contains("Publish the first web-first guide"))

        val patches = getBytes(origin, "/projects/woge/woge-patches")
        assertEquals(200, patches.statusCode())
        assertTrue(patches.header("content-type").startsWith("application/vnd.woge.patch-stream"))
        assertTrue(patches.header("content-type").contains("version=1"))
        val events = decode(patches.body())
        val frames = events.filterIsInstance<PatchStreamEvent.PatchFrame>()
        assertEquals(setOf("summary", "tasks", "activity"), frames.map { it.patch.target.region.value }.toSet())
        assertEquals(PatchStreamEvent.Complete(3), events.last())
        assertTrue(frames.any { (it.patch as ReplacePatch).html.value.contains("Publish the first web-first guide") })

        val complete = get(origin, "/projects/woge?view=complete")
        assertEquals(200, complete.statusCode())
        assertTrue(complete.body().contains("Publish the first web-first guide"))
        assertFalse(complete.body().contains("data-woge-region"))
        assertFalse(complete.body().contains("src=\"/assets/application.js\""))

        assertEquals(404, get(origin, "/projects/missing").statusCode())
        assertEquals(400, get(origin, "/projects/woge?view=unknown").statusCode())

        val css = get(origin, "/assets/application.css")
        assertTrue(css.body().contains("@container (width >= 32rem)"))
        val javascript = get(origin, "/assets/application.js")
        assertTrue(javascript.body().contains("createWogePatchRuntime"))
        val runtime = get(origin, "/assets/woge/index.js")
        assertTrue(runtime.body().contains("export function createWogePatchRuntime"))
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
