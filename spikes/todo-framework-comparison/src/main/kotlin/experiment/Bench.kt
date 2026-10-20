package experiment

import dev.woge.html.renderHtml
import dev.woge.ktor.WogeKtorHandlers
import dev.woge.ktor.ktorInput
import dev.woge.spring.webflux.WogeWebFluxHandlers
import dev.woge.spring.webflux.webFluxInput
import example.todo.AddTodoAction
import example.todo.DeleteTodoAction
import example.todo.FILTERS
import example.todo.MAX_TITLE_LENGTH
import example.todo.TodosRoute
import example.todo.ToggleTodoAction
import example.todo.addForm
import example.todo.changeForm
import example.todo.liveTodos
import example.todo.todoBoard
import example.todo.todos
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.ul
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitFormData
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.lang.management.ManagementFactory

private val mode = System.getenv("BENCH_MODE") ?: "spring-woge"
private val woge = mode.endsWith("woge")
private val templateEngine =
    TemplateEngine().apply {
        setTemplateResolver(
            ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                isCacheable =
                    true
            },
        )
    }

fun main() {
    repeat(10) { todos.add("Todo <${it + 1}> & Kotlin") }
    if (mode.startsWith("spring")) {
        SpringApplication(BenchConfiguration::class.java)
            .apply {
                setDefaultProperties(
                    mapOf(
                        "server.port" to (System.getenv("PORT") ?: "8080"),
                        "spring.main.web-application-type" to "reactive",
                        "spring.main.banner-mode" to "off",
                        "logging.level.root" to "WARN",
                    ),
                )
            }.run()
    } else {
        embeddedServer(
            Netty,
            host = System.getenv("BENCH_BIND") ?: "127.0.0.1",
            port =
                System.getenv("PORT")?.toInt() ?: 8080,
        ) {
            val handlers = WogeKtorHandlers()
            val page = handlers.liveRegion(liveTodos, TodosRoute)
            val add = handlers.action(AddTodoAction, addForm.ktorInput())
            val toggle = handlers.action(ToggleTodoAction, changeForm.ktorInput())
            val delete = handlers.action(DeleteTodoAction, changeForm.ktorInput())
            routing {
                get("/") {
                    if (woge) {
                        page.handle(call)
                    } else {
                        val filter = call.request.queryParameters["filter"] ?: "all"
                        if (filter !in FILTERS) {
                            call.respondText("Invalid filter", status = HttpStatusCode.BadRequest)
                        } else {
                            call.respondText(fullPage(filter = filter), ContentType.Text.Html)
                        }
                    }
                }
                get("/board") {
                    val filter = call.request.queryParameters["filter"] ?: "all"
                    if (filter !in FILTERS) {
                        call.respondText("Invalid filter", status = HttpStatusCode.BadRequest)
                    } else {
                        call.respondText(board(filter), ContentType.Text.Html)
                    }
                }
                get("/events") {
                    val filter = call.request.queryParameters["filter"] ?: "all"
                    if (filter !in FILTERS) {
                        call.respondText("Invalid filter", status = HttpStatusCode.BadRequest)
                    } else {
                        call.respondTextWriter(ContentType.Text.EventStream) {
                            todos.changes().collect {
                                write("event: todo-change\ndata: changed\n\n")
                                flush()
                            }
                        }
                    }
                }
                get("/bench-render") { call.respondText(renderMetrics(), ContentType.Application.Json) }
                post("/woge-actions/{action}") {
                    if (woge) {
                        when (call.parameters["action"]) {
                            "add-todo" -> add.handle(call)
                            "toggle-todo" -> toggle.handle(call)
                            "delete-todo" -> delete.handle(call)
                        }
                    } else {
                        val response =
                            baseline(
                                call.parameters["action"]!!,
                                call.request.headers["Origin"],
                                call.request.headers["Host"],
                                call.request.headers["HX-Request"] == "true",
                                call.receiveParameters().entries().associate {
                                    it.key to
                                        it.value.first()
                                },
                            )
                        response.location?.let { call.response.headers.append("Location", it) }
                        response.headers.forEach { (key, value) -> call.response.headers.append(key, value) }
                        call.respondText(
                            response.body,
                            ContentType.Text.Html,
                            HttpStatusCode.fromValue(response.status),
                        )
                    }
                }
                staticResources("/assets", "static/assets")
                get("/todo.css") {
                    call.respondText(
                        requireNotNull(object {}.javaClass.getResource("/static/todo.css")).readText(),
                        ContentType.Text.CSS,
                    )
                }
                get("/htmx.min.js") {
                    call.respondText(
                        requireNotNull(object {}.javaClass.getResource("/static/htmx.min.js")).readText(),
                        ContentType.Application.JavaScript,
                    )
                }
                get("/baseline.js") {
                    call.respondText(
                        requireNotNull(object {}.javaClass.getResource("/static/baseline.js")).readText(),
                        ContentType.Application.JavaScript,
                    )
                }
            }
        }.start(wait = true)
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
class BenchConfiguration {
    @Bean
    fun routes() =
        coRouter {
            val handlers = WogeWebFluxHandlers()
            val page = handlers.liveRegion(liveTodos, TodosRoute)
            val add = handlers.action(AddTodoAction, addForm.webFluxInput())
            val toggle = handlers.action(ToggleTodoAction, changeForm.webFluxInput())
            val delete = handlers.action(DeleteTodoAction, changeForm.webFluxInput())
            GET("/") { request ->
                if (woge) {
                    page.handle(request)
                } else {
                    val filter = request.queryParam("filter").orElse("all")
                    if (filter !in FILTERS) {
                        ServerResponse.badRequest().bodyValueAndAwait("Invalid filter")
                    } else {
                        ServerResponse
                            .ok()
                            .contentType(
                                MediaType.TEXT_HTML,
                            ).bodyValueAndAwait(fullPage(filter = filter))
                    }
                }
            }
            GET("/board") { request ->
                val filter = request.queryParam("filter").orElse("all")
                if (filter !in FILTERS) {
                    ServerResponse.badRequest().bodyValueAndAwait("Invalid filter")
                } else {
                    ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValueAndAwait(board(filter))
                }
            }
            GET("/events") { request ->
                val filter = request.queryParam("filter").orElse("all")
                if (filter !in FILTERS) {
                    ServerResponse.badRequest().bodyValueAndAwait("Invalid filter")
                } else {
                    ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM).bodyAndAwait(
                        todos.changes().map { ServerSentEvent.builder("changed").event("todo-change").build() },
                    )
                }
            }
            GET(
                "/bench-render",
            ) { ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(renderMetrics()) }
            POST("/woge-actions/{action}") { request ->
                if (woge) {
                    when (request.pathVariable("action")) {
                        "add-todo" -> add.handle(request)
                        "toggle-todo" -> toggle.handle(request)
                        else -> delete.handle(request)
                    }
                } else {
                    val response =
                        baseline(
                            request.pathVariable("action"),
                            request.headers().firstHeader("Origin"),
                            request.headers().firstHeader("Host"),
                            request.headers().firstHeader("HX-Request") == "true",
                            request.awaitFormData().toSingleValueMap(),
                        )
                    ServerResponse
                        .status(response.status)
                        .contentType(MediaType.TEXT_HTML)
                        .apply {
                            response.location?.let { header("Location", it) }
                            response.headers.forEach { (key, value) -> header(key, value) }
                        }.bodyValueAndAwait(response.body)
                }
            }
        }
}

private data class Result(
    val status: Int,
    val body: String = "",
    val location: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

private fun baseline(
    action: String,
    origin: String?,
    host: String?,
    enhanced: Boolean,
    fields: Map<String, String>,
): Result {
    if (origin != "http://$host") return Result(403)
    val title = fields["title"].orEmpty()
    val filter = fields["filter"] ?: "all"
    if (filter !in FILTERS) return Result(400)
    when (action) {
        "add-todo" -> {
            if (title.trim().isEmpty() ||
                title.trim().length > MAX_TITLE_LENGTH
            ) {
                return if (enhanced) {
                    Result(
                        400,
                        addFormHtml(title, true, filter),
                        headers =
                            mapOf(
                                "HX-Retarget" to "#todo-form",
                                "HX-Reswap" to "outerHTML",
                            ),
                    )
                } else {
                    Result(400, fullPage(title, true, filter))
                }
            }
            todos.add(title)
        }
        "toggle-todo" -> if (!todos.toggle(fields["id"]?.toLongOrNull() ?: return Result(400))) return Result(404)
        "delete-todo" -> if (!todos.delete(fields["id"]?.toLongOrNull() ?: return Result(400))) return Result(404)
        else -> return Result(404)
    }
    val message =
        when (action) {
            "add-todo" -> "Todo added."
            "toggle-todo" -> "Todo updated."
            else -> "Todo deleted."
        }
    return if (enhanced) {
        Result(
            200,
            board(filter) + "<p id=\"todo-status\" role=\"status\" hx-swap-oob=\"outerHTML\">$message</p>" +
                addFormHtml(filter = filter, outOfBand = true),
        )
    } else {
        Result(303, location = "/?filter=$filter")
    }
}

private fun board(filter: String = "all"): String {
    val snapshot = todos.snapshot(filter)
    if (woge) return renderHtml { todoBoard(snapshot) }
    if (mode.startsWith("spring")) {
        return templateEngine.process(
            "board",
            Context().apply {
                setVariable("snapshot", snapshot)
            },
        )
    }
    return createHTML().div {
        id = "todo-board"
        p {
            id = "todo-count"
            +"${snapshot.remaining} remaining · ${snapshot.todos.size} total"
        }
        ul {
            snapshot.visible.forEach { todo ->
                li {
                    attributes["data-todo-id"] = todo.id.toString()
                    span {
                        +todo.title
                        if (todo.completed) +" (completed)"
                    }
                    for ((action, label) in listOf(
                        "toggle-todo" to if (todo.completed) "Mark active" else "Complete",
                        "delete-todo" to "Delete",
                    )) {
                        form(action = "/woge-actions/$action", method = FormMethod.post) {
                            attributes["hx-post"] = "/woge-actions/$action"
                            attributes["hx-target"] = "#todo-board"
                            attributes["hx-swap"] =
                                "outerHTML"
                            input(type = InputType.hidden, name = "id") { value = todo.id.toString() }
                            input(type = InputType.hidden, name = "filter") { value = filter }
                            button { +label }
                        }
                    }
                }
            }
        }
    }
}

private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")

private fun fullPage(
    value: String = "",
    error: Boolean = false,
    filter: String = "all",
): String =
    buildString {
        append("<!doctype html><html lang=\"en\"><head><title>Todos</title>")
        append("<link rel=\"stylesheet\" href=\"/todo.css\"><script src=\"/htmx.min.js\"></script>")
        append("<script defer src=\"/baseline.js\"></script></head><body hx-sync=\"body:queue all\"><h1>Todos</h1>")
        append("<nav aria-label=\"Filter todos\">")
        FILTERS.forEach { choice ->
            append("<a href=\"/?filter=$choice\">${choice.replaceFirstChar(Char::uppercase)}</a> ")
        }
        append("</nav>")
        append(board(filter))
        append("<p id=\"todo-status\" role=\"status\"></p><p id=\"todo-alert\" role=\"alert\"></p>")
        append(addFormHtml(value, error, filter))
        append("</body></html>")
    }

private fun addFormHtml(
    value: String = "",
    error: Boolean = false,
    filter: String = "all",
    outOfBand: Boolean = false,
): String =
    buildString {
        append("<div id=\"todo-form\"")
        if (outOfBand) append(" hx-swap-oob=\"outerHTML\"")
        append('>')
        if (error) {
            append("<div id=\"todo-errors\" tabindex=\"-1\"><h2>Check your todo</h2>")
            append("<a href=\"#todo-title\">Enter a title of 1 to 120 characters.</a></div>")
        }
        append("<form id=\"add-todo\" method=\"post\" action=\"/woge-actions/add-todo\"")
        append(" hx-post=\"/woge-actions/add-todo\" hx-target=\"#todo-board\" hx-swap=\"outerHTML\">")
        append("<label for=\"todo-title\">New todo</label>")
        append("<input id=\"todo-title\" name=\"title\" required maxlength=\"120\" value=\"")
        append(escape(value))
        append('"')
        if (error) append(" aria-invalid=\"true\" aria-describedby=\"todo-errors\"")
        append("><input type=\"hidden\" name=\"filter\" value=\"$filter\"><button>Add todo</button></form></div>")
    }

private fun renderMetrics(): String {
    val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    bean.isThreadAllocatedMemoryEnabled = true
    repeat(1_000) { board() }
    val thread = Thread.currentThread().threadId()
    val allocated = bean.getThreadAllocatedBytes(thread)
    val start = System.nanoTime()
    var bytes = 0L
    repeat(2_000) { bytes += board().toByteArray().size }
    val nanos = System.nanoTime() - start
    val allocatedBytes = bean.getThreadAllocatedBytes(thread) - allocated
    return "{\"iterations\":2000,\"nanos\":$nanos,\"allocatedBytes\":$allocatedBytes,\"outputBytes\":$bytes}"
}
