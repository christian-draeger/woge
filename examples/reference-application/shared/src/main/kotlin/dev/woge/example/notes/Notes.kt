package dev.woge.example.notes

import dev.woge.example.rules.DraftPhase
import dev.woge.example.rules.MAX_NOTE_LENGTH
import dev.woge.example.rules.NoteDraft
import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.FormErrors
import dev.woge.host.FormValues
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
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
    // Public demo: same-origin/CSRF verification and persistence are server-only decisions.
    if (context.csrf != CsrfVerification.VERIFIED) {
        return failure(FailureCategory.FORBIDDEN, context.correlationId)
    }
    val draft = NoteDraft(command.text).validating().validated()
    val error = draft.error
    return if (error != null) {
        noteValidation(context, command.text, error)
    } else {
        noteStore.add(command.text.trim())
        check(draft.submitted().phase == DraftPhase.SUBMITTED)
        context.render.updates(NotesRoute.url(NotesInput)) {
            replace(NoteListRegion, noteStore.all())
            replace(NoteStatusRegion, "Note added")
            replace(NoteErrorsRegion, FormErrors<AddNote>(FormValues.EMPTY, emptyList()))
        }
    }
}

public class NotesPage : PageUseCase<NotesInput> {
    override suspend fun open(request: PageRequest<NotesInput>): PageResult = notesDocument(request.context)
}

internal fun notesDocument(
    context: RequestContext,
    errors: FormErrors<AddNote> = FormErrors(FormValues.EMPTY, emptyList()),
    status: ResponseStatus = ResponseStatus.OK,
): PageResult.Document {
    val page = context.render.resume(context.render.epoch)
    return htmlPage(ResponseMetadata(status = status)) {
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
                    region(page.target(NoteErrorsRegion), errors)
                    noteForm(errors)
                }
            }
        }
    }
}

private fun HtmlWriter.noteForm(errors: FormErrors<AddNote>) {
    actionForm(AddNoteAction, attributes = {
        attribute("id", "note-form")
        data("woge-action", "")
        data("woge-status", "note-status")
        data("woge-alert", "note-alert")
        data("woge-error-summary", "note-errors")
        data(
            "woge-failure-message",
            "Could not confirm the result. Reload to check your notes before submitting again.",
        )
    }) {
        noteInput(MAX_NOTE_LENGTH, errors)
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
