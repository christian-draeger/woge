package dev.woge.invalid

import dev.woge.html.unsafeHtml

// WOGE-COMPILE-HTML-003: unsafe conversions require an explicit audited opt-in.
internal val invalidUnsafeConversion = unsafeHtml("<strong>not audited</strong>")
