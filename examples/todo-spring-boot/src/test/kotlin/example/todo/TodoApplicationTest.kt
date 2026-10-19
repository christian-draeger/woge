package example.todo

import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.SpringApplication
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TodoApplicationTest {
    private lateinit var application: ConfigurableApplicationContext
    private lateinit var origin: String
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    @BeforeAll
    fun start() {
        application =
            SpringApplication(Application::class.java)
                .apply {
                    setDefaultProperties(
                        mapOf(
                            "server.port" to "0",
                            "spring.main.banner-mode" to "off",
                            "logging.level.root" to "WARN",
                        ),
                    )
                }.run()
        val server = requireNotNull((application as ConfigurableWebServerApplicationContext).webServer)
        origin = "http://127.0.0.1:${server.port}"
    }

    @AfterAll
    fun stop() {
        application.close()
    }

    @BeforeEach
    fun reset() {
        todos.reset()
    }

    @Test
    fun `native forms add filter complete and delete through redirects`() {
        val home = get("/")
        val document = Jsoup.parse(home.body().toString(UTF_8))
        assertEquals(200, home.statusCode())
        assertEquals("Todos", requireNotNull(document.selectFirst("h1")).text())
        assertEquals(AddTodoAction.url.value, requireNotNull(document.selectFirst("#add-todo")).attr("action"))
        assertTrue(requireNotNull(document.selectFirst("#todo-title")).hasAttr("required"))
        assertEquals(200, get("/todo.css").statusCode())
        assertEquals(200, get("/assets/woge/index.js").statusCode())
        assertEquals(200, get("/actuator/health/readiness").statusCode())
        val added = post(AddTodoAction.path, mapOf("title" to "Read <HTML> & CSS", "filter" to "active"))
        assertEquals(303, added.statusCode())
        assertEquals(TodosRoute.url(TodosInput("active")).value, added.headers().firstValue("location").orElseThrow())
        val id =
            todos
                .snapshot()
                .todos
                .single()
                .id
        val page = Jsoup.parse(get("/?filter=active").body().toString(UTF_8))
        assertTrue(requireNotNull(page.selectFirst("#todo-board")).text().contains("Read <HTML> & CSS"))
        assertEquals(303, post(ToggleTodoAction.path, mapOf("id" to "$id")).statusCode())
        assertEquals(0, todos.snapshot().remaining)
        assertEquals(0, Jsoup.parse(get("/?filter=active").body().toString(UTF_8)).select("[data-todo-id]").size)
        assertEquals(1, Jsoup.parse(get("/?filter=completed").body().toString(UTF_8)).select("[data-todo-id]").size)
        assertEquals(303, post(DeleteTodoAction.path, mapOf("id" to "$id")).statusCode())
        assertTrue(todos.snapshot().todos.isEmpty())
        assertEquals(404, post(ToggleTodoAction.path, mapOf("id" to "$id")).statusCode())
    }

    @Test
    fun `server validation preserves input and links field errors`() {
        val invalid = post(AddTodoAction.path, mapOf("title" to "   "))
        assertEquals(400, invalid.statusCode())
        val document = Jsoup.parse(invalid.body().toString(UTF_8))
        val field = requireNotNull(document.selectFirst("#todo-title"))
        assertEquals("   ", field.attr("value"))
        assertEquals("true", field.attr("aria-invalid"))
        assertEquals("#todo-title", requireNotNull(document.selectFirst("#todo-errors a")).attr("href"))
        assertTrue(todos.snapshot().todos.isEmpty())
        assertEquals(400, post(AddTodoAction.path, mapOf("title" to "x".repeat(121))).statusCode())
        assertEquals(400, post(AddTodoAction.path, mapOf("title" to "ok", "filter" to "unknown")).statusCode())
        assertEquals(400, get("/?filter=unknown").statusCode())
    }

    @Test
    fun `cross-origin mutations cannot change server state`() {
        assertEquals(
            403,
            post(
                AddTodoAction.path,
                mapOf("title" to "Untrusted"),
                mapOf(
                    "Origin" to "https://other.example",
                ),
            ).statusCode(),
        )
        assertTrue(todos.snapshot().todos.isEmpty())
    }

    @Test
    fun `enhanced requests use the same action and real framed codec`() {
        val document = Jsoup.parse(get("/").body().toString(UTF_8))
        val headers =
            mapOf(
                "Accept" to "application/vnd.woge.patch-stream; version=1",
                "Woge-Page-Epoch" to requireNotNull(document.selectFirst("meta[name=woge-page-epoch]")).attr("content"),
                "Woge-Region-Revisions" to
                    document.select("[data-woge-region]").joinToString(",") {
                        it.attr("data-woge-region") + ":" + it.attr("data-woge-revision")
                    },
                "Woge-Interaction" to "1",
                "Woge-Request-Identity" to UUID.randomUUID().toString(),
            )
        val response = post(AddTodoAction.path, mapOf("title" to "Reactive <todo>"), headers)
        assertEquals(200, response.statusCode())
        assertTrue(
            response
                .headers()
                .firstValue("content-type")
                .orElseThrow()
                .contains("patch-stream"),
        )
        val decoder = PatchStreamV1.decoder()
        val events = decoder.feed(response.body())
        decoder.finish()
        val frames = events.filterIsInstance<PatchStreamEvent.PatchFrame>().map { it.patch as ReplacePatch }
        assertEquals(3, frames.size)
        assertTrue(
            frames.any { it.html.value.contains("Reactive &lt;todo&gt;") && it.html.value.contains("1 remaining") },
        )
        assertEquals(listOf("Reactive <todo>"), todos.snapshot().todos.map { it.title })
        assertEquals(PatchStreamEvent.Complete(3), events.last())
    }

    private fun get(path: String): HttpResponse<ByteArray> =
        send(HttpRequest.newBuilder(URI.create(origin + path)).GET().build())

    private fun post(
        path: String,
        fields: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<ByteArray> {
        val body =
            fields.entries.joinToString("&") { (name, value) ->
                URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)
            }
        return send(
            HttpRequest
                .newBuilder(URI.create(origin + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", headers["Origin"] ?: origin)
                .apply { headers.filterKeys { it != "Origin" }.forEach { (name, value) -> header(name, value) } }
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
        )
    }

    private fun send(request: HttpRequest): HttpResponse<ByteArray> =
        client.send(
            HttpRequest.newBuilder(request, { _, _ -> true }).timeout(Duration.ofSeconds(10)).build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
}
