package dev.woge.example.rules

const val MAX_NOTE_LENGTH: Int = 120

enum class DraftPhase { DRAFT, VALIDATING, READY, SUBMITTED, REJECTED }

// Names deliberately match the host FormErrorCode vocabulary without depending on JVM host APIs.
enum class NoteErrorCode { MISSING, MALFORMED }

data class NoteError(
    val code: NoteErrorCode,
    val message: String,
)

data class NoteDraft(
    val text: String,
    val phase: DraftPhase = DraftPhase.DRAFT,
    val error: NoteError? = null,
) {
    fun validating(): NoteDraft {
        check(phase == DraftPhase.DRAFT)
        return copy(phase = DraftPhase.VALIDATING)
    }

    fun validated(): NoteDraft {
        check(phase == DraftPhase.VALIDATING)
        val problem = validateNote(text)
        return copy(phase = if (problem == null) DraftPhase.READY else DraftPhase.REJECTED, error = problem)
    }

    fun edited(value: String): NoteDraft = NoteDraft(value)

    // This transition is bookkeeping, not permission to persist. Only the server acknowledges a save.
    fun submitted(): NoteDraft {
        check(phase == DraftPhase.READY)
        return copy(phase = DraftPhase.SUBMITTED)
    }

    fun toFields(): Map<String, String> = mapOf("text" to text)
}

fun validateNote(text: String): NoteError? =
    when {
        text.all { it in NOTE_WHITESPACE } -> NoteError(NoteErrorCode.MISSING, "Enter a note.")
        text.length > MAX_NOTE_LENGTH ->
            NoteError(NoteErrorCode.MALFORMED, "Use at most 120 UTF-16 code units.")
        else -> null
    }

fun noteDraftFromFields(
    fields: Map<String, String>,
    version: Int,
): NoteDraft {
    require(version == 1 && fields.keys == setOf("text")) { "Invalid note fields" }
    return NoteDraft(requireNotNull(fields["text"]))
}

fun validateNoteProps(
    fields: Map<String, String>,
    version: Int,
) {
    require(version == 1 && fields == mapOf("maxLength" to MAX_NOTE_LENGTH.toString())) { "Invalid note props" }
}

// Explicit set avoids platform-dependent Unicode whitespace classification (e.g. NBSP and BOM).
private const val NOTE_WHITESPACE =
    "\u0009\u000A\u000B\u000C\u000D \u0085\u00A0\u1680" +
        "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF"
