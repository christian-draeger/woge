package dev.woge.spike.html.negative

import dev.woge.spike.html.renderHtml

public fun invalidRawString(): String = renderHtml {
    raw("<strong>not explicitly trusted</strong>")
}
