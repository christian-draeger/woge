package dev.woge.site

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

class GenerateSiteTest {
    @TempDir
    lateinit var workspace: Path

    private val sourcePath = "examples/test/src/Page.kt"
    private val revision = "a".repeat(40)
    private val output get() = workspace.resolve("out")
    private val static get() = workspace.resolve("site/static")

    @BeforeEach
    fun prepare() {
        workspace.resolve("site/content").createDirectories()
        static.createDirectories().resolve("site.css").writeText("main { margin: auto; }\n")
        workspace.resolve(sourcePath).apply {
            parent.createDirectories()
            writeText("val markup = \"<button>it & me</button>\"\n")
        }
        listOf("index", "start", "tasks", "hosts", "evidence").forEach { name ->
            page(name, "## Shared heading\n\nTry [a same-page link](#shared-heading).")
        }
    }

    @Test
    fun `renders navigable static HTML with fingerprinted local assets and escaped snippets`() {
        static.resolve("copy.js").writeText("console.info('fixture');\n")
        page("index", """
            ## Use **Kotlin**

            Try [Start](start.md#shared-heading).

            | Host | Setup |
            | ---- | ----- |
            | Ktor | From source |

            <!-- snippet: $sourcePath -->

            <script>unexpected()</script>
        """.trimIndent())
        build("/woge/")
        val document = Jsoup.parse(output.resolve("index.html").readText())
        assertEquals("index <main> · Woge", document.title())
        assertEquals("Summary & details", document.selectFirst("meta[name=description]")!!.attr("content"))
        assertEquals("0.1.0-SNAPSHOT", document.selectFirst("meta[name=woge-version]")!!.attr("content"))
        assertEquals(revision, document.selectFirst("meta[name=woge-revision]")!!.attr("content"))
        assertEquals("index", document.body().attr("data-page"))
        assertEquals(listOf("Overview", "Start", "Everyday tasks", "Hosts & deployment", "Demo & evidence"),
            document.select("nav a").eachText())
        assertEquals("/woge/index.html", document.selectFirst("nav a[aria-current=page]")!!.attr("href"))
        assertEquals("/woge/start.html#shared-heading", document.selectFirst("main a")!!.attr("href"))
        assertEquals("Use Kotlin", document.selectFirst("#use-kotlin")!!.text())
        assertEquals(listOf("Host", "Setup"), document.select("thead th[scope=col]").eachText())
        assertEquals(workspace.resolve(sourcePath).readText(), document.selectFirst("pre > code.language-kotlin")!!.wholeText())
        assertTrue(document.select("pre > code").any { "<script>unexpected()</script>" in it.wholeText() })
        assertEquals(1, document.select("script").size)

        val assets = output.resolve("assets").listDirectoryEntries().map { it.fileName.toString() }.toSet()
        assertEquals(2, assets.size)
        assertTrue(assets.any { it.matches(Regex("site-[a-f0-9]{64}\\.css")) })
        assertTrue(assets.any { it.matches(Regex("copy-[a-f0-9]{64}\\.js")) })
        assertEquals(assets, listOf(
            document.selectFirst("link[rel=stylesheet]")!!.attr("href"),
            document.selectFirst("script[type=module]")!!.attr("src"),
        ).map { it.removePrefix("/woge/assets/") }.toSet())
    }

    @Test
    fun `repeats the same output and retires old asset fingerprints`() {
        build()
        val original = output.resolve("index.html").readBytes()
        val assets = output.resolve("assets")
        val firstCss = assets.listDirectoryEntries().single()
        build()
        assertTrue(original.contentEquals(output.resolve("index.html").readBytes()))
        assertEquals(firstCss, assets.listDirectoryEntries().single())

        static.resolve("site.css").writeText("main { margin: 0; }\n")
        build()
        assertFalse(firstCss.exists())
        assertEquals(1, assets.listDirectoryEntries().size)
        assertFalse(original.contentEquals(output.resolve("index.html").readBytes()))
    }

    @Test
    fun `rejects unknown links before replacing a working site`() {
        build()
        val original = output.resolve("index.html").readText()
        page("index", "[Typo](start.md#missing)")
        val error = assertThrows(IllegalArgumentException::class.java) { build() }
        assertTrue(error.message!!.contains("index.md: unknown heading"))
        assertEquals(original, output.resolve("index.html").readText())
    }

    @Test
    fun `rejects project path and snippet traversal`() {
        assertThrows(IllegalArgumentException::class.java) { build("/woge/../") }
        page("index", "<!-- snippet: examples/test/src/../../../Private.kt -->")
        val error = assertThrows(IllegalArgumentException::class.java) { build() }
        assertTrue(error.message!!.contains("expected a Kotlin source file"))
        assertFalse(output.exists())
    }

    private fun build(base: String = "/") {
        generateSite(workspace, output, base, "0.1.0-SNAPSHOT", revision)
    }

    private fun page(name: String, body: String) {
        workspace.resolve("site/content/$name.md").writeText(
            "---\ntitle: $name <main>\ndescription: Summary & details\n---\n\n$body\n",
        )
    }
}
