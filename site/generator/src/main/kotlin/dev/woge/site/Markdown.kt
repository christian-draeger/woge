package dev.woge.site

import dev.woge.html.HtmlUrl
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.externalUrl
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import java.net.URI
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.readText

private fun Node.children(): Sequence<Node> = generateSequence(firstChild) { it.next }

private fun Node.headingText(): String = when (this) {
    is Text -> literal
    is Code -> literal
    else -> children().joinToString("") { it.headingText() }
}

private val headingSeparator = Regex("[^\\p{L}\\p{N}]+")
private val snippetMarker = Regex("""<!-- snippet: ([A-Za-z0-9_./-]+[.]kt) -->""")
private val snippetRoots = setOf("examples", "scaffolds", "modules")
private val languageToken = Regex("[A-Za-z0-9_+-]+")

internal fun headingAnchors(body: Node, filename: String): Map<Heading, String> {
    val headings = linkedMapOf<Heading, String>()
    val used = mutableSetOf("main")
    fun visit(node: Node) {
        if (node is Heading) {
            require(node.level in 2..6) { "$filename: use front matter for the page title and start headings at level two" }
            val slug = node.headingText().lowercase(Locale.ROOT).replace(headingSeparator, "-").trim('-')
            require(slug.isNotEmpty() && used.add(slug)) { "$filename: empty or duplicate heading anchor '$slug'" }
            headings[node] = slug
        }
        node.children().forEach(::visit)
    }
    visit(body)
    return headings
}

internal class Markdown(
    private val repository: Path,
    private val basePath: String,
    private val pageAnchors: Map<String, Set<String>>,
) {
    fun render(writer: HtmlWriter, page: String, root: Node, headings: Map<Heading, String>) {
        fun HtmlWriter.renderNode(node: Node) {
            fun HtmlWriter.children() = node.children().forEach { renderNode(it) }
            when (node) {
                is Document -> children()
                is Heading -> element("h${node.level}", { attribute("id", headings.getValue(node)) }) { children() }
                is Paragraph -> {
                    val list = (node.parent as? ListItem)?.parent as? ListBlock
                    if (list?.isTight == true) children() else element("p") { children() }
                }
                is Text -> text(node.literal)
                is SoftLineBreak -> text("\n")
                is HardLineBreak -> voidElement("br")
                is Emphasis -> element("em") { children() }
                is StrongEmphasis -> element("strong") { children() }
                is Code -> element("code") { text(node.literal) }
                is FencedCodeBlock -> codeBlock(node.literal, node.info.substringBefore(' '))
                is IndentedCodeBlock -> codeBlock(node.literal)
                is Link -> element("a", {
                    url("href", linkUrl(page, node.destination))
                    node.title?.let { attribute("title", it) }
                }) { children() }
                is LinkReferenceDefinition -> Unit
                is BulletList -> element("ul") { children() }
                is OrderedList -> element("ol", {
                    node.markerStartNumber?.takeIf { it != 1 }?.let { attribute("start", it.toString()) }
                }) { children() }
                is ListItem -> element("li") { children() }
                is BlockQuote -> element("blockquote") { children() }
                is ThematicBreak -> voidElement("hr")
                is TableBlock -> element("table") { children() }
                is TableHead -> element("thead") { children() }
                is TableBody -> element("tbody") { children() }
                is TableRow -> element("tr") { children() }
                is TableCell -> element(if (node.isHeader) "th" else "td", {
                    if (node.isHeader) attribute("scope", "col")
                }) { children() }
                is HtmlInline -> text(node.literal)
                is HtmlBlock -> {
                    val literal = node.literal.trim()
                    val snippet = snippetMarker.matchEntire(literal)
                    require(snippet != null || !literal.startsWith("<!-- snippet:")) {
                        "$page.md: invalid Kotlin snippet marker"
                    }
                    if (snippet == null) codeBlock(node.literal)
                    else codeBlock(readSnippet(page, snippet.groupValues[1]), "kotlin")
                }
                else -> error("$page.md: unsupported Markdown element ${node.javaClass.simpleName}")
            }
        }
        writer.renderNode(root)
    }

    private fun linkUrl(page: String, destination: String): HtmlUrl {
        val uri = URI(destination)
        if (uri.isAbsolute) return externalUrl(destination)
        require(uri.rawAuthority == null && uri.rawQuery == null && !destination.startsWith('/')) {
            "$page.md: expected an absolute external URL or a documentation page link, got '$destination'"
        }
        val rawPath = uri.rawPath
        val target = if (rawPath.isEmpty()) page else rawPath.removeSuffix(".md")
        val allowed = pageAnchors[target]
        require(allowed != null && (rawPath.isEmpty() || rawPath == "$target.md")) {
            "$page.md: unknown target '$destination'"
        }
        val fragment = uri.fragment
        require(fragment == null || fragment in allowed) {
            "$page.md: unknown heading '$destination'"
        }
        require(rawPath.isNotEmpty() || fragment != null) { "$page.md: empty link" }
        val suffix = uri.rawFragment?.let { "#$it" }.orEmpty()
        return applicationUrl(if (rawPath.isEmpty()) destination else "$basePath$target.html$suffix")
    }

    private fun readSnippet(page: String, source: String): String {
        val path = Path(source)
        require(!path.isAbsolute && path.nameCount > 2 && path.getName(0).toString() in snippetRoots &&
            path.any { it.toString() == "src" } && path.none { it.toString() in setOf(".", "..") }) {
            "$page.md: expected a Kotlin source file under examples, scaffolds or modules, got '$source'"
        }
        val resolved = repository.resolve(path).toRealPath()
        require(resolved.startsWith(repository.resolve(path.getName(0)))) { "$page.md: snippet leaves its source tree" }
        return resolved.readText()
    }
}

private fun HtmlWriter.codeBlock(content: String, language: String = "") {
    element("pre", { attribute("tabindex", "0") }) {
        element("code", {
            if (languageToken.matches(language)) attribute("class", "language-$language")
        }) { text(content) }
    }
}
