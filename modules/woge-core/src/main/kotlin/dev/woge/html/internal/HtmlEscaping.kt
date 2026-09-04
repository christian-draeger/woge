package dev.woge.html.internal

internal fun escapeHtmlText(value: String): String = escapeHtml(value, attribute = false)

internal fun escapeHtmlAttribute(value: String): String = escapeHtml(value, attribute = true)

private fun escapeHtml(
    value: String,
    attribute: Boolean,
): String =
    buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character.isHighSurrogate()) {
                index += appendSurrogatePair(value, index)
            } else {
                appendEscaped(character, attribute)
            }
            index += 1
        }
    }

private fun StringBuilder.appendSurrogatePair(
    value: String,
    index: Int,
): Int {
    val highSurrogate = value[index]
    val lowSurrogate = value.getOrNull(index + 1)
    if (lowSurrogate?.isLowSurrogate() == true) {
        append(highSurrogate)
        append(lowSurrogate)
        return 1
    }

    append('\uFFFD')
    return 0
}

private fun StringBuilder.appendEscaped(
    character: Char,
    attribute: Boolean,
) {
    when {
        character == '\u0000' || character.isLowSurrogate() -> append('\uFFFD')
        character == '&' -> append("&amp;")
        character == '<' -> append("&lt;")
        character == '>' -> append("&gt;")
        attribute && character == '"' -> append("&quot;")
        attribute && character == '\'' -> append("&#39;")
        attribute && character == '\r' -> append("&#13;")
        else -> append(character)
    }
}
