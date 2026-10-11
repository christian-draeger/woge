package dev.woge.example.project

import dev.woge.host.WogeRegion
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.caption
import dev.woge.html.dd
import dev.woge.html.div
import dev.woge.html.dl
import dev.woge.html.dt
import dev.woge.html.li
import dev.woge.html.p
import dev.woge.html.table
import dev.woge.html.tbody
import dev.woge.html.td
import dev.woge.html.th
import dev.woge.html.thead
import dev.woge.html.tr
import dev.woge.html.ul
import dev.woge.ui.UiId
import dev.woge.ui.dialogCloseButton
import dev.woge.ui.dialogLink
import dev.woge.ui.modalDialog
import java.util.UUID

@WogeRegion
internal fun HtmlWriter.boardTasks(rows: BoardTaskRows) {
    val titles = visibleBoardTitles(rows.titles, rows.input)
    boardCards(titles, rows)
    if (titles.isEmpty()) p { text("No matching tasks. Clear the filter or create a task below.") }
    div(attributes = {
        classes("operations-table table-scroll")
        attribute("role", "region")
        aria("label", "Task delivery table")
        attribute("tabindex", "0")
    }) {
        table {
            caption { text("Task delivery · scroll this table horizontally on small screens") }
            thead {
                tr {
                    listOf("Task", "Owner", "Priority", "Status", "Action").forEach { label ->
                        th(attributes = { attribute("scope", "col") }) { text(label) }
                    }
                }
            }
            tbody {
                titles.forEach { title ->
                    val details = rows.details[title] ?: BoardTaskDetails()
                    tr {
                        th(attributes = { attribute("scope", "row") }) { text(title) }
                        td { text(details.owner) }
                        td { text(details.priority) }
                        td { text(details.status) }
                        td { boardDetailsLink(title, rows.input.theme) }
                    }
                }
            }
        }
    }
    titles.forEach { title ->
        modalDialog(taskDialog(title), title = title, attributes = { classes("board-dialog") }) {
            val details = rows.details[title] ?: BoardTaskDetails()
            p { text("${details.owner} · ${details.priority} · ${details.status}") }
            a(attributes = {
                url("href", TaskBoardRoute.url(TaskBoardInput(theme = rows.input.theme, edit = title)))
            }) {
                text("Edit task")
            }
            dialogCloseButton { text("Close") }
        }
    }
    boardPagination(rows.titles, rows.input)
}

private fun HtmlWriter.boardCards(
    titles: List<String>,
    rows: BoardTaskRows,
) {
    ul(attributes = {
        attribute("id", "board-tasks")
        classes("task-cards")
    }) {
        titles.forEach { title ->
            li {
                text(title)
                val details = rows.details[title] ?: BoardTaskDetails()
                dl {
                    dt { text("Owner") }
                    dd { text(details.owner) }
                    dt { text("Priority") }
                    dd { text(details.priority) }
                    dt { text("Status") }
                    dd { text(details.status) }
                }
                boardDetailsLink(title, rows.input.theme)
            }
        }
    }
}

private fun HtmlWriter.boardDetailsLink(
    title: String,
    theme: BoardTheme?,
) {
    dialogLink(
        taskDialog(title),
        TaskBoardRoute.url(TaskBoardInput(theme = theme, edit = title)),
        attributes = { aria("label", "Details for $title") },
    ) { text("Details") }
}

private fun taskDialog(title: String): UiId = UiId("task-${UUID.nameUUIDFromBytes(title.toByteArray(Charsets.UTF_8))}")
