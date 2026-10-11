// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.Attributes
import dev.woge.html.HtmlUrl
import dev.woge.html.HtmlWriter
import dev.woge.ui.UiId
import dev.woge.ui.dialogCloseButton
import dev.woge.ui.dialogLink
import dev.woge.ui.modalDialog
import dev.woge.ui.popoverButton
import dev.woge.ui.popoverPanel

/** Load DIALOG_MODULE_PATH and installWogeDialogs only on pages using dialog triggers. */
public fun HtmlWriter.overlayLink(
    id: UiId,
    fallback: HtmlUrl,
    label: String,
) {
    dialogLink(id, fallback, attributes = {
        data("woge-state-key", "${id.value}-trigger")
        overlayPart("trigger")
    }) { text(label) }
}

public fun HtmlWriter.dialogComponent(
    id: UiId,
    title: String,
    closeLabel: String,
    content: HtmlWriter.() -> Unit,
) {
    overlayDialog(id, title, closeLabel, "dialog", content)
}

/** A drawer is a modal dialog with edge presentation; it does not change URL or history. */
public fun HtmlWriter.drawerComponent(
    id: UiId,
    title: String,
    closeLabel: String,
    content: HtmlWriter.() -> Unit,
) {
    overlayDialog(id, title, closeLabel, "drawer", content)
}

private fun HtmlWriter.overlayDialog(
    id: UiId,
    title: String,
    closeLabel: String,
    part: String,
    content: HtmlWriter.() -> Unit,
) {
    require(title.isNotBlank() && closeLabel.isNotBlank()) { "Dialogs need a title and close label" }
    modalDialog(id, title, attributes = { overlayPart(part) }) {
        content()
        dialogCloseButton(attributes = { boolean("autofocus", true) }) { text(closeLabel) }
    }
}

public fun HtmlWriter.popoverComponent(
    id: UiId,
    label: String,
    content: HtmlWriter.() -> Unit,
) {
    popoverButton(id, attributes = { overlayPart("trigger") }) { text(label) }
    popoverPanel(id, attributes = { overlayPart("popover") }, content = content)
}

/** Explicit help works by keyboard and touch. Essential instructions must also be visible inline. */
public fun HtmlWriter.tooltipComponent(
    id: UiId,
    label: String,
    description: String,
) {
    popoverButton(id, attributes = {
        aria("describedby", id.value)
        overlayPart("trigger")
    }) { text(label) }
    popoverPanel(id, attributes = {
        attribute("role", "tooltip")
        overlayPart("tooltip")
    }) { text(description) }
}

internal fun Attributes.overlayPart(part: String) {
    data("woge-scope", "woge-overlay-components")
    data("part", part)
    classes("woge-overlay-$part")
}
