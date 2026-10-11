// SPDX-License-Identifier: Apache-2.0
// Application customization: operations handbook label and board-specific class.
package dev.woge.example.components

import dev.woge.html.HtmlWriter
import dev.woge.html.p
import dev.woge.ui.disclosure

public fun HtmlWriter.disclosureCard(
    title: String,
    open: Boolean = false,
    content: HtmlWriter.() -> Unit,
) {
    disclosure(
        summary = { text(title) },
        open = open,
        attributes = {
            data("woge-scope", "woge-disclosure-card")
            classes("woge-disclosure-card board-faq")
        },
        summaryAttributes = { classes("woge-disclosure-card-summary") },
        content = {
            p(attributes = { classes("handbook-label") }) { text("Northstar operations handbook") }
            content()
        },
    )
}
