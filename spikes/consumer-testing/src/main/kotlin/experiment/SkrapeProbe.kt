package experiment

import it.skrape.core.htmlDocument
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun main() {
    val base = requireNotNull(System.getenv("WOGE_TODO_URL"))
    val client = HttpClient.newHttpClient()
    val initial = client.send(HttpRequest.newBuilder(URI(base)).GET().build(), HttpResponse.BodyHandlers.ofString())
    check(initial.statusCode() == 200)
    val doc = htmlDocument(initial.body())
    val form = doc.findFirst("form#add-todo")
    check(form.attribute("method").lowercase() == "post")
    check(form.attribute("action") == "/woge-actions/add-todo")
    val input = form.findFirst("input[name=title]")
    check(input.attributeKeys.contains("required")) // Presence workaround until upstream fix is released.
    check(doc.findFirst("label[for=todo-title]").text == "New todo")
    val validation =
        client.send(
            HttpRequest
                .newBuilder(URI(base + form.attribute("action")))
                .header("Origin", base)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("title=+++&filter=all"))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    check(validation.statusCode() == 400)
    val errors = htmlDocument(validation.body())
    check(errors.findFirst("#todo-errors a").attribute("href") == "#todo-title")
    check(errors.findFirst("input[name=title]").attribute("value") == "   ")
    check(errors.findFirst("input[name=title]").attribute("aria-invalid") == "true")
    println(
        "PASS skrapeit-html-parser 1.3.0-alpha.2: native form, label, required presence, HTTP 400, preserved value, linked errors",
    )
}
