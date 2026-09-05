package dev.woge.html

import dev.woge.css.CssDeclarations
import dev.woge.html.internal.escapeHtmlAttribute
import dev.woge.html.internal.escapeHtmlText

/** Writes standards-shaped HTML directly to a sink without building an in-memory DOM. */
@WogeHtmlDsl
public class HtmlWriter internal constructor(
    private val sink: HtmlSink,
) {
    /** Writes the standard declaration that selects standards mode for an HTML document. */
    public fun doctype() {
        sink.write("<!doctype html>")
    }

    /** Writes [value] as HTML text. Markup characters are escaped rather than interpreted. */
    public fun text(value: String) {
        sink.write(escapeHtmlText(value))
    }

    /** Writes deliberately unescaped HTML. Ordinary strings cannot cross this boundary. */
    @UnsafeWogeHtmlApi
    public fun raw(value: UnsafeHtml) {
        sink.write(value.value)
    }

    /** Writes a normal start tag, nested [content], and matching end tag. */
    public fun element(
        name: String,
        attributes: Attributes.() -> Unit = {},
        content: HtmlWriter.() -> Unit = {},
    ) {
        val normalizedName = requireElementName(name)
        require(normalizedName !in VOID_ELEMENTS) {
            "HTML void element '$name' must use voidElement(...)"
        }
        require(normalizedName !in UNSUPPORTED_RAW_TEXT_ELEMENTS) {
            "HTML raw-text element '$name' requires a context-specific API"
        }
        val resolvedAttributes = Attributes().apply(attributes)

        sink.write("<$name")
        resolvedAttributes.writeTo(sink)
        sink.write(">")
        content()
        sink.write("</$name>")
    }

    /** Writes a start tag without content or an end tag. */
    public fun voidElement(
        name: String,
        attributes: Attributes.() -> Unit = {},
    ) {
        requireElementName(name)
        val resolvedAttributes = Attributes().apply(attributes)

        sink.write("<$name")
        resolvedAttributes.writeTo(sink)
        sink.write(">")
    }

    internal fun rawTextElement(
        name: String,
        attributes: Attributes.() -> Unit,
        content: String,
    ) {
        val normalizedName = requireElementName(name)
        require(normalizedName in SUPPORTED_RAW_TEXT_ELEMENTS) {
            "HTML element '$name' is not supported by the raw-text writer"
        }
        val resolvedAttributes = Attributes().apply(attributes)

        sink.write("<$name")
        resolvedAttributes.writeTo(sink)
        sink.write(">")
        sink.write(content)
        sink.write("</$name>")
    }
}

/** Writes one HTML fragment directly to [sink]. */
public fun writeHtml(
    sink: HtmlSink,
    block: HtmlWriter.() -> Unit,
) {
    HtmlWriter(sink).block()
}

/** Renders a small HTML fragment to a deterministic in-memory string. */
public fun renderHtml(block: HtmlWriter.() -> Unit): String =
    BufferedHtmlSink().also { sink -> writeHtml(sink, block) }.content()

/** Collects one start tag's attributes in deterministic insertion order. */
@WogeHtmlDsl
public class Attributes internal constructor() {
    private val values: MutableAttributes = MutableAttributes()

    /**
     * Adds an ordinary quoted attribute.
     *
     * Known Boolean, URL, inline-event and `srcdoc` attributes require their context-specific method.
     */
    public fun attribute(
        name: String,
        value: String,
    ) {
        val normalizedName = requireAttributeName(name)
        require(normalizedName !in BOOLEAN_ATTRIBUTES) {
            "Boolean HTML attribute '$name' must use boolean(...)"
        }
        require(normalizedName !in URL_ATTRIBUTES) {
            "URL-bearing HTML attribute '$name' must use url(...)"
        }
        require(normalizedName != "srcdoc") { "HTML attribute 'srcdoc' must use srcdoc(...)" }
        require(normalizedName != "style") { "HTML attribute 'style' must use styles(declarations(...))" }
        require(!normalizedName.startsWith("on")) {
            "Inline event attribute '$name' requires unsafeAttribute(...)"
        }
        values.putUnique(name, normalizedName, AttributeValue.Text(value))
    }

    /** Emits [name] with no value when [present] is true, and omits it otherwise. */
    public fun boolean(
        name: String,
        present: Boolean = true,
    ) {
        val normalizedName = requireAttributeName(name)
        if (present) {
            values.putUnique(name, normalizedName, AttributeValue.Present)
        } else {
            values.remove(normalizedName)
        }
    }

    /** Adds a `data-*` attribute without restricting application-defined suffixes to a catalog. */
    public fun data(
        name: String,
        value: String,
    ) {
        requireAttributeSuffix(name, "data")
        attribute("data-$name", value)
    }

    /** Adds an `aria-*` attribute while preserving its string-valued web semantics. */
    public fun aria(
        name: String,
        value: String,
    ) {
        requireAttributeSuffix(name, "aria")
        attribute("aria-$name", value)
    }

    /** Adds ordered CSS class contributors without parsing Tailwind or framework-specific tokens. */
    public fun classes(vararg contributors: String) {
        values.appendContributor("class", contributors.asIterable())
    }

    /** Adds ordered typed CSS declaration-list contributors without parsing CSS properties. */
    public fun styles(declarationList: CssDeclarations) {
        values.appendContributor("style", listOf(declarationList.source))
    }

    /** Adds a quoted URL attribute after scheme and structure validation by an [HtmlUrl] factory. */
    public fun url(
        name: String,
        value: HtmlUrl,
    ) {
        val normalizedName = requireAttributeName(name)
        requireSafeUrlAttributeName(name, normalizedName)
        values.putUnique(name, normalizedName, AttributeValue.Text(value.value))
    }

    /** Writes an audited URL-like value, including active or multi-URL syntax, as a quoted attribute. */
    @UnsafeWogeHtmlApi
    public fun unsafeUrl(
        name: String,
        value: UnsafeHtmlUrl,
    ) {
        val normalizedName = requireAttributeName(name)
        requireSafeUrlAttributeName(name, normalizedName)
        values.putUnique(name, normalizedName, AttributeValue.Text(value.value))
    }

    /** Bypasses context restrictions for an explicitly audited attribute value. */
    @UnsafeWogeHtmlApi
    public fun unsafeAttribute(
        name: String,
        value: String,
    ) {
        val normalizedName = requireAttributeName(name)
        values.putUnique(name, normalizedName, AttributeValue.Text(value))
    }

    internal fun writeTo(sink: HtmlSink) {
        values.writeTo(sink)
    }
}

/** Writes audited HTML into an iframe `srcdoc` attribute after outer attribute escaping. */
@UnsafeWogeHtmlApi
public fun Attributes.srcdoc(value: UnsafeHtml) {
    unsafeAttribute("srcdoc", value.value)
}

private class MutableAttributes {
    private val entries: LinkedHashMap<String, AttributeEntry> = linkedMapOf()

    fun putUnique(
        name: String,
        normalizedName: String,
        value: AttributeValue,
    ) {
        require(normalizedName !in entries) { "Duplicate HTML attribute '$name'" }
        entries[normalizedName] = AttributeEntry(name, value)
    }

    fun remove(normalizedName: String) {
        entries.remove(normalizedName)
    }

    fun writeTo(sink: HtmlSink) {
        entries.values.forEach { entry ->
            sink.write(" ${entry.name}")
            when (val value = entry.value) {
                AttributeValue.Present -> Unit
                is AttributeValue.Text -> {
                    sink.write("=\"")
                    sink.write(escapeHtmlAttribute(value.value))
                    sink.write("\"")
                }
            }
        }
    }

    fun appendContributor(
        name: String,
        contributors: Iterable<String>,
    ) {
        val addition = contributors.map { it.trim() }.filter(String::isNotEmpty).joinToString(" ")
        if (addition.isEmpty()) return

        val existing = entries[name]
        if (existing == null) {
            entries[name] = AttributeEntry(name, AttributeValue.Text(addition))
            return
        }
        val existingText =
            existing.value as? AttributeValue.Text
                ?: throw IllegalArgumentException("Cannot compose a value-less '$name' attribute")
        existing.value = AttributeValue.Text("${existingText.value} $addition")
    }
}

private data class AttributeEntry(
    val name: String,
    var value: AttributeValue,
)

private sealed interface AttributeValue {
    data object Present : AttributeValue

    data class Text(
        val value: String,
    ) : AttributeValue
}

private val ELEMENT_NAME: Regex = Regex("[A-Za-z][A-Za-z0-9._:-]*")
private val ATTRIBUTE_SUFFIX: Regex = Regex("[a-z][a-z0-9._-]*")

private fun requireElementName(value: String): String {
    require(ELEMENT_NAME.matches(value)) { "Invalid HTML element name '$value'" }
    return value.lowercase()
}

private fun requireAttributeName(value: String): String {
    require(value.isNotEmpty()) { "HTML attribute name must not be empty" }
    require(value.none(::isInvalidAttributeNameCharacter)) { "Invalid HTML attribute name '$value'" }
    return value.lowercase()
}

private fun requireAttributeSuffix(
    value: String,
    prefix: String,
) {
    require(ATTRIBUTE_SUFFIX.matches(value)) { "Invalid $prefix-* attribute suffix '$value'" }
}

private fun requireSafeUrlAttributeName(
    name: String,
    normalizedName: String,
) {
    require(normalizedName !in BOOLEAN_ATTRIBUTES) {
        "Boolean HTML attribute '$name' cannot contain a URL"
    }
    require(normalizedName != "srcdoc") { "HTML attribute 'srcdoc' must use srcdoc(...)" }
    require(!normalizedName.startsWith("on")) {
        "Inline event attribute '$name' requires unsafeAttribute(...)"
    }
}

private fun isInvalidAttributeNameCharacter(character: Char): Boolean =
    character.isWhitespace() ||
        character.isISOControl() ||
        character in INVALID_ATTRIBUTE_NAME_CHARACTERS ||
        character.isSurrogate()

private val INVALID_ATTRIBUTE_NAME_CHARACTERS: Set<Char> = setOf('<', '>', '/', '=', '"', '\'')

private val VOID_ELEMENTS: Set<String> =
    setOf(
        "area",
        "base",
        "br",
        "col",
        "embed",
        "hr",
        "img",
        "input",
        "link",
        "meta",
        "source",
        "track",
        "wbr",
    )

private val UNSUPPORTED_RAW_TEXT_ELEMENTS: Set<String> =
    setOf("iframe", "noembed", "noframes", "plaintext", "script", "style", "xmp")

private val SUPPORTED_RAW_TEXT_ELEMENTS: Set<String> = setOf("script", "style")

private val BOOLEAN_ATTRIBUTES: Set<String> =
    setOf(
        "allowfullscreen",
        "async",
        "autofocus",
        "autoplay",
        "checked",
        "controls",
        "default",
        "defer",
        "disabled",
        "formnovalidate",
        "inert",
        "ismap",
        "itemscope",
        "loop",
        "multiple",
        "muted",
        "nomodule",
        "novalidate",
        "open",
        "playsinline",
        "readonly",
        "required",
        "reversed",
        "selected",
    )

private val URL_ATTRIBUTES: Set<String> =
    setOf(
        "action",
        "archive",
        "background",
        "cite",
        "classid",
        "codebase",
        "data",
        "formaction",
        "href",
        "icon",
        "imagesrcset",
        "itemid",
        "longdesc",
        "manifest",
        "ping",
        "poster",
        "profile",
        "src",
        "srcset",
        "usemap",
        "xlink:href",
    )
