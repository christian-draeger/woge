package dev.woge.html

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class HtmlSinksTest {
    @Test
    fun `buffered sink produces stable snapshots`() {
        val sink = BufferedHtmlSink(initialCapacity = 1)

        writeHtml(sink) {
            element("p", attributes = { attribute("lang", "en") }) {
                text("Hello & goodbye")
            }
        }

        assertEquals("<p lang=\"en\">Hello &amp; goodbye</p>", sink.content())
        assertEquals(sink.content().length, sink.length)
    }

    @Test
    fun `streaming sink emits before rendering completes`() {
        val chunks = mutableListOf<String>()
        var observedDuringRender = false

        streamHtml(HtmlSink(chunks::add), maxChunkChars = 8) {
            element("main") {
                text("first")
                observedDuringRender = chunks.isNotEmpty()
                text("-second")
            }
        }

        assertTrue(observedDuringRender)
        assertEquals("<main>first-second</main>", chunks.joinToString(separator = ""))
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 8 })
    }

    @Test
    fun `large writes remain bounded and never split a surrogate pair`() {
        val chunks = mutableListOf<String>()
        val content = "ab😀cd".repeat(100)

        streamHtml(HtmlSink(chunks::add), maxChunkChars = 3) {
            element("p") { text(content) }
        }

        assertEquals("<p>$content</p>", chunks.joinToString(separator = ""))
        assertTrue(chunks.all { it.length <= 4 })
        assertFalse(chunks.any { it.last().isHighSurrogate() })
        assertFalse(chunks.any { it.first().isLowSurrogate() })
    }

    @Test
    fun `downstream failure propagates unchanged and makes the sink terminal`() {
        val failure = IOException("client disconnected")
        val sink =
            StreamingHtmlSink(
                downstream = HtmlSink { throw failure },
                maxChunkChars = 1,
            )

        assertSame(failure, assertThrows(IOException::class.java) { sink.write("content") })
        assertSame(failure, assertThrows(IOException::class.java) { sink.write("more") })
        assertSame(failure, assertThrows(IOException::class.java) { sink.flush() })
    }

    @Test
    fun `cancellation propagates unchanged`() {
        val cancellation = CancellationException("request cancelled")

        val thrown =
            assertThrows(CancellationException::class.java) {
                streamHtml(HtmlSink { throw cancellation }, maxChunkChars = 1) {
                    element("p") { text("never completed") }
                }
            }

        assertSame(cancellation, thrown)
    }

    @Test
    fun `render failure does not flush trailing buffered content`() {
        val chunks = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            streamHtml(HtmlSink(chunks::add), maxChunkChars = 8) {
                element("main") {
                    text("first")
                    error("render failed")
                }
            }
        }

        assertEquals(listOf("<main>fi"), chunks)
    }
}
