package dev.woge.spike.html

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class HtmlWriterComparisonTest {
    private val hostileCard = TaskCard(
        title = "Fix <script>alert(\"x\")</script> & docs",
        pending = true,
    )

    @Test
    public fun `purpose built writer preserves web attributes and escapes text`() {
        val html = renderHtml { renderWithWogeWriter(hostileCard, sink) }

        assertContains(html, "class=\"task-card grid gap-3 md:grid-cols-[minmax(0,1fr)_auto]\"")
        assertContains(html, "data-state=\"pending\"")
        assertContains(html, "aria-busy=\"true\"")
        assertContains(html, "style=\"--accent: oklch(62% 0.2 250); container-type: inline-size;\"")
        assertContains(html, "<woge-status kind=\"task\">Saving</woge-status>")
        assertContains(html, "<button disabled>Complete</button>")
        assertContains(html, "Fix &lt;script&gt;alert(\"x\")&lt;/script&gt; &amp; docs")
    }

    @Test
    public fun `kotlinx html supports the same output concepts`() {
        val html = renderWithKotlinxHtml(hostileCard)

        assertContains(html, "data-state=\"pending\"")
        assertContains(html, "aria-busy=\"true\"")
        assertContains(html, "md:grid-cols-[minmax(0,1fr)_auto]")
        assertContains(html, "<woge-status kind=\"task\">Saving</woge-status>")
        assertContains(html, "disabled")
        assertContains(html, "Fix &lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; docs")
        assertFalse(renderWithKotlinxHtml(hostileCard.copy(pending = false)).contains("disabled"))
    }

    @Test
    public fun `writer emits incrementally without building a DOM`() {
        val chunks = mutableListOf<String>()

        renderWithWogeWriter(TaskCard("Stream", pending = false), HtmlSink(chunks::add))

        assertTrue(chunks.size > 10)
        assertEquals("<article", chunks.first())
        assertEquals("</article>", chunks.last())
        assertContains(chunks.joinToString(""), "<h2>Stream</h2>")
    }

    @Test
    public fun `kotlinx html can target an appendable without a DOM`() {
        val appendable = RecordingAppendable()

        renderKotlinxIntoAppendable(TaskCard("Stream", pending = false), appendable)

        assertTrue(appendable.writeCount > 1)
        assertContains(appendable.toString(), "<article>")
        assertContains(appendable.toString(), "<h2>Stream</h2>")
    }

    @Test
    public fun `kotlinx html adapter can write through the Woge sink`() {
        val chunks = mutableListOf<String>()

        renderKotlinxIntoWogeSink(TaskCard("Interop", pending = false), HtmlSink(chunks::add))

        assertTrue(chunks.size > 1)
        assertContains(chunks.joinToString(""), "<h2>Interop</h2>")
    }

    @Test
    public fun `raw HTML requires an explicit unsafe value`() {
        val html = renderHtml {
            text("<strong>escaped</strong>")
            raw(unsafeHtml("<strong>explicit raw</strong>"))
        }

        assertEquals("&lt;strong&gt;escaped&lt;/strong&gt;<strong>explicit raw</strong>", html)
    }

    @Test
    public fun `element and attribute names cannot inject markup`() {
        assertFailsWith<IllegalArgumentException> {
            renderHtml { element("div onclick=alert(1)") }
        }
        assertFailsWith<IllegalArgumentException> {
            renderHtml { element("div", attributes = { attribute("x\" onmouseover", "bad") }) }
        }
    }
}

private class RecordingAppendable : Appendable {
    private val value = StringBuilder()
    var writeCount: Int = 0
        private set

    override fun append(csq: CharSequence?): Appendable = apply {
        writeCount += 1
        value.append(csq)
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): Appendable = apply {
        writeCount += 1
        value.append(csq, start, end)
    }

    override fun append(c: Char): Appendable = apply {
        writeCount += 1
        value.append(c)
    }

    override fun toString(): String = value.toString()
}
