package dev.woge.ui

import dev.woge.html.Attributes
import dev.woge.html.HtmlUrl
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.button
import dev.woge.html.dialog
import dev.woge.html.form
import dev.woge.html.h2

/** Path of the optional module that opens [dialogLink]s as modal dialogs. */
public const val DIALOG_MODULE_PATH: String = "/assets/woge-ui/dialog.js"

/**
 * A normal link to [fallback] that the optional dialog module turns into "open [dialog]".
 *
 * Without JavaScript, or when the module is not loaded, the link opens the fallback page. That
 * page should show the same content, so the task still works. Modifier clicks (new tab) always
 * follow the link.
 */
public fun HtmlWriter.dialogLink(
    dialog: UiId,
    fallback: HtmlUrl,
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit,
) {
    a(attributes = {
        url("href", fallback)
        data("woge-dialog", dialog.value)
        attributes()
    }, content = content)
}

/**
 * A native `dialog` element with a visible [title] that also names it for screen readers.
 *
 * Opened as a modal, the browser traps focus inside, makes the rest of the page inert and closes it
 * with Escape. The title gets the id `<id>-title`.
 */
public fun HtmlWriter.modalDialog(
    id: UiId,
    title: String,
    attributes: Attributes.() -> Unit = {},
    titleAttributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit,
) {
    val titleId = "${id.value}-title"
    dialog(attributes = {
        attribute("id", id.value)
        aria("labelledby", titleId)
        data("woge-ui", "dialog")
        attributes()
    }) {
        h2(attributes = {
            attribute("id", titleId)
            titleAttributes()
        }) { text(title) }
        content()
    }
}

/** A button that closes the surrounding dialog with a native `form method="dialog"`. No JavaScript. */
public fun HtmlWriter.dialogCloseButton(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit,
) {
    form(attributes = { attribute("method", "dialog") }) {
        button(attributes = {
            attribute("type", "submit")
            attributes()
        }, content = content)
    }
}
