package datastarbaseline

import example.todo.FILTERS
import example.todo.MAX_TITLE_LENGTH
import example.todo.todos
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
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
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

fun main(args: Array<String>) {
    repeat(10) { todos.add("Todo <${it + 1}> & Kotlin") }
    SpringApplication(TodoApplication::class.java)
        .apply { setDefaultProperties(mapOf("server.address" to "127.0.0.1")) }
        .run(*args)
}

@SpringBootApplication(proxyBeanMethods = false)
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
                html(HttpStatus.OK, render("board", mapOf("snapshot" to todos.snapshot(filter))))
            } else {
                ServerResponse.badRequest().buildAndAwait()
            }
        }
        GET("/events") {
            streamResponse(HttpStatus.OK).bodyAndAwait(
                todos.changes().map { patchSignals("\"_version\":${todos.snapshot().todos.size}") },
            )
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
    val enhanced = request.headers().firstHeader("Datastar-Request") == "true"
    if (title.trim().length !in 1..MAX_TITLE_LENGTH) {
        if (!enhanced) return page(filter, title, hasError = true)
        // Datastar only reads patch streams from HTTP 200 responses.
        return streamResponse(HttpStatus.OK).bodyAndAwait(flowOf(
            patchSignals("\"_invalid\":true"),
            patchElements(render("errors", mapOf("hasError" to true, "focus" to true)), replace = true),
        ))
    }

    todos.add(title)
    if (!enhanced) return ServerResponse.seeOther(URI.create("/?filter=$filter")).buildAndAwait()
    return streamResponse(HttpStatus.OK).bodyAndAwait(flowOf(
        patchSignals("\"_invalid\":false,\"_version\":${todos.snapshot().todos.size}"),
        patchElements(render("errors", mapOf("hasError" to false, "focus" to false))),
        patchElements("""<p id="todo-status" role="status">Todo added.</p>"""),
    ))
}

private fun ServerRequest.filter(): String = queryParam("filter").orElse("all")

private val templates = TemplateEngine().apply {
    setTemplateResolver(ClassLoaderTemplateResolver().apply {
        prefix = "templates/"
        suffix = ".html"
        characterEncoding = Charsets.UTF_8.name()
    })
}
private val random = SecureRandom()

private suspend fun page(filter: String, title: String = "", hasError: Boolean = false): ServerResponse {
    val nonce = documentNonce()
    val model = mapOf("snapshot" to todos.snapshot(filter), "choices" to FILTERS, "draft" to title,
        "hasError" to hasError, "focus" to false, "nonce" to nonce)
    val status = if (hasError) HttpStatus.BAD_REQUEST else HttpStatus.OK
    return html(status, render("page", model), nonce)
}

internal fun documentNonce(): String = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))

internal fun render(template: String, model: Map<String, Any>): String =
    templates.process(template, Context(Locale.ROOT, model))

internal suspend fun html(status: HttpStatus, html: String, nonce: String? = null): ServerResponse {
    val response = ServerResponse.status(status)
        .header(HttpHeaders.VARY, "Datastar-Request")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .contentType(MediaType.TEXT_HTML)
    if (nonce != null) {
        response.header("Content-Security-Policy", "default-src 'self'; script-src 'self' 'nonce-$nonce'; " +
            "base-uri 'none'; form-action 'self'; frame-ancestors 'none'")
    }
    return response.bodyValueAndAwait(html)
}

internal fun streamResponse(status: HttpStatus): ServerResponse.BodyBuilder = ServerResponse.status(status)
    .header(HttpHeaders.VARY, "Datastar-Request")
    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-transform")
    .contentType(MediaType.TEXT_EVENT_STREAM)

internal fun patchElements(html: String, replace: Boolean = false): ServerSentEvent<String> {
    val elements = html.trim().lineSequence().joinToString("\n") { "elements $it" }
    return ServerSentEvent.builder(if (replace) "mode replace\n$elements" else elements)
        .event("datastar-patch-elements").build()
}

private fun patchSignals(fields: String): ServerSentEvent<String> =
    ServerSentEvent.builder("signals {$fields}").event("datastar-patch-signals").build()
