package dev.woge.spike.html.negative

import dev.woge.spike.html.renderHtml

public fun invalidBooleanAttribute(): String = renderHtml {
    element("button", attributes = {
        boolean("disabled", "true")
    })
}
