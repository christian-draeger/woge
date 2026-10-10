package dev.woge.protocol.internal

import dev.woge.html.applicationUrl
import dev.woge.html.externalUrl
import dev.woge.protocol.PatchItemId
import dev.woge.protocol.PatchStreamErrorCode
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.util.Locale

internal fun validatePatchHtml(
    html: String,
    itemId: PatchItemId? = null,
) {
    val fragment = Element("template")
    fragment.insertChildren(0, Parser.parseFragment(html, fragment, ""))
    fragment.allElements.forEach { element ->
        val tagName = element.normalName()
        if (tagName in BLOCKED_ELEMENTS) {
            activeContentFailure()
        }

        element.attributes().forEach { attribute ->
            val name = attribute.key.lowercase(Locale.ROOT)
            if (name.startsWith("on") || name == SRCDOC_ATTRIBUTE || name in MULTI_URL_ATTRIBUTES) {
                activeContentFailure()
            }
            if (name in URL_ATTRIBUTES && !isSafePatchUrl(attribute.value)) {
                activeContentFailure()
            }
        }
    }
    if (itemId != null) {
        validateItemRoot(fragment, itemId)
    }
}

private fun validateItemRoot(
    fragment: Element,
    itemId: PatchItemId,
) {
    val root = fragment.children().singleOrNull()
    val extraContent =
        fragment.childNodes().any { node ->
            when (node) {
                is Element -> false
                is TextNode -> !node.wholeText.isBlank()
                is Comment -> !node.data.isBlank()
                else -> true
            }
        }
    if (root == null || root.attr("data-woge-item") != itemId.value || extraContent) {
        protocolFailure(PatchStreamErrorCode.INVALID_ITEM, "Append patch must contain one identified item root")
    }
}

private fun isSafePatchUrl(value: String): Boolean {
    if (value.isEmpty()) return true
    return runCatching { applicationUrl(value) }.isSuccess ||
        runCatching { externalUrl(value) }.isSuccess
}

private fun activeContentFailure(): Nothing =
    protocolFailure(
        PatchStreamErrorCode.ACTIVE_CONTENT,
        "Patch HTML contains an executable element, attribute, or URL",
    )

private const val SRCDOC_ATTRIBUTE: String = "srcdoc"
private val BLOCKED_ELEMENTS: Set<String> =
    setOf("base", "embed", "iframe", "link", "meta", "object", "script", "style")
private val MULTI_URL_ATTRIBUTES: Set<String> = setOf("imagesrcset", "ping", "srcset")
private val URL_ATTRIBUTES: Set<String> =
    setOf(
        "action",
        "background",
        "cite",
        "data",
        "formaction",
        "href",
        "manifest",
        "poster",
        "src",
        "xlink:href",
    )
