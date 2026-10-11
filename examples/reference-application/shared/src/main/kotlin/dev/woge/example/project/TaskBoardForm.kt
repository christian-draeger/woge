package dev.woge.example.project

import dev.woge.host.WogeRegion
import dev.woge.host.actionForm
import dev.woge.html.HtmlWriter
import dev.woge.html.button
import dev.woge.html.input
import dev.woge.html.label

internal fun HtmlWriter.boardForm() {
    actionForm(AddBoardTaskAction, attributes = {
        attribute("id", "board-form")
        data("woge-action", "")
        data("woge-status", "board-status")
        data("woge-alert", "board-alert")
        data("woge-failure-message", "Task was not added. Reload the board before trying again.")
    }) {
        label(attributes = { attribute("for", "task-title") }) { text("Task title") }
        input(attributes = {
            attribute("id", "task-title")
            attribute("name", "title")
            attribute("maxlength", "120")
            boolean("required")
        })
        button { text("Add task") }
    }
}

@WogeRegion
internal fun HtmlWriter.boardState(snapshot: TaskBoardSnapshot) {
    hidden("epoch", snapshot.page.epoch.value)
    hidden("version", snapshot.version.toString())
    hidden("revision", snapshot.revision.toString())
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
