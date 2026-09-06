package dev.woge.invalid

import dev.woge.html.renderHtml

// WOGE-COMPILE-HTML-001: raw HTML requires the explicit UnsafeHtml boundary.
internal fun invalidRawHtml(): String = renderHtml { raw("<strong>unsafe</strong>") }
