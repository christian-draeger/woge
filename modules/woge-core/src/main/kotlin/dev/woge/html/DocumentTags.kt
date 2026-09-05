package dev.woge.html

/** Writes an `html` element. */
public fun HtmlWriter.html(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("html", attributes, content)

/** Writes a `head` element. */
public fun HtmlWriter.head(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("head", attributes, content)

/** Writes a `body` element. */
public fun HtmlWriter.body(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("body", attributes, content)

/** Writes a `title` element. */
public fun HtmlWriter.title(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("title", attributes, content)

/** Writes a void `meta` element. */
public fun HtmlWriter.meta(attributes: Attributes.() -> Unit = {}): Unit = voidElement("meta", attributes)

/** Writes a `noscript` element whose content is useful without JavaScript. */
public fun HtmlWriter.noscript(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("noscript", attributes, content)
