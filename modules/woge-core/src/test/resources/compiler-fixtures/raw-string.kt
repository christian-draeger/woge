package fixture

import dev.woge.html.HtmlWriter

// WOGE-XSS-001: ordinary strings must not become unescaped HTML.
public fun renderInvalidRawHtml(writer: HtmlWriter) {
    writer.raw("<script>alert('x')</script>")
}
