package dev.woge.ui

import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.button
import dev.woge.html.div

/**
 * A button that shows and hides [popover] with the native `popovertarget` attribute.
 *
 * The browser reports the expanded state, closes the panel on Escape or an outside click and
 * returns focus to this button. No JavaScript is needed.
 */
public fun HtmlWriter.popoverButton(
    popover: UiId,
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit,
) {
    button(attributes = {
        attribute("type", "button")
        attribute("popovertarget", popover.value)
        attributes()
    }, content = content)
}

/**
 * Content that opens on top of the page from a [popoverButton], using the native `popover`
 * attribute. Use it for small panels such as options or extra help; use [modalDialog] when the
 * rest of the page must wait.
 */
public fun HtmlWriter.popoverPanel(
    id: UiId,
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit,
) {
    div(attributes = {
        attribute("id", id.value)
        attribute("popover", "auto")
        data("woge-ui", "popover")
        attributes()
    }, content = content)
}
