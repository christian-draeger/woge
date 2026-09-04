package dev.woge.protocol.internal

import dev.woge.html.applicationUrl
import dev.woge.html.externalUrl
import dev.woge.protocol.PatchStreamErrorCode
import org.jsoup.Jsoup
import java.util.Locale

internal fun validatePatchHtml(html: String) {
    val document = Jsoup.parseBodyFragment(html)
    document.allElements.forEach { element ->
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
