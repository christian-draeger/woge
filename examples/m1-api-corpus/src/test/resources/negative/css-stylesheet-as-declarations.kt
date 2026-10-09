package dev.woge.invalid

import dev.woge.css.stylesheet
import dev.woge.html.renderHtml

// WOGE-COMPILE-CSS-001: a style attribute takes declarations, not a whole stylesheet.
internal fun invalidInlineStyle(): String =
    renderHtml {
        element("div", attributes = { styles(stylesheet(".card { color: red; }")) })
    }
