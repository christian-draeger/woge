package dev.woge.html

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GeneratedHtmlElementsTest {
    @Test
    fun `generated wrappers cover modern normal void and keyword element names`() {
        val html =
            renderHtml {
                search {
                    hgroup {
                        h4 { text("Results") }
                        p { text("One match") }
                    }
                    selectedcontent()
                    `object` { `var` { text("answer") } }
                    br { classes("clear-both") }
                }
            }

        assertEquals(
            "<search><hgroup><h4>Results</h4><p>One match</p></hgroup>" +
                "<selectedcontent></selectedcontent><object><var>answer</var></object>" +
                "<br class=\"clear-both\"></search>",
            html,
        )
    }

    @Test
    fun `escapable raw text wrappers escape strings and never interpret nested markup`() {
        assertEquals(
            "<title>Woge &amp; &lt;web&gt;</title><textarea rows=\"3\">&lt;strong&gt;plain&lt;/strong&gt;</textarea>",
            renderHtml {
                title("Woge & <web>")
                textarea("<strong>plain</strong>") { attribute("rows", "3") }
            },
        )
    }

    @Test
    fun `active raw text wrappers expose attributes but not ordinary source strings`() {
        assertEquals(
            "<script src=\"/assets/app.js\"></script><iframe src=\"/preview\"></iframe>",
            renderHtml {
                script { url("src", applicationUrl("/assets/app.js")) }
                iframe { url("src", applicationUrl("/preview")) }
            },
        )
    }

    @Test
    fun `generic normal and void escape hatches remain open to platform additions`() {
        assertEquals(
            "<future-card>content</future-card><future-void data-state=\"ready\">",
            renderHtml {
                element("future-card") { text("content") }
                voidElement("future-void") { data("state", "ready") }
            },
        )
    }

    @Test
    fun `context-specific standard elements cannot bypass generated safety boundaries`() {
        listOf("iframe", "script", "style", "textarea", "title").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { renderHtml { element(name) } }
        }
    }
}
