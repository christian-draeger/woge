package dev.woge.example.notes

import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.WogeAction
import dev.woge.host.WogeRegion
import dev.woge.host.WogeRoute
import dev.woge.host.actionForm
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.region
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.title
import kotlinx.serialization.Serializable

internal class NoteStore {
    private val notes = mutableListOf("Water the herbs")

    fun all(): List<String> = synchronized(notes) { notes.toList() }

    fun add(text: String) {
        synchronized(notes) { notes += text }
    }
}

internal val noteStore = NoteStore()

@WogeRoute("/notes")
public data object NotesInput

private const val MAX_NOTE_LENGTH = 120

@Serializable
public data class AddNote(
    val text: String,
)

public val addNoteForm: FormDecoder<AddNote> = FormDecoder(AddNote.serializer())

/** One implementation: native posts get a 303 redirect, enhanced posts get the two region updates. */
@WogeAction("add-note")
public suspend fun addNote(
    command: AddNote,
    context: RequestContext,
): PageResult {
    if (command.text.isBlank() ||
        command.text.length > MAX_NOTE_LENGTH
    ) {
        return failure(FailureCategory.BAD_REQUEST, context.correlationId)
    }
    noteStore.add(command.text.trim())
    return context.render.updates(NotesRoute.url(NotesInput)) {
        replace(NoteListRegion, noteStore.all())
        replace(NoteStatusRegion, "Note added")
    }
}

public class NotesPage : PageUseCase<NotesInput> {
    override suspend fun open(request: PageRequest<NotesInput>): PageResult {
        val page = request.context.render
        return htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    page.metadata(this)
                    title("Notes · Woge quickstart")
                    stylesheet(applicationUrl("/assets/notes.css"))
                    moduleScript(applicationUrl("/assets/woge/index.js"))
                    noteIslandAssets()
                }
                body {
                    main {
                        h1 { text("Garden notes") }
                        region(page.target(NoteListRegion), noteStore.all(), elementName = "ul")
                        region(page.target(NoteStatusRegion), "", attributes = {
                            attribute("id", "note-status")
                            attribute("role", "status")
                        })
                        p(attributes = {
                            attribute("id", "note-alert")
                            attribute("role", "alert")
                        }) {}
                        noteForm()
                    }
                }
            }
        }
    }
}

private fun HtmlWriter.noteForm() {
    actionForm(AddNoteAction, attributes = {
        attribute("id", "note-form")
        data("woge-action", "")
        data("woge-status", "note-status")
        data("woge-alert", "note-alert")
        data(
            "woge-failure-message",
            "Could not confirm the result. Reload to check your notes before submitting again.",
        )
    }) {
        noteInput(MAX_NOTE_LENGTH)
        button { text("Add note") }
    }
}

@WogeRegion
internal fun HtmlWriter.noteList(notes: List<String>) {
    notes.forEach { li { text(it) } }
}

@WogeRegion
internal fun HtmlWriter.noteStatus(message: String) {
    p { text(message) }
}
