package dev.woge.html

import dev.woge.css.declarations
import dev.woge.css.stylesheet
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class XssCorpusTest {
    @Test
    fun `shared corpus exercises escaped text ordinary attributes and style values`() {
        val corpus = readCorpus()
        assertTrue(corpus.any { it.category == "text" })
        assertTrue(corpus.any { it.category == "attribute" })
        assertTrue(corpus.any { it.category == "style" })

        corpus.filter { it.category == "text" }.forEach { entry ->
            val html = renderHtml { element("p") { text(entry.payload) } }
            assertFalse(html.contains("<script", ignoreCase = true), entry.payload)
            assertFalse(html.contains("<img", ignoreCase = true), entry.payload)
        }
        corpus.filter { it.category == "attribute" }.forEach { entry ->
            val html = renderHtml { element("p", attributes = { attribute("title", entry.payload) }) }
            assertTrue(html.startsWith("<p title=\""), entry.payload)
            assertFalse(html.contains(" title=\"\""), entry.payload)
            assertTrue(html.endsWith("</p>"), entry.payload)
        }
        corpus.filter { it.category == "text" }.forEach { entry ->
            val html =
                renderHtml {
                    title(entry.payload)
                    textarea(entry.payload)
                }
            assertFalse(html.contains("<script", ignoreCase = true), entry.payload)
            assertFalse(html.contains("<img", ignoreCase = true), entry.payload)
        }

        val style = corpus.single { it.category == "style" }.payload
        assertThrows(IllegalArgumentException::class.java) { renderHtml { style(stylesheet(style)) } }
        val html = renderHtml { element("p", attributes = { styles(declarations(style)) }) }
        assertFalse(html.contains("<script", ignoreCase = true), html)
        assertFalse(html.contains("</style", ignoreCase = true), html)
        assertTrue(html.contains("&lt;/style&gt;"), html)
    }

    @Test
    fun `shared corpus URL schemes and authorities are rejected by safe URL factories`() {
        readCorpus().filter { it.category == "url" }.forEach { entry ->
            assertThrows(IllegalArgumentException::class.java) {
                applicationUrl(entry.payload)
            }
            assertThrows(IllegalArgumentException::class.java) {
                externalUrl(entry.payload)
            }
        }
    }

    @Test
    fun `base elements require the application-owned document policy`() {
        listOf(
            { renderHtml { voidElement("base", attributes = { url("href", applicationUrl("/")) }) } },
            { renderHtml { element("base") } },
        ).forEach { render ->
            assertThrows(IllegalArgumentException::class.java) { render() }
        }
    }
}

internal data class XssCorpusEntry(
    val category: String,
    val payload: String,
)

internal fun readXssCorpus(): List<XssCorpusEntry> =
    Files
        .readAllLines(Path.of(requireNotNull(System.getProperty("woge.xss.corpus"))))
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val (category, payload) = line.split('\t', limit = 2)
            XssCorpusEntry(category, payload)
        }

private fun readCorpus(): List<XssCorpusEntry> = readXssCorpus()
