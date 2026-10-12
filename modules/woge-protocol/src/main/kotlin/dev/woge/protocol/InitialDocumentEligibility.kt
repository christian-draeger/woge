package dev.woge.protocol

import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.util.Locale

/** Fail-closed content policy for experimental original-parser replacements, not fetched fragments. */
public fun isInitialDocumentHtmlEligible(html: PatchHtml): Boolean {
    if (html.containsUnsafeHtml) return false
    val fragment = Element("template")
    fragment.insertChildren(0, Parser.parseFragment(html.value, fragment, ""))
    return fragment.children().all { child -> child.allElements.all(::isPassiveElement) }
}

private fun isPassiveElement(element: Element): Boolean {
    if (element.normalName() in ACTIVE_ELEMENTS ||
        element.normalName() in PARSER_SENSITIVE_ELEMENTS ||
        '-' in element.normalName()
    ) {
        return false
    }
    return element.attributes().all { attribute ->
        val name = attribute.key.lowercase(Locale.ROOT)
        when {
            name.startsWith("on") || name in BLOCKED_ATTRIBUTES -> false
            name in URL_ATTRIBUTES -> isPassiveUrl(attribute.value)
            else -> true
        }
    }
}

private fun isPassiveUrl(value: String): Boolean =
    if (value.isBlank() || value.firstOrNull() in setOf('/', '#', '?')) {
        true
    } else {
        runCatching { URI(value.trim()) }.fold(
            onSuccess = { uri -> uri.scheme?.lowercase(Locale.ROOT)?.let(SAFE_URL_SCHEMES::contains) ?: true },
            onFailure = { false },
        )
    }

private val ACTIVE_ELEMENTS = setOf("base", "embed", "iframe", "link", "meta", "object", "script", "style")
private val PARSER_SENSITIVE_ELEMENTS =
    setOf(
        "html",
        "head",
        "body",
        "template",
        "table",
        "thead",
        "tbody",
        "tfoot",
        "tr",
        "td",
        "th",
        "caption",
        "col",
        "colgroup",
        "select",
        "option",
        "optgroup",
        "svg",
        "math",
    )
private val BLOCKED_ATTRIBUTES = setOf("is", "srcdoc", "style", "srcset", "imagesrcset", "ping")
private val URL_ATTRIBUTES =
    setOf("action", "background", "cite", "data", "formaction", "href", "manifest", "poster", "src", "xlink:href")
private val SAFE_URL_SCHEMES = setOf("http", "https", "mailto", "tel")
