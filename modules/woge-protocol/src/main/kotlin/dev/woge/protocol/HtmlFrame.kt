package dev.woge.protocol

import dev.woge.html.HtmlSink
import dev.woge.html.HtmlWriter
import dev.woge.html.writeHtml

/**
 * One ordered, lazily rendered part of an HTML document stream.
 *
 * The frame stores a Woge HTML-DSL program rather than already concatenated markup. A host can
 * therefore render it through a bounded sink when the frame is collected. Ordinary strings remain
 * text; bypassing escaping still requires Woge's explicit unsafe HTML opt-in.
 */
public class HtmlFrame internal constructor(
    private val content: HtmlWriter.() -> Unit,
) {
    /** Renders this frame to [sink], preserving writer order and downstream failures. */
    public fun writeTo(sink: HtmlSink) {
        writeHtml(sink, content)
    }
}

/** Creates one lazy HTML frame using the same safe DSL as a complete buffered document. */
public fun htmlFrame(content: HtmlWriter.() -> Unit): HtmlFrame = HtmlFrame(content)
