package dev.woge.html.internal

internal fun escapeHtmlText(value: String): String = escapeHtml(value, attribute = false)

internal fun escapeHtmlAttribute(value: String): String = escapeHtml(value, attribute = true)

private fun escapeHtml(
    value: String,
    attribute: Boolean,
): String {
    var index = 0
    while (index < value.length) {
        if (value.hasSurrogatePairAt(index)) {
            index += 2
        } else {
            if (value[index].htmlReplacement(attribute) != null) break
            index++
        }
    }
    if (index == value.length) return value

    return buildString(value.length) {
        append(value, 0, index)
        while (index < value.length) {
            if (value.hasSurrogatePairAt(index)) {
                append(value[index])
                append(value[index + 1])
                index += 2
            } else {
                val character = value[index++]
                val replacement = character.htmlReplacement(attribute)
                if (replacement == null) append(character) else append(replacement)
            }
        }
    }
}

private fun String.hasSurrogatePairAt(index: Int): Boolean =
    this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()

private fun Char.htmlReplacement(attribute: Boolean): String? =
    when {
        this == '\u0000' || isSurrogate() -> "\uFFFD"
        this == '&' -> "&amp;"
        this == '<' -> "&lt;"
        this == '>' -> "&gt;"
        attribute && this == '"' -> "&quot;"
        attribute && this == '\'' -> "&#39;"
        attribute && this == '\r' -> "&#13;"
        else -> null
    }
