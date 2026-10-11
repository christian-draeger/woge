package dev.woge.host

import dev.woge.css.declarations
import dev.woge.html.BufferedHtmlSink
import dev.woge.html.HtmlWriter
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class XssPathParityTest {
    @Test
    fun `native server documents and enhanced patches share HTML encoding for corpus values`() =
        runBlocking {
            readCorpus().filter { it.category in setOf("text", "attribute", "style") }.forEach { entry ->
                val content: HtmlWriter.() -> Unit = {
                    element(
                        "p",
                        attributes = {
                            when (entry.category) {
                                "attribute" -> attribute("title", entry.payload)
                                "style" -> styles(declarations(entry.payload))
                            }
                        },
                    ) {
                        if (entry.category == "text") text(entry.payload)
                    }
                }
                val native = BufferedHtmlSink()
                htmlPage(content = content).writeTo(native)
                val enhanced = patchHtml(content)
                assertEquals(native.content(), enhanced.value, entry.payload)
            }
        }
}

private data class CorpusEntry(
    val category: String,
    val payload: String,
)

private fun readCorpus(): List<CorpusEntry> =
    Files
        .readAllLines(Path.of(requireNotNull(System.getProperty("woge.xss.corpus"))))
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val (category, payload) = line.split('\t', limit = 2)
            CorpusEntry(category, payload)
        }
