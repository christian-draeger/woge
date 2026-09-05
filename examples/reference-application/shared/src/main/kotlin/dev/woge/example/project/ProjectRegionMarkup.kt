package dev.woge.example.project

import dev.woge.host.DeferredRegionFailure
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.caption
import dev.woge.html.dd
import dev.woge.html.div
import dev.woge.html.dl
import dev.woge.html.dt
import dev.woge.html.h2
import dev.woge.html.li
import dev.woge.html.ol
import dev.woge.html.p
import dev.woge.html.section
import dev.woge.html.table
import dev.woge.html.tbody
import dev.woge.html.td
import dev.woge.html.th
import dev.woge.html.thead
import dev.woge.html.time
import dev.woge.html.tr

internal fun HtmlWriter.renderCompleteProject(project: ProjectSnapshot) {
    section(attributes = {
        classes("project-region")
        aria("labelledby", "summary-heading")
    }) {
        renderSummary(project)
    }
    section(attributes = {
        classes("project-region")
        aria("labelledby", "tasks-heading")
    }) {
        renderTasks(project)
    }
    section(attributes = {
        classes("project-region")
        aria("labelledby", "activity-heading")
    }) {
        renderActivity(project)
    }
}

internal fun HtmlWriter.renderSummary(project: ProjectSnapshot) {
    h2(attributes = { attribute("id", "summary-heading") }) { text("Project summary") }
    dl(attributes = { classes("summary-grid") }) {
        metric("Open tasks", project.tasks.count { it.status != "Complete" })
        metric("Completed tasks", project.tasks.count { it.status == "Complete" })
        metric("Recent events", project.activity.size)
    }
}

internal fun HtmlWriter.renderTasks(project: ProjectSnapshot) {
    h2(attributes = { attribute("id", "tasks-heading") }) { text("Tasks") }
    table {
        caption { text("Current tasks for ${project.name}") }
        thead {
            tr {
                tableHeading("Task")
                tableHeading("Owner")
                tableHeading("Status")
            }
        }
        tbody {
            project.tasks.forEach { task ->
                tr {
                    th(attributes = { attribute("scope", "row") }) { text(task.title) }
                    td { text(task.owner) }
                    td { text(task.status) }
                }
            }
        }
    }
}

internal fun HtmlWriter.renderActivity(project: ProjectSnapshot) {
    h2(attributes = { attribute("id", "activity-heading") }) { text("Recent activity") }
    ol(attributes = { classes("activity-list") }) {
        project.activity.forEach { event ->
            li {
                text(event.description)
                text(" · ")
                time(attributes = { attribute("datetime", event.date) }) { text(event.date) }
            }
        }
    }
}

internal fun HtmlWriter.renderRegionLoading(
    region: String,
    title: String,
) {
    h2(attributes = { attribute("id", "$region-heading") }) { text(title) }
    p { text("Loading from the server…") }
}

internal fun HtmlWriter.renderRegionFailure(
    project: ProjectSnapshot,
    region: String,
    title: String,
    failure: DeferredRegionFailure,
) {
    h2(attributes = { attribute("id", "$region-heading") }) { text(title) }
    p { text("This region ${failureLabel(failure)}. ") }
    a(attributes = { url("href", applicationUrl("/projects/${project.slug}?view=complete")) }) {
        text("Load the complete page")
    }
}

private fun HtmlWriter.metric(
    label: String,
    value: Int,
) {
    div {
        dt { text(label) }
        dd { text(value.toString()) }
    }
}

private fun HtmlWriter.tableHeading(label: String) {
    th(attributes = { attribute("scope", "col") }) { text(label) }
}
