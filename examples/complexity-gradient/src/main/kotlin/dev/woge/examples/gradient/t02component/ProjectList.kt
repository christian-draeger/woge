package dev.woge.examples.gradient.t02component

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.article
import dev.woge.html.body
import dev.woge.html.h1
import dev.woge.html.h2
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.title

public data class Project(
    val name: String,
    val summary: String,
)

private val projects =
    listOf(
        Project("Herb bed", "Basil, thyme and mint by the kitchen door."),
        Project("Compost corner", "Turn the pile every second week."),
    )

@WogeRoute("/projects")
public data object ProjectListInput

public class ProjectList : PageUseCase<ProjectListInput> {
    override suspend fun open(request: PageRequest<ProjectListInput>): PageResult =
        htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    title("Projects · Component")
                    stylesheet(applicationUrl("/assets/component/site.css"))
                }
                body {
                    main {
                        h1 { text("Garden projects") }
                        projects.forEach { projectCard(it) }
                    }
                }
            }
        }
}

/** A reusable component is an ordinary function that writes HTML. */
internal fun HtmlWriter.projectCard(project: Project) {
    article(attributes = { classes("project-card") }) {
        h2 { text(project.name) }
        p { text(project.summary) }
    }
}
