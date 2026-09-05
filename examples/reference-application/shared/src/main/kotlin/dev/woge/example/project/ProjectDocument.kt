package dev.woge.example.project

import dev.woge.host.regionPlaceholder
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.footer
import dev.woge.html.form
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.header
import dev.woge.html.html
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.nav
import dev.woge.html.noscript
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.title

internal fun HtmlWriter.renderProjectDocument(
    project: ProjectSnapshot,
    view: ProjectPageView,
) {
    doctype()
    html(attributes = { attribute("lang", "en") }) {
        renderHead(project, view)
        renderBody(project, view)
    }
}

private fun HtmlWriter.renderHead(
    project: ProjectSnapshot,
    view: ProjectPageView,
) {
    head {
        meta { attribute("charset", "utf-8") }
        metadata("viewport", "width=device-width, initial-scale=1")
        metadata("description", "A web-native Woge project page")
        metadata("woge-page-epoch", projectEpoch(project).value)
        title { text("${project.name} project · Woge quickstart") }
        stylesheet(applicationUrl("/assets/application.css"))
        if (view == ProjectPageView.SHELL) {
            moduleScript(applicationUrl("/assets/application.js"))
        }
    }
}

private fun HtmlWriter.renderBody(
    project: ProjectSnapshot,
    view: ProjectPageView,
) {
    body(
        attributes = {
            classes("project-page")
            if (view == ProjectPageView.SHELL) {
                data("woge-patch-url", "/projects/${project.slug}/woge-patches")
            }
        },
    ) {
        a(attributes = {
            classes("skip-link")
            url("href", applicationUrl("#main-content"))
        }) {
            text("Skip to project content")
        }
        header(attributes = { classes("site-header") }) {
            nav(attributes = { aria("label", "Primary") }) {
                a(attributes = { url("href", applicationUrl("/projects/${project.slug}")) }) {
                    text("Projects")
                }
            }
        }
        main(attributes = {
            attribute("id", "main-content")
            classes("page-shell")
        }) {
            p(attributes = { classes("eyebrow") }) { text("Spring Boot WebFlux quickstart") }
            h1 { text(project.name) }
            p(attributes = { classes("lede") }) {
                text("The server sent this useful HTML shell first. Independent regions can follow in any order.")
            }
            if (view == ProjectPageView.COMPLETE) {
                renderCompleteProject(project)
            } else {
                renderFullNavigationFallback(project)
                renderDeferredProject(project)
            }
        }
        footer(attributes = { classes("site-footer") }) {
            text("Normal HTML, CSS, URLs and HTTP remain the application contract.")
        }
    }
}

private fun HtmlWriter.renderFullNavigationFallback(project: ProjectSnapshot) {
    val completeUrl = applicationUrl("/projects/${project.slug}?view=complete")
    noscript {
        p(attributes = { classes("notice") }) {
            text("JavaScript is off. ")
            a(attributes = { url("href", completeUrl) }) {
                text("Load all project data as one complete page.")
            }
        }
    }
    form(attributes = {
        url("action", applicationUrl("/projects/${project.slug}"))
        attribute("method", "get")
    }) {
        button(
            attributes = {
                attribute("type", "submit")
                attribute("name", "view")
                attribute("value", "complete")
            },
        ) {
            text("Load the complete page instead")
        }
    }
}

private fun HtmlWriter.renderDeferredProject(project: ProjectSnapshot) {
    deferredRegions(project).forEach { region ->
        regionPlaceholder(region, elementName = "section") {
            classes("project-region")
            aria("labelledby", "${region.target.region.value}-heading")
        }
    }
}
