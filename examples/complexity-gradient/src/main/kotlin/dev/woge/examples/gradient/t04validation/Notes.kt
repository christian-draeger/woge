package dev.woge.examples.gradient.t04validation

import dev.woge.host.ActionExecutor
import dev.woge.host.FormDecoder
import dev.woge.host.FormElementId
import dev.woge.host.FormError
import dev.woge.host.FormErrors
import dev.woge.host.FormSubmission
import dev.woge.host.FormValidation
import dev.woge.host.FormValues
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.WogeAction
import dev.woge.host.WogeRoute
import dev.woge.host.actionForm
import dev.woge.host.formErrorSummary
import dev.woge.host.formField
import dev.woge.host.formFieldErrors
import dev.woge.host.htmlPage
import dev.woge.host.redirect
import dev.woge.host.withFormValidation
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
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.html.ul
import kotlinx.serialization.Serializable

internal class NoteStore {
    private val notes = mutableListOf("Water the herbs")

    fun all(): List<String> = synchronized(notes) { notes.toList() }

    fun add(text: String) {
        synchronized(notes) { notes += text }
    }
}

internal val noteStore = NoteStore()

@WogeRoute("/validated-notes")
public data object ValidatedNotesInput

@Serializable
public data class AddNote(
    val text: String,
)

public val addNoteForm: FormDecoder<AddNote> = FormDecoder(AddNote.serializer())

private val textField = addNoteForm.field(AddNote::text, FormElementId.of("note-text"))
private val errorSummary = FormElementId.of("note-errors")
private const val MAX_NOTE_LENGTH = 80
private const val NOTE_MESSAGE = "Enter a note of 1 to 80 characters."

/** Business rules live in the action; the same errors model renders them. */
@WogeAction("add-validated-note")
@Suppress("UnusedParameter")
public suspend fun addValidatedNote(
    command: AddNote,
    context: RequestContext,
): PageResult {
    val text = command.text.trim()
    return if (text.isEmpty() || text.length > MAX_NOTE_LENGTH) {
        notesDocument(
            FormErrors(FormValues.EMPTY, listOf(FormError(textField, NOTE_MESSAGE))),
            command.text,
            ResponseStatus.BAD_REQUEST,
        )
    } else {
        noteStore.add(text)
        redirect(ValidatedNotesRoute.url(ValidatedNotesInput))
    }
}

/** Renders the 400 page when the submission cannot be decoded at all. */
private val decodingProblems =
    PageUseCase<FormValidation> { request ->
        val errors = FormErrors(request.input.values, request.input.errors.map { FormError(textField, NOTE_MESSAGE) })
        notesDocument(
            errors,
            request.input.values
                .first("text")
                .orEmpty(),
            ResponseStatus.BAD_REQUEST,
        )
    }

public val addNoteAction: ActionExecutor<FormSubmission<AddNote>> =
    AddValidatedNoteAction.withFormValidation(
        decodingProblems,
    )

public class NotesPage : PageUseCase<ValidatedNotesInput> {
    override suspend fun open(request: PageRequest<ValidatedNotesInput>): PageResult =
        notesDocument(FormErrors(FormValues.EMPTY, emptyList()), "", ResponseStatus.OK)
}

private fun notesDocument(
    errors: FormErrors<AddNote>,
    draft: String,
    status: ResponseStatus,
): PageResult =
    htmlPage(ResponseMetadata(status = status)) {
        doctype()
        html(attributes = { attribute("lang", "en") }) {
            head {
                meta { attribute("charset", "utf-8") }
                metadata("viewport", "width=device-width, initial-scale=1")
                title("Notes · Validation")
                stylesheet(applicationUrl("/assets/validation/site.css"))
            }
            body {
                main {
                    h1 { text("Garden notes") }
                    ul { noteStore.all().forEach { li { text(it) } } }
                    actionForm(AddValidatedNoteAction) {
                        formErrorSummary(errors, errorSummary, "Check the note")
                        label(attributes = { attribute("for", textField.id.value) }) { text("New note") }
                        input(attributes = {
                            formField(textField, errors)
                            attribute("value", draft)
                        })
                        formFieldErrors(textField, errors)
                        button { text("Add note") }
                    }
                }
            }
        }
    }
