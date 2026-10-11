package dev.woge.example.project

import dev.woge.example.components.disclosureCard
import dev.woge.host.WogeRegion
import dev.woge.host.region
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.dd
import dev.woge.html.div
import dev.woge.html.dl
import dev.woge.html.dt
import dev.woge.html.h1
import dev.woge.html.h2
import dev.woge.html.head
import dev.woge.html.header
import dev.woge.html.html
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.meta
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
import dev.woge.ui.liveRegion
import dev.woge.ui.modalDialog
import dev.woge.ui.popoverButton
import dev.woge.ui.popoverPanel

private val boardHelpDialog = UiId("board-help")
private val liveUpdatesPopover = UiId("live-updates")

internal fun HtmlWriter.renderTaskBoard(
    snapshot: TaskBoardSnapshot,
    view: TaskBoardView? = null,
    input: TaskBoardInput = TaskBoardInput(),
) {
    doctype()
    html(attributes = { attribute("lang", "en") }) {
        head {
            meta { attribute("charset", "utf-8") }
            metadata("viewport", "width=device-width, initial-scale=1")
            metadata("woge-page-epoch", snapshot.page.epoch.value)
            metadata("woge-live-url", BoardLiveRoute.url(BoardLiveInput(snapshot.page.epoch.value)).value)
            title("Task board · Woge")
            stylesheet(applicationUrl("/assets/application.css"))
            stylesheet(applicationUrl("/assets/components/disclosure-card.css"))
            stylesheet(applicationUrl("/assets/operations.css"))
            if (input.theme == BoardTheme.TAILWIND) stylesheet(applicationUrl("/assets/operations-tailwind.css"))
            moduleScript(applicationUrl("/assets/application.js"))
        }
        body(attributes = {
            classes("task-board")
            data("theme", if (input.theme == BoardTheme.TAILWIND) "tailwind" else "css")
        }) {
            a(attributes = {
                classes("skip-link")
                url("href", applicationUrl("#board-main"))
            }) { text("Skip to board content") }
            boardNavigation(input)
            header(attributes = { classes("board-header site-header") }) {
                h1 { text("Task board") }
                boardTools(input.theme)
                boardThemeLinks(input)
            }
            boardContent(snapshot, view, input)
            modalDialog(boardHelpDialog, title = "Board help", attributes = { classes("board-dialog") }) {
                boardHelp()
                dialogCloseButton { text("Close") }
            }
        }
    }
}

private fun HtmlWriter.boardContent(
    snapshot: TaskBoardSnapshot,
    view: TaskBoardView?,
    input: TaskBoardInput,
) {
    main(attributes = {
        attribute("id", "board-main")
        attribute("tabindex", "-1")
        classes("page-shell")
    }) {
        if (view == TaskBoardView.HELP) {
            section(attributes = {
                classes("board-help")
                aria("labelledby", "board-help-page-title")
            }) {
                h2(attributes = { attribute("id", "board-help-page-title") }) { text("Board help") }
                boardHelp()
            }
        }
        p(attributes = { classes("eyebrow") }) { text("Northstar · Project operations") }
        p(attributes = { classes("lede") }) { text("Plan delivery, assign owners and track changes across the team.") }
        region(BoardSummaryRegion.target(snapshot.page), snapshot.titles.size, elementName = "section", attributes = {
            classes("project-region")
        })
        boardFilters(input)
        region(
            BoardTasksRegion.target(snapshot.page),
            BoardTaskRows(snapshot.titles, snapshot.details, input),
            elementName = "section",
        )
        region(BoardStateRegion.target(snapshot.page), snapshot, elementName = "div")
        if (input.edit != null) {
            section(attributes = { classes("notice") }) {
                h2 { text("Task details") }
                a(attributes = { url("href", TaskBoardRoute.url(TaskBoardInput(theme = input.theme))) }) {
                    text("Return to all tasks")
                }
            }
        }
        boardForm(snapshot, input)
        disclosureCard("What happens when I add a task?") {
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
        })
        p(attributes = {
            attribute("id", "board-alert")
            liveRegion(LiveRegion.ALERT)
        }) {}
    }
}

private fun HtmlWriter.boardTools(theme: BoardTheme?) {
    nav(attributes = {
        classes("board-tools")
        aria("label", "Board tools")
    }) {
        popoverButton(liveUpdatesPopover) { text("Live updates") }
        popoverPanel(liveUpdatesPopover, attributes = { classes("board-popover") }) {
            p {
                text(
                    "When another visitor changes a task, a notice appears below the form. Nothing moves on its own.",
                )
            }
        }
        dialogLink(boardHelpDialog, TaskBoardRoute.url(TaskBoardInput(TaskBoardView.HELP, theme = theme))) {
            text("Board help")
        }
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
    h2 { text("Delivery overview") }
    p(attributes = { attribute("id", "task-count") }) { text("$count tasks") }
    dl(attributes = { classes("summary-grid") }) {
        div {
            dt { text("Workspace") }
            dd { text("Northstar") }
        }
        div {
            dt { text("Ownership") }
            dd { text("Shared team") }
        }
        div {
            dt { text("Activity") }
            dd { text("Live notices") }
        }
    }
}

/** Live notice; empty until another visitor changes a task. A plain link keeps the update in the user's hands. */
@WogeRegion
internal fun HtmlWriter.boardActivity(newTasks: Long) {
    if (newTasks > 0) {
        p {
            text(if (newTasks == 1L) "1 task changed. " else "$newTasks tasks changed. ")
            a(attributes = { url("href", TaskBoardRoute.url(TaskBoardInput())) }) { text("Show the latest board") }
        }
    }
}

@WogeRegion
internal fun HtmlWriter.boardStatus(message: String) {
    text(message)
}
