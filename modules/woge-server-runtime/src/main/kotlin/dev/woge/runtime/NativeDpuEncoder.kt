package dev.woge.runtime

import dev.woge.protocol.PatchHtml
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.isInitialDocumentHtmlEligible
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
                ?.filter { it.substringBefore('=').trim() == CAPABILITY_COOKIE_NAME }
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
    public fun isEligible(html: PatchHtml): Boolean = isInitialDocumentHtmlEligible(html)

    private const val CAPABILITY_COOKIE_NAME = "woge_dpu"
    private const val CAPABILITY_COOKIE = "woge_dpu=1"
}
