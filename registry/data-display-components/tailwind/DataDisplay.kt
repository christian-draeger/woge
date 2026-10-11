// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.HtmlUrl
import dev.woge.html.HtmlWriter
import dev.woge.html.div
import dev.woge.html.img
import dev.woge.html.li
import dev.woge.html.p
import dev.woge.html.span
import dev.woge.html.ul

public enum class BadgeTone(
    public val html: String,
) {
    NEUTRAL("neutral"),
    SUCCESS("success"),
    WARNING("warning"),
    DANGER("danger"),
}

/** One grid entry. The key must stay the same for the same record, so patches do not duplicate it. */
public class DataItem(
    public val key: String,
    public val content: HtmlWriter.() -> Unit,
)

/** A plain article with a heading. Pick the heading level that fits the page outline. */
public fun HtmlWriter.cardComponent(
    heading: String,
    level: Int = 3,
    content: HtmlWriter.() -> Unit,
) {
    require(level in 2..6) { "Card heading level must be 2 to 6" }
    require(heading.isNotBlank()) { "Card needs a heading" }
    element("article", attributes = { dataPart("card") }) {
        element("h$level", attributes = { dataPart("card-heading") }) { text(heading) }
        content()
    }
}

/** The label is always text, so tone is never the only signal. */
public fun HtmlWriter.badgeComponent(
    label: String,
    tone: BadgeTone = BadgeTone.NEUTRAL,
) {
    require(label.isNotBlank()) { "Badge needs text" }
    span(attributes = {
        dataPart("badge")
        data("tone", tone.html)
    }) { text(label) }
}

/**
 * An image, or initials when there is none. Set [decorative] when the person's name is already
 * visible next to the avatar.
 */
public fun HtmlWriter.avatarComponent(
    name: String,
    image: HtmlUrl? = null,
    decorative: Boolean = false,
) {
    require(name.isNotBlank()) { "Avatar needs a name" }
    span(attributes = {
        dataPart("avatar")
        if (image == null) {
            if (decorative) aria("hidden", "true") else attribute("role", "img")
            if (!decorative) aria("label", name)
        }
    }) {
        if (image != null) {
            img(attributes = {
                url("src", image)
                attribute("alt", if (decorative) "" else name)
                attribute("loading", "lazy")
                dataPart("avatar-image")
            })
        } else {
            text(initials(name))
        }
    }
}

/** Replaces a table or grid with no records. It is not a live region; announce changes separately. */
public fun HtmlWriter.emptyState(
    title: String,
    message: String,
    level: Int = 2,
    action: (HtmlWriter.() -> Unit)? = null,
) {
    require(level in 2..6) { "Empty state heading level must be 2 to 6" }
    div(attributes = { dataPart("empty") }) {
        element("h$level") { text(title) }
        p { text(message) }
        action?.invoke(this)
    }
}

/** A responsive grid of cards. Each item is a list item with a stable key. */
public fun HtmlWriter.cardGrid(
    label: String,
    items: List<DataItem>,
) {
    require(label.isNotBlank()) { "Card grid needs an accessible name" }
    require(items.map { it.key }.distinct().size == items.size) { "Card grid keys must be unique" }
    ul(attributes = {
        aria("label", label)
        dataPart("grid")
    }) {
        items.forEach { item ->
            li(attributes = { data("item", dataKey(item.key)) }) { item.content(this) }
        }
    }
}

private fun initials(name: String): String =
    name
        .trim()
        .split(Regex("\\s+"))
        .take(2)
        .joinToString("") { word -> String(Character.toChars(word.codePointAt(0))) }
        .uppercase()
