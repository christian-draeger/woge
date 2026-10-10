package dev.woge.example.project

import dev.woge.host.WogeRegion
import dev.woge.host.actionForm
import dev.woge.host.region
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.h1
import dev.woge.html.h2
import dev.woge.html.head
import dev.woge.html.header
import dev.woge.html.html
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.li
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.nav
import dev.woge.html.p
import dev.woge.html.section
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.html.ul
import dev.woge.ui.LiveRegion
import dev.woge.ui.UiId
import dev.woge.ui.dialogCloseButton
import dev.woge.ui.dialogLink
import dev.woge.ui.disclosure
import dev.woge.ui.liveRegion
import dev.woge.ui.modalDialog
import dev.woge.ui.popoverButton
import dev.woge.ui.popoverPanel

private val boardHelpDialog = UiId("board-help")
private val liveUpdatesPopover = UiId("live-updates")

internal fun HtmlWriter.renderTaskBoard(
    snapshot: TaskBoardSnapshot,
    view: TaskBoardView? = null,
) {
    doctype()
    html(attributes = { attribute("lang", "en") }) {
        head {
            metadata("woge-page-epoch", snapshot.page.epoch.value)
            metadata("woge-live-url", BoardLiveRoute.url(BoardLiveInput(snapshot.page.epoch.value)).value)
            title("Task board · Woge")
            stylesheet(applicationUrl("/assets/application.css"))
            moduleScript(applicationUrl("/assets/application.js"))
        }
        body {
            header(attributes = { classes("board-header") }) {
                h1 { text("Task board") }
                boardTools()
            }
            if (view == TaskBoardView.HELP) {
                section(attributes = {
                    classes("board-help")
                    aria("labelledby", "board-help-page-title")
                }) {
                    h2(attributes = { attribute("id", "board-help-page-title") }) { text("Board help") }
                    boardHelp()
                }
            }
            region(BoardSummaryRegion.target(snapshot.page), snapshot.titles.size, elementName = "section")
            region(BoardTasksRegion.target(snapshot.page), snapshot.titles, elementName = "section")
            region(BoardStateRegion.target(snapshot.page), snapshot, elementName = "div")
            boardForm()
            disclosure(summary = { text("What happens when I add a task?") }, attributes = { classes("board-faq") }) {
                p {
                    text("With JavaScript, only the task list, the count and the status line change. ")
                    text("Without it, the browser posts the form and loads the board again.")
                }
            }
            region(BoardStatusRegion.target(snapshot.page), "", elementName = "p", attributes = {
                attribute("id", "board-status")
                liveRegion()
            })
            region(BoardActivityRegion.target(snapshot.page), 0L, elementName = "div", attributes = {
                attribute("id", "board-activity")
                liveRegion()
            })
            p(attributes = {
                attribute("id", "board-alert")
                liveRegion(LiveRegion.ALERT)
            }) {}
            modalDialog(boardHelpDialog, title = "Board help", attributes = { classes("board-dialog") }) {
                boardHelp()
                dialogCloseButton { text("Close") }
            }
        }
    }
}

private fun HtmlWriter.boardTools() {
    nav(attributes = {
        classes("board-tools")
        aria("label", "Board tools")
    }) {
        popoverButton(liveUpdatesPopover) { text("Live updates") }
        popoverPanel(liveUpdatesPopover, attributes = { classes("board-popover") }) {
            p { text("When another visitor adds a task, a notice appears below the form. Nothing moves on its own.") }
        }
        dialogLink(boardHelpDialog, TaskBoardRoute.url(TaskBoardInput(TaskBoardView.HELP))) { text("Board help") }
    }
}

private fun HtmlWriter.boardHelp() {
    ul {
        li { text("Type a title and press Enter to add a task.") }
        li { text("The status line below the form tells you whether it worked.") }
        li { text("Everything also works without JavaScript, as ordinary pages and forms.") }
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

/** Live notice; empty until another visitor adds a task. A plain link keeps the update in the user's hands. */
@WogeRegion
internal fun HtmlWriter.boardActivity(newTasks: Long) {
    if (newTasks > 0) {
        p {
            text(if (newTasks == 1L) "1 new task was added. " else "$newTasks new tasks were added. ")
            a(attributes = { url("href", TaskBoardRoute.url(TaskBoardInput())) }) { text("Show the latest board") }
        }
    }
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
