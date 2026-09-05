package dev.woge.html

/** Writes a `form` element. Use [Attributes.url] for its `action`. */
public fun HtmlWriter.form(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("form", attributes, content)

/** Writes a `button` element. */
public fun HtmlWriter.button(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("button", attributes, content)

/** Writes a void `input` element. */
public fun HtmlWriter.input(attributes: Attributes.() -> Unit = {}): Unit = voidElement("input", attributes)

/** Writes a `label` element. */
public fun HtmlWriter.label(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("label", attributes, content)

/** Writes a `select` element. */
public fun HtmlWriter.select(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("select", attributes, content)

/** Writes an `option` element. */
public fun HtmlWriter.option(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("option", attributes, content)

/** Writes a `textarea` element. */
public fun HtmlWriter.textarea(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("textarea", attributes, content)
