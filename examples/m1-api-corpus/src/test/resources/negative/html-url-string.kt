package dev.woge.invalid

import dev.woge.html.a
import dev.woge.html.renderHtml

// WOGE-COMPILE-HTML-002: URL attributes require a validated HtmlUrl, not a String.
internal fun invalidUrl(): String =
    renderHtml {
        a(attributes = { url("href", "/projects/woge") }) { text("Open") }
    }
