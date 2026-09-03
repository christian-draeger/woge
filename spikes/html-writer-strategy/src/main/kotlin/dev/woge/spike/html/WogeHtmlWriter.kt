package dev.woge.spike.html

@DslMarker
public annotation class WogeHtmlDsl

public fun interface HtmlSink {
    public fun write(value: String)
}

@JvmInline
public value class UnsafeHtml internal constructor(internal val value: String)

public fun unsafeHtml(value: String): UnsafeHtml = UnsafeHtml(value)

@WogeHtmlDsl
public class Attributes internal constructor() {
    private val values: LinkedHashMap<String, AttributeValue> = linkedMapOf()

    public fun attribute(name: String, value: String) {
        requireAttributeName(name)
        values[name] = AttributeValue.Text(value)
    }

    public fun boolean(name: String, present: Boolean = true) {
        requireAttributeName(name)
        if (present) values[name] = AttributeValue.Present else values.remove(name)
    }

    public fun data(name: String, value: String) {
        requireToken(name, "data attribute")
        attribute("data-$name", value)
    }

    public fun aria(name: String, value: String) {
        requireToken(name, "ARIA attribute")
        attribute("aria-$name", value)
    }

    public fun classes(vararg contributors: String) {
        val addition = contributors.filter(String::isNotBlank).joinToString(" ")
        if (addition.isEmpty()) return
        val existing = (values["class"] as? AttributeValue.Text)?.value
        values["class"] = AttributeValue.Text(listOfNotNull(existing, addition).joinToString(" "))
    }

    public fun styles(vararg declarationLists: String) {
        val addition = declarationLists.filter(String::isNotBlank).joinToString(" ")
        if (addition.isEmpty()) return
        val existing = (values["style"] as? AttributeValue.Text)?.value
        values["style"] = AttributeValue.Text(listOfNotNull(existing, addition).joinToString(" "))
    }

    internal fun entries(): List<Pair<String, AttributeValue>> = values.toList()
}

@WogeHtmlDsl
public class HtmlWriter(public val sink: HtmlSink) {
    public fun text(value: String) {
        sink.write(escapeText(value))
    }

    public fun raw(value: UnsafeHtml) {
        sink.write(value.value)
    }

    public fun element(
        name: String,
        attributes: Attributes.() -> Unit = {},
        content: HtmlWriter.() -> Unit = {},
    ) {
        requireElementName(name)
        sink.write("<$name")
        writeAttributes(attributes)
        sink.write(">")
        content()
        sink.write("</$name>")
    }

    public fun voidElement(
        name: String,
        attributes: Attributes.() -> Unit = {},
    ) {
        requireElementName(name)
        sink.write("<$name")
        writeAttributes(attributes)
        sink.write(">")
    }

    private fun writeAttributes(block: Attributes.() -> Unit) {
        val attributes = Attributes().apply(block)
        for ((name, value) in attributes.entries()) {
            sink.write(" $name")
            if (value is AttributeValue.Text) {
                sink.write("=\"")
                sink.write(escapeAttribute(value.value))
                sink.write("\"")
            }
        }
    }
}

public fun renderHtml(block: HtmlWriter.() -> Unit): String = buildString {
    HtmlWriter(HtmlSink(::append)).block()
}

internal sealed interface AttributeValue {
    data object Present : AttributeValue
    data class Text(val value: String) : AttributeValue
}

private val attributeToken = Regex("[a-z][a-z0-9._:-]*")
private val invalidNameCharacters = setOf('\u0000', '<', '>', '/', '=', '"', '\'')

private fun requireElementName(value: String) {
    require(value.firstOrNull()?.isLetter() == true && value.none(::isInvalidNameCharacter)) {
        "Invalid HTML element name: $value"
    }
}

private fun requireAttributeName(value: String) {
    require(value.isNotEmpty() && value.none(::isInvalidNameCharacter)) {
        "Invalid HTML attribute name: $value"
    }
}

private fun requireToken(value: String, kind: String) {
    require(attributeToken.matches(value)) { "Invalid $kind token: $value" }
}

private fun isInvalidNameCharacter(character: Char): Boolean =
    character.isWhitespace() || character in invalidNameCharacters

private fun escapeText(value: String): String = buildString(value.length) {
    value.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                else -> character
            },
        )
    }
}

private fun escapeAttribute(value: String): String = buildString(value.length) {
    value.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> character
            },
        )
    }
}
