package dev.woge.protocol

import dev.woge.html.BufferedHtmlSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HtmlFrameTest {
    @Test
    fun `frame renders lazily with normal HTML escaping`() {
        var title = "before"
        val frame =
            htmlFrame {
                element("h1") {
                    text(title)
                }
            }
        title = "Tasks <today>"
        val sink = BufferedHtmlSink()

        frame.writeTo(sink)

        assertEquals("<h1>Tasks &lt;today&gt;</h1>", sink.content())
    }
}
