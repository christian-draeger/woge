package dev.woge.examples.gradient.t05enhancedaction

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
import dev.woge.html.input
import dev.woge.html.label
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

@WogeRoute("/enhanced-notes")
public data object EnhancedNotesInput

@Serializable
public data class AddNote(
    val text: String,
)

public val addNoteForm: FormDecoder<AddNote> = FormDecoder(AddNote.serializer())

/** One implementation: native posts get a 303 redirect, enhanced posts get the two region updates. */
@WogeAction("add-enhanced-note")
public suspend fun addEnhancedNote(
    command: AddNote,
    context: RequestContext,
): PageResult {
    if (command.text.isBlank()) return failure(FailureCategory.BAD_REQUEST, context.correlationId)
    noteStore.add(command.text.trim())
    return context.render.updates(EnhancedNotesRoute.url(EnhancedNotesInput)) {
        replace(NoteListRegion, noteStore.all())
        replace(NoteStatusRegion, "Note added")
    }
}

public class NotesPage : PageUseCase<EnhancedNotesInput> {
    override suspend fun open(request: PageRequest<EnhancedNotesInput>): PageResult {
        val page = request.context.render
        return htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    page.metadata(this)
                    title("Notes · Enhanced action")
                    stylesheet(applicationUrl("/assets/enhanced-action/site.css"))
                    moduleScript(applicationUrl("/assets/woge/index.js"))
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
    actionForm(AddEnhancedNoteAction, attributes = {
        attribute("id", "note-form")
        data("woge-action", "")
        data("woge-status", "note-status")
        data("woge-alert", "note-alert")
        data("woge-failure-message", "The note was not added. Reload the page and try again.")
    }) {
        label(attributes = { attribute("for", "note-text") }) { text("New note") }
        input(attributes = {
            attribute("id", "note-text")
            attribute("name", "text")
            boolean("required")
        })
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
