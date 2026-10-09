package dev.woge.invalid

import dev.woge.html.renderHtml

// WOGE-COMPILE-HTML-004: boolean attributes take a Boolean, not the string "true".
internal fun invalidBooleanAttribute(): String =
    renderHtml {
        element("button", attributes = { boolean("disabled", "true") })
    }
