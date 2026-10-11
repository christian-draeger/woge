package dev.woge.runtime

import dev.woge.protocol.PatchHtml
import dev.woge.protocol.ReplacePatch
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.util.Locale
import java.util.UUID

/** Opaque generated name shared by a page's range boundary and its deferred replacement. */
@JvmInline
internal value class NativeDpuName internal constructor(
    public val value: String,
)

/** Creates collision-resistant names with no application-provided selector data. */
internal fun newNativeDpuName(): NativeDpuName = NativeDpuName("woge-${UUID.randomUUID()}")

/** Internal native encoder for semantic Replace patches on an initial document parser. */
internal object NativeDpuEncoder {
    /** Selects native only when both explicit mode and content policy allow it. */
    public fun isSelected(
        mode: NativeDpuMode,
        cookieHeader: String?,
        html: PatchHtml,
    ): Boolean {
        if (!isEligible(html)) return false
        return when (mode) {
            NativeDpuMode.OFF, NativeDpuMode.FORCE_FALLBACK -> false
            NativeDpuMode.AUTO -> hasCapabilityCookie(cookieHeader)
            NativeDpuMode.FORCE_NATIVE -> true
        }
    }

    /** Reads exactly one affirmative, non-stale capability cookie; ambiguous duplicates fail closed. */
    public fun hasCapabilityCookie(cookieHeader: String?): Boolean {
        val signals =
            cookieHeader
                ?.split(';')
                ?.map(String::trim)
                ?.filter { it.startsWith(CAPABILITY_COOKIE_PREFIX) }
                ?: return false
        return signals.singleOrNull() == CAPABILITY_COOKIE
    }

    /** Writes the initial-document range boundary and its useful HTML fallback. */
    public fun encodeRangeStart(
        name: NativeDpuName,
        fallback: PatchHtml,
    ): String {
        require(isEligible(fallback)) { "Native DPU range fallback contains ineligible HTML" }
        return "<?start name=\"${name.value}\">${fallback.value}<?end>"
    }

    /** Maps the existing Replace Patch IR to a native range replacement template. */
    public fun encodeReplace(
        patch: ReplacePatch,
        name: NativeDpuName,
    ): String {
        require(isEligible(patch.html)) { "Native DPU replacement contains ineligible HTML" }
        return "<template for=\"${name.value}\">${patch.html.value}</template>"
    }

    /** Active or explicitly unsafe markup is never handed to the active native HTML parser. */
    public fun isEligible(html: PatchHtml): Boolean {
        if (html.containsUnsafeHtml) return false
        val fragment = Element("template")
        fragment.insertChildren(0, Parser.parseFragment(html.value, fragment, ""))
        return fragment.allElements.all(::isPassiveElement)
    }

    private fun isPassiveElement(element: Element): Boolean {
        if (element.normalName() in ACTIVE_ELEMENTS) return false
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
        if (isRelativeUrl(value)) {
            true
        } else {
            runCatching { URI(value.trim()) }.fold(
                onSuccess = { uri -> uri.scheme?.lowercase(Locale.ROOT)?.let(SAFE_URL_SCHEMES::contains) ?: true },
                onFailure = { false },
            )
        }

    private fun isRelativeUrl(value: String): Boolean = value.isBlank() || value.firstOrNull() in setOf('/', '#', '?')

    private const val CAPABILITY_COOKIE_PREFIX = "woge_dpu="
    private const val CAPABILITY_COOKIE = "woge_dpu=1"
    private val ACTIVE_ELEMENTS = setOf("base", "embed", "iframe", "link", "meta", "object", "script", "style")
    private val BLOCKED_ATTRIBUTES = setOf("srcdoc", "style", "srcset", "imagesrcset", "ping")
    private val URL_ATTRIBUTES =
        setOf("action", "background", "cite", "data", "formaction", "href", "manifest", "poster", "src", "xlink:href")
    private val SAFE_URL_SCHEMES = setOf("http", "https", "mailto", "tel")
}
