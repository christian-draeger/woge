package dev.woge.html

/** Writes an `h1` element. */
public fun HtmlWriter.h1(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("h1", attributes, content)

/** Writes an `h2` element. */
public fun HtmlWriter.h2(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("h2", attributes, content)

/** Writes an `h3` element. */
public fun HtmlWriter.h3(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("h3", attributes, content)

/** Writes a `p` element. */
public fun HtmlWriter.p(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("p", attributes, content)

/** Writes an `a` element. Use [Attributes.url] for its `href`. */
public fun HtmlWriter.a(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("a", attributes, content)

/** Writes a `span` element. */
public fun HtmlWriter.span(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("span", attributes, content)

/** Writes a `strong` element. */
public fun HtmlWriter.strong(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("strong", attributes, content)

/** Writes an `em` element. */
public fun HtmlWriter.em(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("em", attributes, content)

/** Writes a `time` element. */
public fun HtmlWriter.time(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("time", attributes, content)
