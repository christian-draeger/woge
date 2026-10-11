package dev.woge.example.notes

import dev.woge.host.FormElementId
import dev.woge.host.FormErrors
import dev.woge.host.formField
import dev.woge.html.HtmlWriter
import dev.woge.html.IslandModule
import dev.woge.html.IslandProps
import dev.woge.html.IslandRegistry
import dev.woge.html.IslandType
import dev.woge.html.input
import dev.woge.html.island
import dev.woge.html.islandLoader
import dev.woge.html.islandManifest
import dev.woge.html.label
import dev.woge.html.p

private val noteCounter =
    IslandType<Int>("note-counter", 1) { maximum ->
        IslandProps(mapOf("maxLength" to maximum.toString()))
    }
private val noteIslands = IslandRegistry(IslandModule(noteCounter, "/assets/note-counter.js", "2"))

internal fun HtmlWriter.noteIslandAssets() {
    islandManifest(noteIslands)
    islandLoader()
}

internal fun HtmlWriter.noteInput(
    maximum: Int,
    errors: FormErrors<AddNote>,
) {
    island(noteCounter, maximum, key = "note-counter") {
        label(attributes = { attribute("for", "note-text") }) { text("New note") }
        input(attributes = {
            formField(noteTextField, errors, listOf(FormElementId.of("note-count")))
            errors.value(noteTextField)?.let { attribute("value", it) }
            boolean("required")
            attribute("maxlength", maximum.toString())
        })
        p(attributes = {
            attribute("id", "note-count")
            attribute("role", "status")
        }) { text("Up to $maximum UTF-16 code units. Your note is checked on the server.") }
    }
}
