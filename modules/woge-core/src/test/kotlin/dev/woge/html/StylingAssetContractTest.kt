package dev.woge.html

import dev.woge.css.declarations
import dev.woge.css.stylesheet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class StylingAssetContractTest {
    @Test
    fun `modern and unknown CSS passes through without a property catalog`() {
        val source = requireNotNull(javaClass.getResource("/css/modern.css")).toURI().let(Path::of).readText()

        assertEquals(source, stylesheet(source).source)
    }

    @Test
    fun `plain CSS CSS-module names and Tailwind utilities compose as ordered web classes`() {
        val html =
            renderHtml {
                element(
                    "project-card",
                    attributes = {
                        classes(
                            "project-card",
                            "ProjectCard_root__a1b2c",
                            "grid gap-4 md:grid-cols-[1fr_auto]",
                        )
                        styles(declarations("--accent: oklch(68% 0.18 35);"))
                        styles(declarations("view-transition-name: project-card;"))
                        data("state", "ready")
                        aria("busy", "false")
                        attribute("part", "surface")
                    },
                )
            }

        assertEquals(
            "<project-card class=\"project-card ProjectCard_root__a1b2c grid gap-4 " +
                "md:grid-cols-[1fr_auto]\" style=\"--accent: oklch(68% 0.18 35); " +
                "view-transition-name: project-card;\" data-state=\"ready\" aria-busy=\"false\" " +
                "part=\"surface\"></project-card>",
            html,
        )
    }

    @Test
    fun `head assets keep standard HTML names and explicit security metadata`() {
        val nonce = cspNonce("c29tZS1yYW5kb20tbm9uY2U=")
        val integrity = subresourceIntegrity("sha384-YWJjMTIzNDU2Nzg5MA==")
        val css = stylesheet("@layer page { .notice { color: oklch(60% .2 30); } }")

        val html =
            renderHtml {
                element("head") {
                    metadata("description", "Woge & the web")
                    propertyMetadata("og:title", "Project overview")
                    stylesheet(applicationUrl("/assets/application.css?theme=dark&v=2"))
                    stylesheet(
                        externalUrl("https://cdn.example/theme.css"),
                        integrity = integrity,
                        crossOrigin = CrossOrigin.ANONYMOUS,
                    )
                    style(css, nonce = nonce)
                    preload(applicationUrl("/assets/project-card.css"), asType = "style", mimeType = "text/css")
                    assetLink("icon", applicationUrl("/favicon.svg")) { attribute("sizes", "any") }
                    moduleScript(applicationUrl("/assets/application.js"), nonce = nonce)
                }
            }

        assertTrue(html.contains("<meta name=\"description\" content=\"Woge &amp; the web\">"))
        assertTrue(html.contains("<meta property=\"og:title\" content=\"Project overview\">"))
        assertTrue(
            html.contains(
                "<link rel=\"stylesheet\" href=\"/assets/application.css?theme=dark&amp;v=2\">",
            ),
        )
        assertTrue(
            html.contains(
                "<link rel=\"stylesheet\" href=\"https://cdn.example/theme.css\" " +
                    "integrity=\"sha384-YWJjMTIzNDU2Nzg5MA==\" crossorigin=\"anonymous\">",
            ),
        )
        assertTrue(html.contains("<style nonce=\"c29tZS1yYW5kb20tbm9uY2U=\">${css.source}</style>"))
        assertTrue(
            html.contains("<link rel=\"preload\" href=\"/assets/project-card.css\" as=\"style\" type=\"text/css\">"),
        )
        assertTrue(html.contains("<link rel=\"icon\" href=\"/favicon.svg\" sizes=\"any\">"))
        assertTrue(
            html.contains(
                "<script type=\"module\" src=\"/assets/application.js\" " +
                    "nonce=\"c29tZS1yYW5kb20tbm9uY2U=\"></script>",
            ),
        )
    }

    @Test
    fun `CSS and active head contexts reject accidental string or raw-text crossings before output`() {
        val chunks = mutableListOf<String>()

        assertThrows(IllegalArgumentException::class.java) {
            writeHtml(HtmlSink(chunks::add)) {
                element("div", attributes = { attribute("style", "color: red") })
            }
        }
        assertTrue(chunks.isEmpty())

        assertThrows(IllegalArgumentException::class.java) {
            writeHtml(HtmlSink(chunks::add)) {
                style(stylesheet(".safe {} </StYlE><script>alert(1)</script>"))
            }
        }
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `nonce integrity and external integrity CORS requirements fail closed`() {
        listOf("", "spaces are not base64", "line\nbreak", "***").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { cspNonce(value) }
        }
        listOf("", "md5-YWJj", "sha384-***", "sha384-value extra").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { subresourceIntegrity(value) }
        }

        assertThrows(IllegalArgumentException::class.java) {
            renderHtml {
                moduleScript(
                    externalUrl("https://cdn.example/application.js"),
                    integrity = subresourceIntegrity("sha384-YWJj"),
                )
            }
        }
    }

    @Test
    fun `published CSS factories retain IntelliJ language injection metadata`() {
        val coreJar = requireNotNull(System.getProperty("woge.core.jar"))
        val javap = Path.of(System.getProperty("java.home"), "bin", "javap").toString()
        val process =
            ProcessBuilder(javap, "-v", "-classpath", coreJar, "dev.woge.css.CssValuesKt")
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()

        assertEquals(0, process.waitFor(), output)
        assertTrue(output.contains("org.intellij.lang.annotations.Language"), output)
        assertTrue(output.contains("CSS"), output)
        assertTrue(output.contains(".woge-declaration-list {"), output)
        assertTrue(output.contains("prefix"), output)
        assertTrue(output.contains("suffix"), output)
        assertFalse(output.contains("Tailwind"), output)
    }
}
