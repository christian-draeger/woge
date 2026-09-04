package dev.woge.host

import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.protocol.PatchHtml
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.TargetRevision

/** Safe failure categories available while rendering a deferred region fallback. */
public enum class DeferredRegionFailure {
    TIMED_OUT,
    FAILED,
}

/**
 * One independently resolved region declared by portable page code.
 *
 * The loading content is ordinary server-rendered HTML. Deferred work starts only when the server
 * runtime collects the region execution flow.
 */
public class DeferredRegion internal constructor(
    public val target: PatchTarget,
    public val initialRevision: TargetRevision,
    private val loading: HtmlWriter.() -> Unit,
    private val failure: (DeferredRegionFailure) -> PatchHtml,
    private val content: suspend () -> PatchHtml,
) {
    /** Resolves the successful region content inside the current request coroutine. */
    public suspend fun renderContent(): PatchHtml = content()

    /** Renders client-safe replacement content for one controlled failure category. */
    public fun renderFailure(failure: DeferredRegionFailure): PatchHtml = this.failure(failure)

    internal fun renderLoading(writer: HtmlWriter) {
        loading(writer)
    }

    override fun toString(): String =
        "DeferredRegion(target=$target, initialRevision=${initialRevision.value}, renderers=<redacted>)"
}

/** Declares one deferred region without starting its [content] work. */
public fun deferredRegion(
    target: PatchTarget,
    initialRevision: TargetRevision = TargetRevision.INITIAL,
    loading: HtmlWriter.() -> Unit,
    onFailure: (DeferredRegionFailure) -> PatchHtml,
    content: suspend () -> PatchHtml,
): DeferredRegion = DeferredRegion(target, initialRevision, loading, onFailure, content)

/**
 * Writes the stable region element and its useful loading fallback into the initial page shell.
 *
 * The caller chooses a normal HTML element and may add ordinary attributes. Woge reserves only the
 * region and revision data attributes required by the fallback browser runtime.
 */
public fun HtmlWriter.regionPlaceholder(
    region: DeferredRegion,
    elementName: String = "div",
    attributes: Attributes.() -> Unit = {},
) {
    element(
        elementName,
        attributes = {
            data("woge-region", region.target.region.value)
            data("woge-revision", region.initialRevision.value.toString())
            attributes()
        },
    ) {
        region.renderLoading(this)
    }
}
