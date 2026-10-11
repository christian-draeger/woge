// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.HtmlWriter
import dev.woge.html.div
import dev.woge.ui.LiveRegion
import dev.woge.ui.liveRegion

public fun HtmlWriter.statusMessage(
    message: String = "",
    kind: LiveRegion = LiveRegion.STATUS,
) {
    div(attributes = {
        liveRegion(kind)
        data("woge-scope", "woge-status-message")
        classes("woge-status-message")
    }) {
        text(message)
    }
}
