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
    return context.render.validation(
        notesDocument(context, errors, ResponseStatus.BAD_REQUEST),
        noteErrorSummary,
    ) {
        replace(NoteErrorsRegion, errors)
    }
}

@WogeRegion
internal fun HtmlWriter.noteErrors(errors: FormErrors<AddNote>) {
    formErrorSummary(errors, noteErrorSummary, "Check your note")
    formFieldErrors(noteTextField, errors)
}
