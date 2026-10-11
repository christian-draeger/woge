import dev.woge.html.HtmlWriter
import dev.woge.html.unsafeAttribute

fun bypass(writer: HtmlWriter) {
    writer.element("button", attributes = { unsafeAttribute("onclick", "alert(1)") })
}
