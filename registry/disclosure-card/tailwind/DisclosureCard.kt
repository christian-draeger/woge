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
            classes("rounded-lg border border-slate-300 p-4 text-slate-950")
        },
        summaryAttributes = { classes("cursor-pointer font-semibold") },
        content = content,
    )
}
