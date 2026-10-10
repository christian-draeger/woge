package dev.woge.development.browser

import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.DevelopmentSessionPhase
import dev.woge.development.DevelopmentUrl
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import dev.woge.development.orchestrator.DevelopmentAdapters
import dev.woge.development.orchestrator.DevelopmentBuildAdapter
import dev.woge.development.orchestrator.DevelopmentBuildResult
import dev.woge.development.orchestrator.DevelopmentFrontendAdapter
import dev.woge.development.orchestrator.DevelopmentHostAdapter
import dev.woge.development.orchestrator.DevelopmentHostRestartRequest
import dev.woge.development.orchestrator.DevelopmentHostRestartResult
import dev.woge.development.orchestrator.DevelopmentOrchestrator
import dev.woge.html.renderHtml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalWogeDevelopmentApi::class)
class DevelopmentBrowserChannelTest {
    private class Fixture(
        maxTabs: Int = 16,
    ) : AutoCloseable {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val orchestrator =
            DevelopmentOrchestrator.start(
                scope,
                DevelopmentAdapters(
                    DevelopmentBuildAdapter { request ->
                        if (request.changes.all { it.kind == DevelopmentChangeKind.CSS }) {
                            DevelopmentBuildResult.Succeeded(ReloadLevel.HOT_ASSET)
                        } else {
                            DevelopmentBuildResult.Succeeded()
                        }
                    },
                    object : DevelopmentHostAdapter {
                        override suspend fun restart(request: DevelopmentHostRestartRequest) =
                            DevelopmentHostRestartResult.Ready(listOf(DevelopmentUrl.local("http://127.0.0.1:8080/")))

                        override suspend fun shutdown() = Unit
                    },
                    DevelopmentFrontendAdapter { _, level ->
                        level == ReloadLevel.HOT_ASSET || level == ReloadLevel.DOCUMENT_REFRESH
                    },
                ),
            )
        val channel = DevelopmentBrowserChannel(scope, orchestrator, setOf("http://127.0.0.1:8080"), maxTabs = maxTabs)
        val http = HttpClient.newHttpClient()

        override fun close() {
            channel.close()
            runBlocking { orchestrator.stop() }
            scope.cancel()
        }

        fun request(
            url: String,
            origin: String? = null,
        ): HttpRequest =
            HttpRequest
                .newBuilder(URI(url))
                .timeout(java.time.Duration.ofSeconds(5))
                .apply {
                    if (origin != null) header("Origin", origin)
                }.build()
    }

    @Test
    fun `development handoff and shared control resources are served as plain modules`() {
        Fixture().use { fixture ->
            for (asset in listOf("client.js", "refresh-state.js", "state-controls.js", "stylesheets.js")) {
                val response =
                    fixture.http.send(
                        fixture.request("${fixture.channel.baseUrl}/$asset", "http://127.0.0.1:8080"),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                assertEquals(200, response.statusCode())
                assertTrue(
                    response
                        .headers()
                        .firstValue("Content-Type")
                        .orElseThrow()
                        .startsWith("text/javascript"),
                )
                assertTrue(response.body().contains("export"))
                assertFalse(response.body().contains("token="))
            }
        }
    }

    @Test
    fun `only the session credential and explicitly allowed origins can read events`() {
        Fixture().use { fixture ->
            assertEquals(
                403,
                fixture.http
                    .send(
                        fixture.request("${fixture.channel.baseUrl}/events"),
                        HttpResponse.BodyHandlers.ofString(),
                    ).statusCode(),
            )
            assertEquals(
                403,
                fixture.http
                    .send(
                        fixture.request(fixture.channel.eventsUrl, "https://evil.example"),
                        HttpResponse.BodyHandlers.ofString(),
                    ).statusCode(),
            )
            val valid =
                fixture.http.send(
                    fixture.request(fixture.channel.eventsUrl, "http://127.0.0.1:8080"),
                    HttpResponse.BodyHandlers.ofInputStream(),
                )
            valid.body().use {
                assertEquals(200, valid.statusCode())
                assertEquals(
                    "http://127.0.0.1:8080",
                    valid.headers().firstValue("Access-Control-Allow-Origin").orElseThrow(),
                )
                assertTrue(
                    valid
                        .headers()
                        .firstValue("Content-Type")
                        .orElseThrow()
                        .startsWith("text/event-stream"),
                )
                assertFalse(fixture.channel.toString().contains("token="))
            }
        }
    }

    @Test
    fun `multiple tabs receive the same ready identity and reconnect gets the newest snapshot`() =
        runBlocking {
            Fixture().use { fixture ->
                val first =
                    fixture.http.send(
                        fixture.request(fixture.channel.eventsUrl),
                        HttpResponse.BodyHandlers.ofInputStream(),
                    )
                val second =
                    fixture.http.send(
                        fixture.request(fixture.channel.eventsUrl),
                        HttpResponse.BodyHandlers.ofInputStream(),
                    )
                first.body().bufferedReader().use { tabOne ->
                    second.body().bufferedReader().use { tabTwo ->
                        assertEquals(readSnapshot(tabOne), readSnapshot(tabTwo))
                        fixture.orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE))
                        withTimeout(10.seconds) {
                            fixture.orchestrator.state.first { it.phase == DevelopmentSessionPhase.READY }
                        }
                        val one = readReady(tabOne)
                        val two = readReady(tabTwo)
                        assertEquals(one, two)
                        assertEquals(
                            "1",
                            Json
                                .parseToJsonElement(one)
                                .jsonObject
                                .getValue("generation")
                                .jsonPrimitive.content,
                        )
                    }
                }
                val resumed =
                    fixture.http.send(
                        HttpRequest
                            .newBuilder(URI(fixture.channel.eventsUrl))
                            .header("Last-Event-ID", "unknown-session:0")
                            .build(),
                        HttpResponse.BodyHandlers.ofInputStream(),
                    )
                resumed.body().bufferedReader().use { reader ->
                    assertEquals(
                        "READY",
                        Json
                            .parseToJsonElement(readSnapshot(reader))
                            .jsonObject
                            .getValue("phase")
                            .jsonPrimitive.content,
                    )
                }
            }
        }

    @Test
    fun `a stylesheet-only build is rendered without a new document build`() =
        runBlocking {
            Fixture().use { fixture ->
                val stream =
                    fixture.http.send(
                        fixture.request(fixture.channel.eventsUrl),
                        HttpResponse.BodyHandlers.ofInputStream(),
                    )
                stream.body().bufferedReader().use { reader ->
                    readSnapshot(reader)
                    fixture.orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE))
                    val server = Json.parseToJsonElement(readReady(reader)).jsonObject
                    assertEquals("1", server.getValue("renderedBuild").jsonPrimitive.content)
                    assertEquals("1", server.getValue("documentBuild").jsonPrimitive.content)

                    fixture.orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.CSS))
                    val css =
                        generateSequence { Json.parseToJsonElement(readReady(reader)).jsonObject }
                            .first { it.getValue("renderedBuild").jsonPrimitive.content == "2" }
                    assertEquals("1", css.getValue("documentBuild").jsonPrimitive.content)
                    assertEquals("1", css.getValue("generation").jsonPrimitive.content)
                }
            }
        }

    @Test
    fun `the development client is added only by explicit typed DSL opt in`() {
        Fixture().use { fixture ->
            val production = renderHtml { element("p") { text("No development resources") } }
            val development = renderHtml { developmentClient(fixture.channel, null, null, overlay = false) }
            assertFalse(production.contains("woge-development"))
            assertFalse(production.contains(fixture.channel.baseUrl))
            assertTrue(development.contains("type=\"module\""))
            assertTrue(development.contains("name=\"woge-development\""))
            assertFalse(development.contains("overlay.css"))
            assertTrue(development.contains("&quot;endpoint&quot;"))
        }
    }

    @Test
    fun `tab budget is enforced and shutdown closes existing streams`() {
        Fixture(maxTabs = 1).use { fixture ->
            val stream =
                fixture.http.send(
                    fixture.request(fixture.channel.eventsUrl),
                    HttpResponse.BodyHandlers.ofInputStream(),
                )
            val excess =
                fixture.http.send(
                    fixture.request(fixture.channel.eventsUrl),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(503, excess.statusCode())
            stream.body().close()
            fixture.channel.close()
        }
    }

    private fun readSnapshot(reader: java.io.BufferedReader): String =
        generateSequence { reader.readLine() }.first { it.startsWith("data: ") }.removePrefix("data: ")

    private fun readReady(reader: java.io.BufferedReader): String =
        generateSequence { readSnapshot(reader) }.first {
            Json
                .parseToJsonElement(it)
                .jsonObject
                .getValue("phase")
                .jsonPrimitive.content == "READY"
        }
}
