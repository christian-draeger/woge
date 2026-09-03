package woge.css

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CssAuthoringTest {
    @Test
    fun `stylesheet preserves modern CSS without a property catalog`() {
        val source = """
            @layer tokens, components;
            @layer components {
              .card {
                --accent: oklch(68% 0.18 35);
                margin-inline: 1rem;
                container-type: inline-size;
                view-transition-name: woge-card;

                & > h2 { color: var(--accent); }
              }
              @container (width >= 30rem) { .card { grid-template-columns: 1fr auto; } }
              @supports (color: oklch(50% 0.1 20)) { .card { color: var(--accent); } }
              @media (prefers-reduced-motion: reduce) { .card { animation: none; } }
            }
        """.trimIndent()

        assertEquals(source, stylesheet(source).value)
    }

    @Test
    fun `stylesheet interpolation and literal dollar escaping are ordinary Kotlin`() {
        val accent = "oklch(68% 0.18 35)"
        val source = stylesheet(
            """
                .card {
                  --accent: $accent;
                  --literal-dollar: "${'$'}";
                }
            """.trimIndent(),
        )

        assertTrue("--accent: oklch(68% 0.18 35)" in source.value)
        assertTrue("--literal-dollar: \"${'$'}\"" in source.value)
    }

    @Test
    fun `declaration lists preserve custom properties and escape the HTML attribute context`() {
        val css = declarations("--label: \"A&B\"; color: var(--accent);")

        assertEquals(
            "--label: &quot;A&amp;B&quot;; color: var(--accent);",
            styleAttribute(css),
        )
    }

    @Test
    fun `style blocks preserve valid source and reject raw-text breakout`() {
        assertEquals(
            "<style>.card { color: oklch(68% 0.18 35); }</style>",
            styleBlock(stylesheet(".card { color: oklch(68% 0.18 35); }")),
        )

        assertFailsWith<IllegalArgumentException> {
            styleBlock(stylesheet(".card { color: red; } </StYlE><script>alert(1)</script>"))
        }
    }

    @Test
    fun `CSS text rejects null without interpreting unknown syntax`() {
        assertFailsWith<IllegalArgumentException> { stylesheet(".card { content: '\u0000'; }") }
        assertEquals(".future { future-property: future-value(); }", stylesheet(".future { future-property: future-value(); }").value)
    }
}
