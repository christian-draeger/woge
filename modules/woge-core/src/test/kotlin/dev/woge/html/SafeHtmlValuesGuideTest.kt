package dev.woge.html

import dev.woge.css.declarations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SafeHtmlValuesGuideTest {
    @Test
    fun `project card example remains executable`() {
        val html =
            renderHtml {
                article(
                    attributes = {
                        classes("project-card", "grid gap-3", "md:grid-cols-[1fr_auto]")
                        data("project-id", "woge-7")
                        aria("busy", "false")
                        styles(declarations("container-type: inline-size;"))
                        styles(declarations("--accent: oklch(62% 0.2 250);"))
                    },
                ) {
                    h2 { text("Woge <preview>") }
                    a(attributes = { url("href", applicationUrl("/projects/woge-7")) }) {
                        text("Open & inspect")
                    }
                }
            }

        assertEquals(
            "<article class=\"project-card grid gap-3 md:grid-cols-[1fr_auto]\" " +
                "data-project-id=\"woge-7\" aria-busy=\"false\" " +
                "style=\"container-type: inline-size; --accent: oklch(62% 0.2 250);\">" +
                "<h2>Woge &lt;preview&gt;</h2>" +
                "<a href=\"/projects/woge-7\">Open &amp; inspect</a></article>",
            html,
        )
    }
}
