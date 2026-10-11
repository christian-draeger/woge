// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.HtmlWriter
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
            classes("woge-disclosure-card")
        },
        summaryAttributes = { classes("woge-disclosure-card-summary") },
        content = content,
    )
}
