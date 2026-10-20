package example.todo

import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.FormElementId
import dev.woge.host.FormError
import dev.woge.host.FormErrors
import dev.woge.host.FormValues
import dev.woge.host.PageResult
import dev.woge.host.RequestContext
import dev.woge.host.ResponseStatus
import dev.woge.host.WogeAction
import dev.woge.host.actionValidationUpdates
import dev.woge.host.failure
import kotlinx.serialization.Serializable

@Serializable
data class AddTodo(
    val title: String,
    val filter: String = "all",
)

@Serializable
data class ChangeTodo(
    val id: Long,
    val filter: String = "all",
)

val addForm = FormDecoder(AddTodo.serializer())
val changeForm = FormDecoder(ChangeTodo.serializer())
val titleField = addForm.field(AddTodo::title, FormElementId.of("todo-title"))
val errorSummary = FormElementId.of("todo-errors")

@WogeAction("add-todo")
suspend fun addTodo(
    command: AddTodo,
    context: RequestContext,
): PageResult {
    if (context.csrf != CsrfVerification.VERIFIED) return failure(FailureCategory.FORBIDDEN, context.correlationId)
    if (command.filter !in FILTERS) return failure(FailureCategory.BAD_REQUEST, context.correlationId)
    val title = command.title.trim()
    if (title.isEmpty() || title.length > MAX_TITLE_LENGTH) {
        val errors =
            FormErrors(
                FormValues.of(mapOf(titleField.name to listOf(command.title))),
                listOf(FormError(titleField, "Enter a title of 1 to $MAX_TITLE_LENGTH characters.")),
            )
        val prepared =
            context.render.updates(TodosRoute.url(TodosInput(command.filter))) {
                replace(TodoFormRegion, TodoFormView(command.filter, errors))
            }
        val patch = prepared.patches.single()
        return actionValidationUpdates(
            todoDocument(context, command.filter, errors, ResponseStatus.BAD_REQUEST),
            errorSummary,
            interaction = patch.interactionSequence,
        ) {
            replace(
                context.render.resume(context.render.epoch).target(TodoFormRegion),
                TodoFormView(command.filter, errors),
                patch.revision.base,
            )
        }
    }
    todos.add(title)
    return updated(context, command.filter, "Todo added.")
}

@WogeAction("toggle-todo")
suspend fun toggleTodo(
    command: ChangeTodo,
    context: RequestContext,
): PageResult = change(context, command, "Todo updated.") { todos.toggle(command.id) }

@WogeAction("delete-todo")
suspend fun deleteTodo(
    command: ChangeTodo,
    context: RequestContext,
): PageResult = change(context, command, "Todo deleted.") { todos.delete(command.id) }

private fun change(
    context: RequestContext,
    command: ChangeTodo,
    message: String,
    mutate: () -> Boolean,
): PageResult {
    if (context.csrf != CsrfVerification.VERIFIED) return failure(FailureCategory.FORBIDDEN, context.correlationId)
    if (command.filter !in FILTERS) return failure(FailureCategory.BAD_REQUEST, context.correlationId)
    if (!mutate()) return failure(FailureCategory.NOT_FOUND, context.correlationId)
    return updated(context, command.filter, message)
}

private fun updated(
    context: RequestContext,
    filter: String,
    message: String,
): PageResult =
    context.render.updates(TodosRoute.url(TodosInput(filter))) {
        replace(TodoBoardRegion, todos.snapshot(filter))
        replace(TodoStatusRegion, message)
        replace(TodoFormRegion, TodoFormView(filter, FormErrors<AddTodo>(FormValues.EMPTY, emptyList())))
    }
