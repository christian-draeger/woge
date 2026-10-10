package dev.woge.ui

import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.details
import dev.woge.html.summary

/**
 * Show and hide [content] with the native `details` and `summary` elements.
 *
 * The browser handles the click, the keyboard and the expanded state for screen readers, with or
 * without JavaScript. [open] sets the state the server renders. Style it with ordinary CSS, for
 * example `details[open] > summary`.
 */
public fun HtmlWriter.disclosure(
    summary: HtmlWriter.() -> Unit,
    open: Boolean = false,
    attributes: Attributes.() -> Unit = {},
    summaryAttributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit,
) {
    details(attributes = {
        data("woge-ui", "disclosure")
        boolean("open", open)
        attributes()
    }) {
        summary(attributes = summaryAttributes, content = summary)
        content()
    }
}
