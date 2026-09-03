package dev.woge.spike.html

import kotlinx.html.BODY
import kotlinx.html.FlowContent
import kotlinx.html.HTMLTag
import kotlinx.html.TagConsumer
import kotlinx.html.article
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.stream.appendHTML
import kotlinx.html.stream.createHTML
import kotlinx.html.visit

public data class TaskCard(
    public val title: String,
    public val pending: Boolean,
)

public fun renderWithWogeWriter(card: TaskCard, sink: HtmlSink): Unit = HtmlWriter(sink).run {
    element(
        name = "article",
        attributes = {
            classes("task-card", "grid gap-3 md:grid-cols-[minmax(0,1fr)_auto]")
            data("state", if (card.pending) "pending" else "ready")
            aria("busy", card.pending.toString())
            styles("--accent: oklch(62% 0.2 250);", "container-type: inline-size;")
        },
    ) {
        element("h2") { text(card.title) }
        element("woge-status", attributes = { attribute("kind", "task") }) {
            text(if (card.pending) "Saving" else "Saved")
        }
        element("button", attributes = { boolean("disabled", card.pending) }) {
            text("Complete")
        }
    }
}

public fun renderWithKotlinxHtml(card: TaskCard): String = createHTML().article(
    classes = "task-card grid gap-3 md:grid-cols-[minmax(0,1fr)_auto]",
) {
    attributes["data-state"] = if (card.pending) "pending" else "ready"
    attributes["aria-busy"] = card.pending.toString()
    attributes["style"] = "--accent: oklch(62% 0.2 250); container-type: inline-size;"
    h2 { +card.title }
    wogeStatus {
        attributes["kind"] = "task"
        +(if (card.pending) "Saving" else "Saved")
    }
    button {
        disabled = card.pending
        +"Complete"
    }
}

public fun renderKotlinxIntoAppendable(card: TaskCard, appendable: Appendable): Unit {
    appendable.appendHTML().body {
        renderKotlinxTaskCard(card)
    }
}

public fun renderKotlinxIntoWogeSink(card: TaskCard, sink: HtmlSink): Unit {
    renderKotlinxIntoAppendable(card, SinkAppendable(sink))
}

private fun BODY.renderKotlinxTaskCard(card: TaskCard) {
    article {
        h2 { +card.title }
    }
}

private class WogeStatus(consumer: TagConsumer<*>) :
    HTMLTag("woge-status", consumer, emptyMap(), inlineTag = false, emptyTag = false)

private fun FlowContent.wogeStatus(block: WogeStatus.() -> Unit = {}) {
    WogeStatus(consumer).visit(block)
}

private class SinkAppendable(private val sink: HtmlSink) : Appendable {
    override fun append(csq: CharSequence?): Appendable = apply {
        sink.write(csq?.toString() ?: "null")
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): Appendable = apply {
        sink.write(csq?.subSequence(start, end)?.toString() ?: "null")
    }

    override fun append(c: Char): Appendable = apply {
        sink.write(c.toString())
    }
}
