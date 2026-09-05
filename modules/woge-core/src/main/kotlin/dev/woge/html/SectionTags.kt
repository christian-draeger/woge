package dev.woge.html

/** Writes a `header` element. */
public fun HtmlWriter.header(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("header", attributes, content)

/** Writes a `nav` element. */
public fun HtmlWriter.nav(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("nav", attributes, content)

/** Writes a `main` element. */
public fun HtmlWriter.main(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("main", attributes, content)

/** Writes a `footer` element. */
public fun HtmlWriter.footer(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("footer", attributes, content)

/** Writes a `section` element. */
public fun HtmlWriter.section(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("section", attributes, content)

/** Writes an `article` element. */
public fun HtmlWriter.article(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("article", attributes, content)

/** Writes an `aside` element. */
public fun HtmlWriter.aside(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("aside", attributes, content)

/** Writes a `div` element. */
public fun HtmlWriter.div(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("div", attributes, content)
