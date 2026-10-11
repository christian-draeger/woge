package dev.woge.example.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NoteDraftTest {
    @Test
    fun boundariesAndErrorParity() {
        for (text in listOf("", " ", "\t\n", "\u00A0", "\uFEFF")) {
            assertEquals(NoteError(NoteErrorCode.MISSING, "Enter a note."), validateNote(text))
        }
        assertNull(validateNote("a".repeat(120)))
        assertNull(validateNote("😀".repeat(60)))
        assertEquals(
            NoteError(NoteErrorCode.MALFORMED, "Use at most 120 UTF-16 code units."),
            validateNote("😀".repeat(61)),
        )
        assertNull(validateNote("<script> & \"quoted\""))
    }

    @Test
    fun transitionsNeverSubmitAnInvalidDraft() {
        val draft = NoteDraft("A note")
        assertEquals(DraftPhase.DRAFT, draft.phase)
        val pending = draft.validating()
        assertEquals(DraftPhase.VALIDATING, pending.phase)
        val ready = pending.validated()
        assertEquals(DraftPhase.READY, ready.phase)
        assertEquals(DraftPhase.SUBMITTED, ready.submitted().phase)
        val rejected = ready.edited(" ").validating().validated()
        assertEquals(DraftPhase.REJECTED, rejected.phase)
        assertFailsWith<IllegalStateException> { rejected.submitted() }
        assertFailsWith<IllegalStateException> { draft.submitted() }
        assertFailsWith<IllegalStateException> { draft.validated() }
        assertFailsWith<IllegalStateException> { ready.validating() }
        assertEquals(DraftPhase.DRAFT, rejected.edited("fixed").phase)
    }

    @Test
    fun stringFieldSerializationOnlyRestoresUntrustedDrafts() {
        val text = "😀 <script> \"quote\" \n"
        val saved = NoteDraft(text).validating().validated().submitted()
        assertEquals(NoteDraft(text), noteDraftFromFields(saved.toFields(), 1))
        assertFailsWith<IllegalArgumentException> { noteDraftFromFields(emptyMap(), 1) }
        assertFailsWith<IllegalArgumentException> { noteDraftFromFields(mapOf("text" to text), 2) }
        assertFailsWith<IllegalArgumentException> {
            noteDraftFromFields(mapOf("text" to text, "phase" to "SUBMITTED"), 1)
        }
        validateNoteProps(mapOf("maxLength" to "120"), 1)
        for (maximum in listOf("0120", "120.0", "121", "-1", "NaN", "")) {
            assertFailsWith<IllegalArgumentException> { validateNoteProps(mapOf("maxLength" to maximum), 1) }
        }
        assertFailsWith<IllegalArgumentException> { validateNoteProps(mapOf("maxLength" to "120"), 2) }
    }
}
