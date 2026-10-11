package dev.woge.example.project

import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.button
import dev.woge.html.form
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.nav
import dev.woge.html.p
import dev.woge.ui.disclosure

internal fun HtmlWriter.boardNavigation(input: TaskBoardInput) {
    nav(attributes = {
        classes("operations-nav flex flex-wrap gap-4")
        aria("label", "Primary")
    }) {
        a(attributes = { url("href", ProjectPageRoute.url(ProjectPageInput("woge"))) }) { text("Northstar · Projects") }
        disclosure(summary = { text("Navigate") }) {
            a(attributes = { url("href", TaskBoardRoute.url(TaskBoardInput(theme = input.theme))) }) {
                text("Task board")
            }
            a(attributes = { url("href", applicationUrl("/notes")) }) { text("Notes workspace") }
        }
    }
}

internal fun HtmlWriter.boardThemeLinks(input: TaskBoardInput) {
    nav(attributes = {
        classes("theme-switch")
        aria("label", "Theme")
    }) {
        a(attributes = { url("href", TaskBoardRoute.url(input.copy(theme = null, edit = null))) }) { text("Plain CSS") }
        a(attributes = { url("href", TaskBoardRoute.url(input.copy(theme = BoardTheme.TAILWIND, edit = null))) }) {
            text("Northstar Tailwind")
        }
    }
}

internal fun HtmlWriter.boardFilters(input: TaskBoardInput) {
    form(attributes = {
        url("action", TaskBoardRoute.url(TaskBoardInput()))
        attribute("method", "get")
        classes("board-filter flex flex-wrap gap-4")
    }) {
        if (input.theme != null) {
            input(attributes = {
                attribute("type", "hidden")
                attribute("name", "theme")
                attribute("value", "tailwind")
            })
        }
        label(attributes = { attribute("for", "board-query") }) { text("Filter tasks") }
        input(attributes = {
            attribute("id", "board-query")
            attribute("name", "query")
            attribute("value", input.query.orEmpty())
            attribute("maxlength", "120")
        })
        button { text("Apply filter") }
        a(attributes = { url("href", TaskBoardRoute.url(TaskBoardInput(theme = input.theme))) }) {
            text("Clear filters")
        }
    }
    a(attributes = {
        url("href", TaskBoardRoute.url(input.copy(descending = input.descending != true, page = 1)))
    }) {
        text(if (input.descending == true) "Sort title A–Z" else "Sort title Z–A")
    }
}

internal fun HtmlWriter.boardPagination(
    titles: List<String>,
    input: TaskBoardInput,
) {
    val currentPage = input.page ?: 1
    val count = titles.count { it.contains(input.query.orEmpty(), ignoreCase = true) }
    val pages = ((count + BOARD_PAGE_SIZE - 1) / BOARD_PAGE_SIZE).coerceAtLeast(1)
    nav(attributes = {
        classes("board-paging")
        aria("label", "Task pages")
    }) {
        if (currentPage > 1) {
            a(attributes = { url("href", TaskBoardRoute.url(input.copy(page = currentPage - 1))) }) {
                text("Previous tasks")
            }
        }
        p { text("Page $currentPage of $pages · $count matching tasks") }
        if (currentPage < pages) {
            a(attributes = {
                url("href", TaskBoardRoute.url(input.copy(page = currentPage + 1)))
            }) {
                text("Next tasks")
            }
        }
    }
}
