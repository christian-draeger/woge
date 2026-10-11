@file:OptIn(ExperimentalJsExport::class)

package dev.woge.example.rules

@JsExport
fun reviewNote(
    text: String,
    maximum: String,
    version: Int,
): Array<String> {
    validateNoteProps(mapOf("maxLength" to maximum), version)
    val draft = noteDraftFromFields(mapOf("text" to text), version).validating().validated()
    return arrayOf(
        draft.phase.name,
        draft.error
            ?.code
            ?.name
            .orEmpty(),
        draft.error?.message.orEmpty(),
    )
}
