package dev.woge.example.project

import dev.woge.host.WogeRegion
import dev.woge.host.actionForm
import dev.woge.html.HtmlWriter
import dev.woge.html.button
import dev.woge.html.fieldset
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.legend
import dev.woge.html.option
import dev.woge.html.p
import dev.woge.html.select

internal fun HtmlWriter.boardForm(
    snapshot: TaskBoardSnapshot,
    input: TaskBoardInput,
) {
    actionForm(AddBoardTaskAction, attributes = {
        attribute("id", "board-form")
        data("woge-action", "")
        data("woge-status", "board-status")
        data("woge-alert", "board-alert")
        data("woge-failure-message", "Task was not added. Reload the board before trying again.")
    }) {
        val selected = input.edit?.takeIf { it in snapshot.titles }
        val details = snapshot.details[selected] ?: BoardTaskDetails()
        if (input.theme != null) hidden("theme", "tailwind")
        if (input.query != null) hidden("query", input.query)
        if (input.descending != null) hidden("descending", input.descending.toString())
        if (input.page != null) hidden("page", input.page.toString())
        fieldset {
            legend { text(if (selected == null) "Create a task" else "Edit task") }
            label(attributes = { attribute("for", "task-title") }) { text("Task title") }
            input(attributes = {
                attribute("id", "task-title")
                attribute("name", "title")
                attribute("maxlength", "120")
                boolean("required")
                attribute("value", selected.orEmpty())
            })
            label(attributes = { attribute("for", "task-owner") }) { text("Owner") }
            input(attributes = {
                attribute("id", "task-owner")
                attribute("name", "owner")
                attribute("maxlength", "60")
                attribute("value", details.owner)
                boolean("required")
            })
        }
        fieldset {
            legend { text("Delivery") }
            boardChoice("priority", "Priority", details.priority, listOf("Normal", "Urgent"))
            boardChoice("status", "Status", details.status, listOf("Open", "Done"))
        }
        p { text("Required: title and owner. Updates are checked against the board version; stale edits are refused.") }
        button { text(if (selected == null) "Add task" else "Save task") }
    }
}

@WogeRegion
internal fun HtmlWriter.boardState(snapshot: TaskBoardSnapshot) {
    hidden("epoch", snapshot.page.epoch.value)
    hidden("version", snapshot.version.toString())
    hidden("revision", snapshot.revision.toString())
    hidden("edit", snapshot.edit)
}

private fun HtmlWriter.hidden(
    name: String,
    value: String,
) {
    input(attributes = {
        attribute("type", "hidden")
        attribute("name", name)
        attribute("value", value)
        attribute("form", "board-form")
    })
}

private fun HtmlWriter.boardChoice(
    name: String,
    title: String,
    value: String,
    choices: List<String>,
) {
    label(attributes = { attribute("for", "task-$name") }) { text(title) }
    select(attributes = {
        attribute("id", "task-$name")
        attribute("name", name)
    }) {
        choices.forEach { choice ->
            option(attributes = {
                attribute("value", choice)
                boolean("selected", choice == value)
            }) { text(choice) }
        }
    }
}
