// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.ApplicationUrl
import dev.woge.html.Attributes
import dev.woge.html.HtmlUrl
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.button
import dev.woge.html.div
import dev.woge.html.form
import dev.woge.html.input
import dev.woge.html.p
import dev.woge.ui.UiId

/**
 * What the user can rely on while data changes. The server picks the state, so a patch replaces
 * it together with the data; no state is kept in the browser.
 */
public enum class DataState(
    public val html: String,
) {
    /** Current data. No notice. */
    READY("ready"),

    /** New data is on its way. Marks the panel busy; show the old data or skeletons. */
    LOADING("loading"),

    /** Data may be out of date. Keep showing it, say so and offer a refresh link. */
    STALE("stale"),

    /** Some data could not be loaded. Show what loaded, say what is missing and offer a retry link. */
    PARTIAL_ERROR("partial-error"),

    /** A change is shown before the server confirmed it. Pending rows say so; a patch settles them. */
    OPTIMISTIC("optimistic"),
}

public class DataLink(
    public val label: String,
    public val href: HtmlUrl,
)

/** Render once. Patches replace the content of this stable root, never the root itself. */
public fun HtmlWriter.dataRegion(
    id: UiId,
    label: String,
    content: HtmlWriter.() -> Unit,
) {
    require(label.isNotBlank()) { "Data region needs an accessible name" }
    element("section", attributes = {
        attribute("id", id.value)
        attribute("tabindex", "-1")
        aria("label", label)
        data("woge-region", id.value)
        data("woge-revision", "0")
        dataPart("region")
    }, content = content)
}

/**
 * The content a patch sends for a region. The notice is visible text, not an announcement: patch a
 * stable announcement region from the feedback components when users must hear about it.
 */
public fun HtmlWriter.dataPanel(
    state: DataState = DataState.READY,
    notice: String? = null,
    retry: DataLink? = null,
    content: HtmlWriter.() -> Unit,
) {
    require((state == DataState.READY) == (notice == null)) { "Every state but READY needs a visible notice" }
    require(retry == null || state == DataState.STALE || state == DataState.PARTIAL_ERROR) {
        "Only stale and partial-error states offer a retry link"
    }
    div(attributes = {
        data("state", state.html)
        if (state == DataState.LOADING) aria("busy", "true")
        dataPart("panel")
    }) {
        if (notice != null) {
            p(attributes = { dataPart("notice") }) {
                text(notice)
                if (retry != null) {
                    text(" ")
                    a(attributes = {
                        url("href", retry.href)
                        data("woge-state-key", "data-retry")
                    }) { text(retry.label) }
                }
            }
        }
        content()
    }
}

/**
 * A native GET form: it works without JavaScript and the result is a bookmarkable URL. Put your
 * search fields in [content]. [preserved] keeps the sort in hidden fields; leave the page out so a
 * new filter starts at page 1.
 */
public fun HtmlWriter.filterForm(
    label: String,
    action: ApplicationUrl,
    submitLabel: String,
    preserved: Map<String, String> = emptyMap(),
    clear: DataLink? = null,
    content: HtmlWriter.() -> Unit,
) {
    require(label.isNotBlank()) { "Filter form needs an accessible name" }
    form(attributes = {
        url("action", action)
        attribute("method", "get")
        attribute("role", "search")
        aria("label", label)
        dataPart("filter")
    }) {
        content()
        preserved.forEach { (name, value) ->
            input(attributes = {
                attribute("type", "hidden")
                attribute("name", name)
                attribute("value", value)
            })
        }
        button(attributes = { attribute("type", "submit") }) { text(submitLabel) }
        clear?.let { link -> a(attributes = { url("href", link.href) }) { text(link.label) } }
    }
}

/**
 * Pagination arithmetic for a large collection. Load only [size] records starting at [offset]; the
 * browser never receives the whole collection. Out-of-range values from a URL are clamped.
 */
public class PageWindow private constructor(
    public val page: Int,
    public val size: Int,
    public val total: Long,
) {
    public val offset: Long = (page - 1).toLong() * size
    public val lastPage: Int = lastPage(total, size)

    /** The page numbers to link: first, last and a few around the current one. */
    public fun pages(radius: Int = 2): List<Int> =
        (setOf(1, lastPage) + (page - radius..page + radius)).filter { it in 1..lastPage }.sorted()

    public companion object {
        public const val DEFAULT_SIZE: Int = 25
        public const val MAX_SIZE: Int = 100

        public fun of(
            page: Int?,
            size: Int?,
            total: Long,
        ): PageWindow {
            require(total >= 0) { "Total must not be negative" }
            val boundedSize = (size ?: DEFAULT_SIZE).coerceIn(1, MAX_SIZE)
            return PageWindow((page ?: 1).coerceIn(1, lastPage(total, boundedSize)), boundedSize, total)
        }

        private fun lastPage(
            total: Long,
            size: Int,
        ): Int = maxOf(1L, (total + size - 1) / size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

/** Keys become attribute values and browser-state keys, so keep them URL-safe and short. */
internal fun dataKey(key: String): String {
    require(Regex("[A-Za-z0-9_-]{1,128}").matches(key)) { "Data key '$key' must use letters, digits, '-' or '_'" }
    return key
}

internal fun Attributes.dataPart(part: String) {
    data("woge-scope", "woge-data-display-components")
    data("part", part)
    classes(
        "text-[color:var(--woge-ink,#1f2933)] focus-visible:outline-2 focus-visible:outline-offset-2",
        "focus-visible:outline-[var(--woge-focus,#005fcc)]",
        when (part) {
            "card" ->
                "border border-[color:var(--woge-line,#d9e2ec)] rounded-lg p-4 bg-[var(--woge-surface,#ffffff)]"
            "card-heading" -> "mt-0 mb-2"
            "badge" ->
                "inline-block border border-current rounded-full px-2 text-sm " +
                    "data-[tone=success]:text-[color:var(--woge-ok,#1b7f3b)] data-[tone=warning]:text-[#8a5a00] " +
                    "data-[tone=danger]:text-[color:var(--woge-danger,#b42318)]"
            "avatar" -> "inline-flex items-center justify-center overflow-hidden size-10 rounded-full bg-gray-200"
            "avatar-image" -> "size-full object-cover"
            "empty" -> "border border-dashed border-[color:var(--woge-line,#d9e2ec)] p-8 text-center"
            "grid" -> "grid gap-4 m-0 p-0 list-none grid-cols-[repeat(auto-fill,minmax(min(16rem,100%),1fr))]"
            "scroll" -> "overflow-x-auto max-w-full"
            "table" ->
                "border-collapse w-full [&_caption]:text-start [&_caption]:font-semibold [&_caption]:pb-2 " +
                    "[&_:is(th,td)]:px-3 [&_:is(th,td)]:py-2 [&_:is(th,td)]:text-start " +
                    "[&_:is(th,td)]:border-b [&_:is(th,td)]:border-[color:var(--woge-line,#d9e2ec)] " +
                    "[&_[data-numeric=true]]:text-end [&_tr[data-pending=true]]:opacity-60 " +
                    "[&_th[aria-sort=ascending]_a]:after:content-['_▲'] " +
                    "[&_th[aria-sort=descending]_a]:after:content-['_▼']"
            "panel" ->
                "data-[state=loading]:opacity-60 data-[state=stale]:border-s-4 " +
                    "data-[state=partial-error]:border-s-4"
            "notice" -> "ps-4"
            "filter" -> "flex flex-wrap items-end gap-3"
            "visually-hidden" -> "absolute size-px overflow-hidden [clip-path:inset(50%)] whitespace-nowrap"
            else -> "accent-[var(--woge-focus,#005fcc)]"
        },
    )
}
