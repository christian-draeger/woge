package baseline

import example.todo.FILTERS
import example.todo.MAX_TITLE_LENGTH
import example.todo.todos
import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import kotlinx.coroutines.flow.map
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitFormData
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter
import java.net.URI

fun main(args: Array<String>) {
    repeat(10) { todos.add("Todo <${it + 1}> & Kotlin") }
    SpringApplication(TodoApplication::class.java)
        .apply { setDefaultProperties(mapOf("server.address" to "127.0.0.1")) }
        .run(*args)
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
class TodoApplication {
    @Bean
    fun routes() = coRouter {
        GET("/") { request ->
            val filter = request.filter()
            if (filter in FILTERS) page(filter) else ServerResponse.badRequest().buildAndAwait()
        }
        GET("/board") { request ->
            val filter = request.filter()
            if (filter in FILTERS) {
                fragment("board.kte", mapOf("snapshot" to todos.snapshot(filter)))
            } else {
                ServerResponse.badRequest().buildAndAwait()
            }
        }
        GET("/events") { request ->
            if (request.filter() in FILTERS) {
                ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM).bodyAndAwait(
                    todos.changes().map { ServerSentEvent.builder("changed").event("todo-change").build() },
                )
            } else {
                ServerResponse.badRequest().buildAndAwait()
            }
        }
        POST("/woge-actions/add-todo") { add(it) }
    }
}

private suspend fun add(request: ServerRequest): ServerResponse {
    val uri = request.uri()
    val origin = request.headers().header(HttpHeaders.ORIGIN).singleOrNull()
    if (origin != "${uri.scheme}://${uri.rawAuthority}") return ServerResponse.status(403).buildAndAwait()
    if (request.headers().contentType().orElse(null)?.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED) != true) {
        return ServerResponse.status(415).buildAndAwait()
    }
    val fields = request.awaitFormData()
    if (fields["filter"].orEmpty().size > 1 || fields["title"].orEmpty().size > 1) {
        return ServerResponse.badRequest().buildAndAwait()
    }
    val filter = fields.getFirst("filter") ?: "all"
    if (filter !in FILTERS) return ServerResponse.badRequest().buildAndAwait()

    val title = fields.getFirst("title").orEmpty()
    val enhanced = request.headers().firstHeader("HX-Request") == "true"
    if (title.trim().length !in 1..MAX_TITLE_LENGTH) {
        return if (enhanced) {
            fragment("form.kte", mapOf("filter" to filter, "title" to title, "hasError" to true), HttpStatus.BAD_REQUEST)
        } else {
            page(filter, title, hasError = true)
        }
    }

    todos.add(title)
    return if (enhanced) {
        fragment("add-result.kte", mapOf("snapshot" to todos.snapshot(filter)))
    } else {
        ServerResponse.seeOther(URI.create("/?filter=$filter")).buildAndAwait()
    }
}

private val templates = TemplateEngine.createPrecompiled(ContentType.Html)

private fun ServerRequest.filter(): String = queryParam("filter").orElse("all")

private suspend fun page(filter: String, title: String = "", hasError: Boolean = false): ServerResponse =
    fragment(
        "page.kte",
        mapOf("snapshot" to todos.snapshot(filter), "title" to title, "hasError" to hasError),
        if (hasError) HttpStatus.BAD_REQUEST else HttpStatus.OK,
    )

private suspend fun fragment(name: String, model: Map<String, Any>, status: HttpStatus = HttpStatus.OK): ServerResponse {
    val output = StringOutput()
    templates.render(name, model, output)
    val response = ServerResponse.status(status)
        .header(HttpHeaders.VARY, "HX-Request")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            "Content-Security-Policy",
            "default-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'",
        )
        .contentType(MediaType.TEXT_HTML)
    if (status == HttpStatus.BAD_REQUEST && name == "form.kte") response.header("X-Todo-Validation", "1")
    return response.bodyValueAndAwait(output.toString())
}
