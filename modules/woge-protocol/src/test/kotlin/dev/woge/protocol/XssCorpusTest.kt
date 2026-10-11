package dev.woge.protocol

import dev.woge.css.declarations
import dev.woge.html.applicationUrl
import dev.woge.html.externalUrl
import dev.woge.html.renderHtml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class XssCorpusTest {
    @Test
    fun `native and enhanced HTML DSL paths encode the same shared corpus values`() {
        readCorpus().filter { it.category in setOf("text", "attribute", "style") }.forEach { entry ->
            val render: dev.woge.html.HtmlWriter.() -> Unit = {
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
            val nativeHtml = renderHtml(render)
            val enhancedHtml = patchHtml(render).value
            assertEquals(nativeHtml, enhancedHtml, entry.payload)
        }
    }

    @Test
    fun `patch parser rejects every active payload in the shared corpus without echoing it`() {
        val payloads = readCorpus().filter { it.category == "patch" }
        assertTrue(payloads.isNotEmpty())
        payloads.forEachIndexed { index, entry ->
            val problem =
                assertThrows(PatchStreamException::class.java) {
                    PatchStreamV1.encode(listOf(xssExamplePatch(PatchHtml(entry.payload), index)))
                }
            assertEquals(PatchStreamErrorCode.ACTIVE_CONTENT, problem.code)
            assertFalse(problem.message.orEmpty().contains(entry.payload))
        }
    }

    @Test
    fun `unsafe URLs in the corpus remain blocked in patch URL attributes`() {
        readCorpus().filter { it.category == "url" }.forEach { entry ->
            val html = "<a href=\"${entry.payload}\">link</a>"
            val problem =
                assertThrows(PatchStreamException::class.java) {
                    PatchStreamV1.encode(listOf(xssExamplePatch(PatchHtml(html), 0)))
                }
            assertEquals(PatchStreamErrorCode.ACTIVE_CONTENT, problem.code)
            assertTrue(runCatching { applicationUrl(entry.payload) }.isFailure)
            assertTrue(runCatching { externalUrl(entry.payload) }.isFailure)
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

private fun xssExamplePatch(
    html: PatchHtml,
    sequence: Int,
): ReplacePatch =
    ReplacePatch(
        patchId = PatchId.of("patch-$sequence"),
        target = PatchTarget(PageEpoch.of("epoch-a"), RegionTargetId.of("summary-1")),
        interactionSequence = InteractionSequence.of(0),
        revision = TargetRevisionStep.after(TargetRevision.of(0)),
        html = html,
    )
