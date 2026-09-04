package dev.woge.html

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HtmlWriterTest {
    @Test
    fun `text and quoted attributes use separate escaping contexts`() {
        val html =
            renderHtml {
                element(
                    "p",
                    attributes = {
                        attribute("title", "5 < 7 & \"yes\" 'ok'\r")
                    },
                ) {
                    text("\u0000<script>alert('x')</script> & 😀")
                }
            }

        assertEquals(
            "<p title=\"5 &lt; 7 &amp; &quot;yes&quot; &#39;ok&#39;&#13;\">" +
                "�&lt;script&gt;alert('x')&lt;/script&gt; &amp; 😀</p>",
            html,
        )
    }

    @Test
    fun `invalid UTF-16 code units become replacement characters`() {
        val html =
            renderHtml {
                element("p", attributes = { attribute("title", "high=\uD800 low=\uDC00") }) {
                    text("a\uD800b\uDC00c")
                }
            }

        assertEquals("<p title=\"high=� low=�\">a�b�c</p>", html)
    }

    @Test
    fun `boolean attributes use presence rather than string truthiness`() {
        assertEquals(
            "<input disabled>",
            renderHtml { voidElement("input", attributes = { boolean("disabled", present = true) }) },
        )
        assertEquals(
            "<input>",
            renderHtml { voidElement("input", attributes = { boolean("disabled", present = false) }) },
        )
    }

    @Test
    fun `classes styles data aria and enumerated values preserve source order`() {
        val html =
            renderHtml {
                element(
                    "woge-card",
                    attributes = {
                        attribute("id", "card-7")
                        classes("task-card", "grid gap-3", "md:grid-cols-[1fr_auto]")
                        styles("--accent: oklch(62% 0.2 250);", "container-type: inline-size;")
                        data("state", "pending")
                        aria("busy", "true")
                        attribute("contenteditable", "plaintext-only")
                    },
                )
            }

        assertEquals(
            "<woge-card id=\"card-7\" class=\"task-card grid gap-3 md:grid-cols-[1fr_auto]\" " +
                "style=\"--accent: oklch(62% 0.2 250); container-type: inline-size;\" " +
                "data-state=\"pending\" aria-busy=\"true\" contenteditable=\"plaintext-only\"></woge-card>",
            html,
        )
    }

    @Test
    fun `multiple class contributors compose with an existing ordinary class attribute`() {
        val html =
            renderHtml {
                element(
                    "div",
                    attributes = {
                        attribute("class", "application-owned")
                        classes("woge-region", "is-loading")
                    },
                )
            }

        assertEquals("<div class=\"application-owned woge-region is-loading\"></div>", html)
    }

    @Test
    fun `invalid or duplicate syntax fails before the start tag is written`() {
        val chunks = mutableListOf<String>()
        val writer = HtmlWriter(HtmlSink(chunks::add))

        assertThrows(IllegalArgumentException::class.java) {
            writer.element("div", attributes = { attribute("x\" onclick", "bad") })
        }
        assertTrue(chunks.isEmpty())

        assertThrows(IllegalArgumentException::class.java) {
            writer.element(
                "div",
                attributes = {
                    attribute("id", "first")
                    attribute("ID", "second")
                },
            )
        }
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `validated application and external URLs remain ordinary quoted HTML`() {
        val html =
            renderHtml {
                element("a", attributes = { url("href", applicationUrl("/search?q=Kotlin&sort=new")) }) {
                    text("Search")
                }
                voidElement("img", attributes = { url("src", externalUrl("https://cdn.example/image.svg")) })
                element("a", attributes = { url("href", externalUrl("mailto:web@example.com")) }) {
                    text("Email")
                }
            }

        assertEquals(
            "<a href=\"/search?q=Kotlin&amp;sort=new\">Search</a>" +
                "<img src=\"https://cdn.example/image.svg\">" +
                "<a href=\"mailto:web@example.com\">Email</a>",
            html,
        )
    }

    @Test
    fun `URL factories reject active ambiguous and review-hostile values`() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "https://example.com",
            "//example.com/path",
            "\\\\example.com\\path",
            "/line\nbreak",
            "/malformed%escape",
            "/visual\u202Espoof",
            "/unpaired\uD800",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { applicationUrl(value) }
        }

        listOf(
            "javascript:alert(1)",
            "data:text/html,hello",
            "ftp://example.com/file",
            "https://user:password@example.com/",
            "https://example.com/line\nbreak",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { externalUrl(value) }
        }
    }

    @Test
    fun `ordinary attributes cannot silently enter active browser contexts`() {
        assertThrows(IllegalArgumentException::class.java) {
            renderHtml { element("a", attributes = { attribute("href", "javascript:alert(1)") }) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            renderHtml { voidElement("input", attributes = { attribute("disabled", "false") }) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            renderHtml { element("button", attributes = { attribute("onclick", "alert(1)") }) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            renderHtml { element("woge-frame", attributes = { attribute("srcdoc", "<script>x()</script>") }) }
        }
    }

    @OptIn(UnsafeWogeHtmlApi::class)
    @Test
    fun `unsafe HTML and URL paths remain explicit and still quote their outer attribute`() {
        val html =
            renderHtml {
                raw(unsafeHtml("<strong>audited</strong>"))
                element("a", attributes = { unsafeUrl("href", unsafeHtmlUrl("javascript:audited()")) }) {
                    text("Audited action")
                }
                voidElement(
                    "img",
                    attributes = { unsafeUrl("srcset", unsafeHtmlUrl("small.png 1x, large.png 2x")) },
                )
                element(
                    "woge-frame",
                    attributes = { srcdoc(unsafeHtml("<p title=\"inner\">Audited</p>")) },
                )
            }

        assertEquals(
            "<strong>audited</strong>" +
                "<a href=\"javascript:audited()\">Audited action</a>" +
                "<img srcset=\"small.png 1x, large.png 2x\">" +
                "<woge-frame srcdoc=\"&lt;p title=&quot;inner&quot;&gt;Audited&lt;/p&gt;\"></woge-frame>",
            html,
        )

        assertThrows(IllegalArgumentException::class.java) {
            unsafeHtmlUrl("small.png 1x,\nlarge.png 2x")
        }
    }

    @Test
    fun `known void and raw-text elements require their correct context`() {
        assertThrows(IllegalArgumentException::class.java) { renderHtml { element("img") } }
        assertThrows(IllegalArgumentException::class.java) { renderHtml { element("script") } }
    }

    @Test
    fun `validated URL values support future URL attribute names`() {
        assertEquals(
            "<woge-link future-url=\"/safe\"></woge-link>",
            renderHtml {
                element("woge-link", attributes = { url("future-url", applicationUrl("/safe")) })
            },
        )
    }
}
