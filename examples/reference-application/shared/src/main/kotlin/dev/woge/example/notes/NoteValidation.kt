package dev.woge.example.notes

import dev.woge.example.rules.NoteError
import dev.woge.host.FormElementId
import dev.woge.host.FormError
import dev.woge.host.FormErrorCode
import dev.woge.host.FormErrors
import dev.woge.host.FormValues
import dev.woge.host.PageResult
import dev.woge.host.RequestContext
import dev.woge.host.ResponseStatus
import dev.woge.host.WogeRegion
import dev.woge.host.actionValidationUpdates
import dev.woge.host.formErrorSummary
import dev.woge.host.formFieldErrors
import dev.woge.html.HtmlWriter

internal val noteTextField = addNoteForm.field(AddNote::text, FormElementId.of("note-text"))
private val noteErrorSummary = FormElementId.of("note-errors")

internal fun noteValidation(
    context: RequestContext,
    text: String,
    error: NoteError,
): PageResult {
    val code = FormErrorCode.valueOf(error.code.name)
    val errors =
        FormErrors(
            FormValues.of(mapOf("text" to listOf(text))),
            listOf(FormError(noteTextField, "text: $code — ${error.message}")),
        )
    // Reuse the request-owned render session's ordering, not a client phase or a new page identity.
    val prepared = context.render.updates(NotesRoute.url(NotesInput)) { replace(NoteErrorsRegion, errors) }
    val patch = prepared.patches.single()
    return actionValidationUpdates(
        notesDocument(context, errors, ResponseStatus.BAD_REQUEST),
        noteErrorSummary,
        interaction = patch.interactionSequence,
    ) {
        replace(context.render.resume(context.render.epoch).target(NoteErrorsRegion), errors, patch.revision.base)
    }
}

@WogeRegion
internal fun HtmlWriter.noteErrors(errors: FormErrors<AddNote>) {
    formErrorSummary(errors, noteErrorSummary, "Check your note")
    formFieldErrors(noteTextField, errors)
}
