// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.div
import dev.woge.ui.LiveRegion
import dev.woge.ui.UiId
import dev.woge.ui.liveRegion

/** Static feedback is not an announcement. Patch the stable announcement region for new events. */
public fun HtmlWriter.alertComponent(message: String) {
    div(attributes = { feedbackPart("alert") }) { text(message) }
}

public fun HtmlWriter.progressComponent(
    label: String,
    value: Double? = null,
    maximum: Double = 100.0,
) {
    require(label.isNotBlank()) { "Progress needs an accessible name" }
    require(maximum.isFinite() && maximum > 0) { "Progress maximum must be positive and finite" }
    require(value == null || value.isFinite() && value in 0.0..maximum) { "Progress value is out of range" }
    element("progress", attributes = {
        aria("label", label)
        attribute("max", maximum.toString())
        if (value != null) attribute("value", value.toString())
        feedbackPart("progress")
    }) { text(label) }
}

/** Decorative placeholder; supply visible loading text and aria-busy on the owning content region. */
public fun HtmlWriter.skeletonComponent() {
    div(attributes = {
        aria("hidden", "true")
        feedbackPart("skeleton")
    }) {}
}

/** No timer or live role: users can read and act on the toast until the application removes it. */
public fun HtmlWriter.toastComponent(
    id: UiId,
    content: HtmlWriter.() -> Unit,
) {
    div(attributes = {
        attribute("id", id.value)
        feedbackPart("toast")
    }, content = content)
}

/** Render once, empty, outside repeatable content. Patches replace text inside this stable root. */
public fun HtmlWriter.announcementRegion(
    id: UiId,
    kind: LiveRegion = LiveRegion.STATUS,
) {
    div(attributes = {
        attribute("id", id.value)
        data("woge-region", id.value)
        data("woge-revision", "0")
        aria("atomic", "true")
        liveRegion(kind)
        feedbackPart("announcement")
    }) {}
}

internal fun Attributes.feedbackPart(part: String) {
    data("woge-scope", "woge-feedback-components")
    data("part", part)
    classes(
        "text-[var(--woge-ink,#1f2933)]",
        "[&[data-part=alert]]:border-s-4",
        "[&[data-part=alert]]:ps-4",
        "[&[data-part=toast]]:border",
        "[&[data-part=toast]]:p-4",
        "[&[data-part=skeleton]]:min-h-6",
        "[&[data-part=skeleton]]:bg-gray-200",
        "[&[data-part=skeleton]]:animate-pulse",
        "motion-reduce:[&[data-part=skeleton]]:animate-none",
    )
}
