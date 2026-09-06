package dev.woge.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HtmlElementSourceGeneratorTest {
    @Test
    fun `generation is deterministic and preserves syntax categories`() {
        val first = HtmlElementSourceGenerator.generate(dataset)
        val second = HtmlElementSourceGenerator.generate(dataset)

        assertEquals(first, second)
        assertTrue(first.contains("fun HtmlWriter.html("))
        assertTrue(first.contains("voidElement(\"br\", attributes)"))
        assertTrue(first.contains("fun HtmlWriter.title(\n    value: String,"))
        assertTrue(first.contains("rawTextElement(\"script\", attributes, content = \"\")"))
        assertTrue(first.contains("specialized style(CssStylesheet, ...) wrapper is maintained in HeadAssets.kt"))
        assertTrue(first.contains("fun HtmlWriter.`object`("))
    }

    @Test
    fun `generation rejects duplicate element names`() {
        val duplicate = dataset + "\nhtml\tNORMAL\tHTMLHtmlElement\thttps://html.spec.whatwg.org/#html"

        val failure = assertThrows(IllegalArgumentException::class.java) {
            HtmlElementSourceGenerator.generate(duplicate)
        }

        assertTrue(failure.message.orEmpty().contains("Duplicate element names"))
    }

    private val dataset =
        """
        # Minimal representative fixture
        name	kind	interface	specification
        html	NORMAL	HTMLHtmlElement	https://html.spec.whatwg.org/#the-html-element
        title	ESCAPABLE_RAW_TEXT	HTMLTitleElement	https://html.spec.whatwg.org/#the-title-element
        br	VOID	HTMLBRElement	https://html.spec.whatwg.org/#the-br-element
        object	NORMAL	HTMLObjectElement	https://html.spec.whatwg.org/#the-object-element
        script	RAW_TEXT	HTMLScriptElement	https://html.spec.whatwg.org/#the-script-element
        style	RAW_TEXT	HTMLStyleElement	https://html.spec.whatwg.org/#the-style-element
        """.trimIndent()
}
