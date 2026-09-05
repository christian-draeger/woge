package dev.woge.html

/** Writes a `table` element. */
public fun HtmlWriter.table(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("table", attributes, content)

/** Writes a `caption` element. */
public fun HtmlWriter.caption(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("caption", attributes, content)

/** Writes a `thead` element. */
public fun HtmlWriter.thead(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("thead", attributes, content)

/** Writes a `tbody` element. */
public fun HtmlWriter.tbody(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("tbody", attributes, content)

/** Writes a `tr` element. */
public fun HtmlWriter.tr(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("tr", attributes, content)

/** Writes a `th` element. */
public fun HtmlWriter.th(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("th", attributes, content)

/** Writes a `td` element. */
public fun HtmlWriter.td(
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit = {},
): Unit = element("td", attributes, content)
