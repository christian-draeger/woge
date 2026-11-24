package dev.woge.site

import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.renderHtml
import dev.woge.html.stylesheet
import dev.woge.html.title
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

fun main(arguments: Array<String>) {
    require(arguments.size == 5) { "Expected repository, output, base path, version and source revision" }
    generateSite(Path(arguments[0]), Path(arguments[1]), arguments[2], arguments[3], arguments[4])
}

private val navigation = linkedMapOf(
    "index" to "Overview",
    "start" to "Start",
    "tasks" to "Everyday tasks",
    "hosts" to "Hosts & deployment",
    "evidence" to "Demo & evidence",
)

private val contentParser: Parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()
private val baseSegment = Regex("[A-Za-z0-9][A-Za-z0-9._~-]*")
private val versionFormat = Regex("[A-Za-z0-9][A-Za-z0-9.+_-]{0,99}")
private val revisionFormat = Regex("[a-f0-9]{40}")

internal fun generateSite(repository: Path, output: Path, basePath: String, version: String, revision: String) {
    require(basePath == "/" || (basePath.startsWith("/") && basePath.endsWith("/") &&
        basePath.drop(1).dropLast(1).split("/").all(baseSegment::matches))) {
        "siteBasePath must be / or a local path with leading and trailing slash, such as /woge/"
    }
    require(versionFormat.matches(version)) { "Invalid Woge version" }
    require(revisionFormat.matches(revision)) { "siteRevision must be a full lowercase Git commit hash" }

    val pages = navigation.keys.associateWith { readPage(repository.resolve("site/content/$it.md")) }
    val markdown = Markdown(repository.toRealPath(), basePath, pages.mapValues { (_, page) -> page.anchors.values.toSet() })
    val static = repository.resolve("site/static")
    val css = readAsset(static.resolve("site.css"))
    val copy = static.resolve("copy.js").takeIf(Path::exists)?.let(::readAsset)
    val html = pages.mapValues { (name, page) -> renderPage(name, page, markdown, basePath, version, revision, css, copy) }

    // Complete parsing, reference validation and rendering before replacing generated output.
    val assets = output.resolve("assets").createDirectories()
    assets.resolve(css.name).writeBytes(css.bytes)
    copy?.let { assets.resolve(it.name).writeBytes(it.bytes) }
    html.forEach { (name, document) -> output.resolve("$name.html").writeText("$document\n") }
    val generatedNames = setOfNotNull(css.name, copy?.name)
    Files.list(assets).use { entries ->
        entries.filter { ownedAsset.matches(it.fileName.toString()) && it.fileName.toString() !in generatedNames }
            .forEach { Files.delete(it) }
    }
}

private data class Asset(val name: String, val bytes: ByteArray)
private val ownedAsset = Regex("(?:site-[a-f0-9]{64}\\.css|copy-[a-f0-9]{64}\\.js)")

private fun readAsset(path: Path): Asset {
    val bytes = path.readBytes()
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
    val name = path.fileName.toString()
    return Asset("${name.substringBeforeLast('.')}-$digest.${name.substringAfterLast('.')}", bytes)
}

private data class Page(
    val title: String,
    val description: String,
    val body: Node,
    val anchors: Map<Heading, String>,
)

private fun readPage(path: Path): Page {
    val lines = path.readText().lines()
    val end = lines.indices.drop(1).firstOrNull { lines[it] == "---" } ?: -1
    require(lines.firstOrNull() == "---" && end > 0) { "${path.fileName}: expected front matter" }
    val metadata = mutableMapOf<String, String>()
    lines.subList(1, end).forEach { line ->
        val key = line.substringBefore(": ")
        val value = line.substringAfter(": ", "").trim()
        require(key in setOf("title", "description") && value.isNotEmpty() && metadata.put(key, value) == null) {
            "${path.fileName}: expected unique title and description fields"
        }
    }
    val title = requireNotNull(metadata["title"]) { "${path.fileName}: missing title" }
    val description = requireNotNull(metadata["description"]) { "${path.fileName}: missing description" }
    val body = contentParser.parse(lines.drop(end + 1).joinToString("\n"))
    return Page(title, description, body, headingAnchors(body, path.fileName.toString()))
}

private fun renderPage(
    name: String,
    page: Page,
    markdown: Markdown,
    basePath: String,
    version: String,
    revision: String,
    css: Asset,
    copy: Asset?,
): String = renderHtml {
    doctype()
    element("html", { attribute("lang", "en") }) {
        element("head") {
            voidElement("meta") { attribute("charset", "utf-8") }
            metadata("viewport", "width=device-width, initial-scale=1")
            metadata("description", page.description)
            metadata("woge-version", version)
            metadata("woge-revision", revision)
            title("${page.title} · Woge")
            stylesheet(applicationUrl("${basePath}assets/${css.name}"))
            copy?.let { moduleScript(applicationUrl("${basePath}assets/${it.name}")) }
        }
        element("body", { attribute("data-page", name) }) {
            link("#main", "Skip to content") { attribute("class", "skip-link") }
            element("header", { attribute("class", "site-header") }) {
                link("${basePath}index.html", "Woge") { attribute("class", "site-brand") }
                element("nav", {
                    attribute("class", "site-nav")
                    attribute("aria-label", "Documentation")
                }) {
                    element("ul") {
                        navigation.forEach { (target, label) ->
                            element("li") {
                                link("$basePath$target.html", label) {
                                    if (target == name) attribute("aria-current", "page")
                                }
                            }
                        }
                    }
                }
            }
            element("main", {
                attribute("id", "main")
                attribute("class", "site-main")
                attribute("tabindex", "-1")
            }) {
                element("article") {
                    element("h1", { attribute("class", "page-title") }) { text(page.title) }
                    element("p", { attribute("class", "page-summary") }) { text(page.description) }
                    markdown.render(this, name, page.body, page.anchors)
                }
            }
            element("footer", { attribute("class", "site-footer") }) {
                text("Woge $version · Source ")
                element("code") { text(revision.take(12)) }
            }
        }
    }
}

private fun HtmlWriter.link(
    target: String,
    label: String,
    attributes: Attributes.() -> Unit = {},
) {
    element("a", {
        url("href", applicationUrl(target))
        attributes()
    }) { text(label) }
}
