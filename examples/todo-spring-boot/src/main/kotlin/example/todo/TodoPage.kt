package example.todo

import dev.woge.host.FormErrors
import dev.woge.host.FormValues
import dev.woge.host.LiveRegionPage
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.WogeRegion
import dev.woge.host.WogeRoute
import dev.woge.host.actionForm
import dev.woge.host.formErrorSummary
import dev.woge.host.formField
import dev.woge.host.formFieldErrors
import dev.woge.host.htmlPage
import dev.woge.host.liveRegion
import dev.woge.host.region
import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.nav
import dev.woge.html.noscript
import dev.woge.html.p
import dev.woge.html.span
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.html.ul

@WogeRoute("/")
data class TodosInput(
    val filter: String? = null,
) {
    val selected: String get() = filter ?: "all"
}

class TodosPage : PageUseCase<TodosInput> {
    override suspend fun open(request: PageRequest<TodosInput>): PageResult =
        if (request.input.selected in FILTERS) {
            todoDocument(request.context, request.input.selected)
        } else {
            dev.woge.host.failure(dev.woge.host.FailureCategory.BAD_REQUEST, request.context.correlationId)
        }
}

val liveTodos =
    LiveRegionPage(
        page = TodosPage(),
        region = TodoBoardRegion,
        load = { request -> todos.snapshot(request.input.selected) },
        changes = { todos.changes() },
        authorize = { request ->
            if (request.input.selected in FILTERS) {
                null
            } else {
                dev.woge.host.failure(dev.woge.host.FailureCategory.BAD_REQUEST, request.context.correlationId)
            }
        },
    )

fun todoDocument(
    context: RequestContext,
    filter: String,
    errors: FormErrors<AddTodo> = FormErrors(FormValues.EMPTY, emptyList()),
    status: ResponseStatus = ResponseStatus.OK,
): PageResult.Document =
    htmlPage(ResponseMetadata(status = status)) {
        val render = context.render.resume(context.render.epoch)
        doctype()
        html(attributes = { attribute("lang", "en") }) {
            head {
                meta { attribute("charset", "utf-8") }
                metadata("viewport", "width=device-width, initial-scale=1")
                render.metadata(this)
                title("Todos · Woge")
                stylesheet(applicationUrl("/todo.css"))
                moduleScript(applicationUrl("/assets/woge/index.js"))
            }
            body {
                a(attributes = { url("href", applicationUrl("#main")) }) { text("Skip to todos") }
                main(attributes = { attribute("id", "main") }) {
                    h1 { text("Todos") }
                    p {
                        text(
                            "HTML, forms and Kotlin. " +
                                "Updates arrive without a page reload when JavaScript is available.",
                        )
                    }
                    nav(attributes = { attribute("aria-label", "Filter todos") }) {
                        FILTERS.forEach { choice ->
                            a(attributes = {
                                url("href", TodosRoute.url(TodosInput(choice)))
                                if (choice == filter) attribute("aria-current", "page")
                            }) { text(choice.replaceFirstChar(Char::uppercase)) }
                        }
                    }
                    liveRegion(
                        render,
                        TodoBoardRegion,
                        todos.snapshot(filter),
                        TodosRoute.url(TodosInput(filter)),
                        attributes = { attribute("id", "todo-board") },
                    )
                    region(render.target(TodoStatusRegion), "", attributes = {
                        attribute("id", "todo-status")
                        attribute("role", "status")
                    })
                    p(attributes = {
                        attribute("id", "todo-alert")
                        attribute("role", "alert")
                    }) {}
                    region(render.target(TodoFormRegion), TodoFormView(filter, errors))
                    p { text("Public demo: todos are shared and stored in memory. Restarting clears them.") }
                    noscript {
                        p {
                            text(
                                "Forms and filters work without JavaScript. Reload to see changes from other tabs.",
                            )
                        }
                    }
                }
            }
        }
    }

data class TodoFormView(
    val filter: String,
    val errors: FormErrors<AddTodo>,
)

@WogeRegion
fun HtmlWriter.todoForm(view: TodoFormView) {
    val errors = view.errors
    formErrorSummary(errors, errorSummary, "Check your todo")
    formFieldErrors(titleField, errors)
    actionForm(AddTodoAction, attributes = {
        attribute("id", "add-todo")
        enhancement()
        data("woge-error-summary", errorSummary.value)
    }) {
        label(attributes = { attribute("for", titleField.id.value) }) { text("New todo") }
        input(attributes = {
            formField(titleField, errors)
            attribute("value", errors.value(titleField).orEmpty())
            attribute("maxlength", MAX_TITLE_LENGTH.toString())
            boolean("required")
        })
        hidden("filter", view.filter)
        button { text("Add todo") }
    }
}

@WogeRegion
fun HtmlWriter.todoBoard(snapshot: TodoSnapshot) {
    p(attributes = { attribute("id", "todo-count") }) {
        text("${snapshot.remaining} remaining · ${snapshot.todos.size} total")
    }
    if (snapshot.visible.isEmpty()) p { text("No todos in this view.") }
    ul {
        snapshot.visible.forEach { todo ->
            li(attributes = { attribute("data-todo-id", todo.id.toString()) }) {
                span {
                    text(todo.title)
                    if (todo.completed) text(" (completed)")
                }
                actionForm(ToggleTodoAction, attributes = { enhancement() }) {
                    hidden("id", todo.id.toString())
                    hidden("filter", snapshot.filter)
                    button(attributes = { data("woge-state-key", "toggle-${todo.id}") }) {
                        text(if (todo.completed) "Mark active" else "Complete")
                    }
                }
                actionForm(DeleteTodoAction, attributes = { enhancement() }) {
                    hidden("id", todo.id.toString())
                    hidden("filter", snapshot.filter)
                    button { text("Delete") }
                }
            }
        }
    }
}

@WogeRegion
fun HtmlWriter.todoStatus(message: String) {
    text(message)
}

private fun Attributes.enhancement() {
    data("woge-action", "")
    data("woge-status", "todo-status")
    data("woge-alert", "todo-alert")
    data("woge-failure-message", "Could not confirm the result. Reload before submitting again.")
}

private fun HtmlWriter.hidden(
    name: String,
    value: String,
) {
    input(attributes = {
        attribute("type", "hidden")
        attribute("name", name)
        attribute("value", value)
    })
}
