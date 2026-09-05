package dev.woge.html

/** Writes a `ul` element. */
public fun HtmlWriter.ul(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("ul", attributes, content)

/** Writes an `ol` element. */
public fun HtmlWriter.ol(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("ol", attributes, content)

/** Writes an `li` element. */
public fun HtmlWriter.li(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("li", attributes, content)

/** Writes a `dl` element. */
public fun HtmlWriter.dl(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("dl", attributes, content)

/** Writes a `dt` element. */
public fun HtmlWriter.dt(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("dt", attributes, content)

/** Writes a `dd` element. */
public fun HtmlWriter.dd(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("dd", attributes, content)
