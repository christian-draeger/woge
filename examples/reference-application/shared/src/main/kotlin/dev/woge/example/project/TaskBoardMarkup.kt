package dev.woge.example.project

import dev.woge.host.WogeRegion
import dev.woge.host.actionForm
import dev.woge.host.region
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.li
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.html.ul

internal fun HtmlWriter.renderTaskBoard(snapshot: TaskBoardSnapshot) {
    doctype()
    html(attributes = { attribute("lang", "en") }) {
        head {
            metadata("woge-page-epoch", snapshot.page.epoch.value)
            title("Task board · Woge")
            stylesheet(applicationUrl("/assets/application.css"))
            moduleScript(applicationUrl("/assets/application.js"))
        }
        body {
            h1 { text("Task board") }
            region(BoardSummaryRegion.target(snapshot.page), snapshot.titles.size, elementName = "section")
            region(BoardTasksRegion.target(snapshot.page), snapshot.titles, elementName = "section")
            region(BoardStateRegion.target(snapshot.page), snapshot, elementName = "div")
            boardForm()
            region(BoardStatusRegion.target(snapshot.page), "", elementName = "p", attributes = {
                attribute("id", "board-status")
                attribute("role", "status")
            })
            p(attributes = {
                attribute("id", "board-alert")
                attribute("role", "alert")
            }) {}
        }
    }
}

@WogeRegion
internal fun HtmlWriter.boardSummary(count: Int) {
    p(attributes = { attribute("id", "task-count") }) { text("$count tasks") }
}

@WogeRegion
internal fun HtmlWriter.boardTasks(titles: List<String>) {
    ul(attributes = { attribute("id", "board-tasks") }) {
        titles.forEach { title -> li { text(title) } }
    }
}

private fun HtmlWriter.boardForm() {
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

@WogeRegion
internal fun HtmlWriter.boardStatus(message: String) {
    text(message)
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
