package dev.woge.examples.m1

import dev.woge.css.CssStylesheet
import dev.woge.css.declarations
import dev.woge.html.HtmlSink
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.article
import dev.woge.html.h2
import dev.woge.html.p
import dev.woge.html.renderHtml
import dev.woge.html.streamHtml
import dev.woge.css.stylesheet as cssStylesheet

internal val projectStyles: CssStylesheet =
    cssStylesheet(
        """
        @layer components {
          .project-card { container-type: inline-size; color: var(--project-accent); }
        }
        """.trimIndent(),
    )

internal fun renderProjectCard(projectName: String): String =
    renderHtml {
        article(
            attributes = {
                classes("project-card", "grid", "md:grid-cols-[1fr_auto]")
                data("project-id", "woge")
                aria("busy", "false")
                styles(declarations("--project-accent: oklch(62% 0.2 250);"))
            },
        ) {
            h2 { text(projectName) }
            p { text("Server-rendered HTML stays useful without JavaScript.") }
            a(attributes = { url("href", applicationUrl("/projects/woge")) }) {
                text("Open project")
            }
        }
    }

internal fun streamProjectCard(
    projectName: String,
    writeChunk: (String) -> Unit,
) {
    streamHtml(HtmlSink(writeChunk), maxChunkChars = 256) {
        article { h2 { text(projectName) } }
    }
}
