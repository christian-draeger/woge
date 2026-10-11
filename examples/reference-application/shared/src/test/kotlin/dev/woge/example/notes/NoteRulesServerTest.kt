package dev.woge.example.notes

import dev.woge.example.rules.NoteDraft
import dev.woge.example.rules.noteDraftFromFields
import dev.woge.example.rules.validateNote
import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.FormProblem
import dev.woge.host.FormResult
import dev.woge.host.PageResult
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.html.HtmlSink
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class NoteRulesServerTest {
    @Test
    fun `native serialization matches shared string fields and never accepts client phases`() {
        val text = "😀 <script> & \"quote\"\n"
        val encoded = "text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
        val command = (addNoteForm.decode(encoded.toByteArray()) as FormResult.Decoded).command
        assertEquals(noteDraftFromFields(mapOf("text" to text), 1), NoteDraft(command.text))
        val json = Json.encodeToString(AddNote.serializer(), command)
        assertEquals(command, Json.decodeFromString(AddNote.serializer(), json))
        val forged = addNoteForm.decode("text=fine&phase=SUBMITTED".toByteArray()) as FormResult.Rejected
        assertTrue(forged.problem is FormProblem.Fields)
    }

    @Test
    fun `server revalidates every note and returns matching codes without mutation`() =
        runTest {
            val before = noteStore.all()
            for (text in listOf(" ", "\uFEFF", "😀".repeat(61))) {
                val error = requireNotNull(validateNote(text))
                val result = addNote(AddNote(text), context()) as PageResult.RegionUpdates
                assertEquals(400, result.metadata.status.code)
                assertEquals("note-errors", result.focusSummary?.value)
                val expected = "text: ${error.code} — ${error.message}"
                assertTrue(
                    result.patches
                        .single()
                        .html.value
                        .contains(expected),
                )
                val native = result.nativeResult as PageResult.Document
                val html = StringBuilder()
                native.frames.collect { it.writeTo(HtmlSink(html::append)) }
                assertTrue(html.contains(expected))
                assertFalse(html.contains("<script>"))
            }
            assertEquals(before, noteStore.all())
        }

    @Test
    fun `client valid is not server permission and success is a real save`() =
        runTest {
            val before = noteStore.all()
            val refused =
                addNote(AddNote("Locally valid"), context(CsrfVerification.NOT_REQUIRED)) as PageResult.Failure
            assertEquals(FailureCategory.FORBIDDEN, refused.failure.category)
            assertEquals(before, noteStore.all())
            val text = "Server saved <script> & note"
            val result = addNote(AddNote(text), context()) as PageResult.RegionUpdates
            assertEquals(before + text, noteStore.all())
            assertEquals(303, result.nativeResult.metadata.status.code)
            assertTrue(
                result.patches
                    .first()
                    .html.value
                    .contains("&lt;script&gt;"),
            )
        }
}

private fun context(csrf: CsrfVerification = CsrfVerification.VERIFIED): RequestContext =
    RequestContext(
        RequestMethod.POST,
        RequestTrace(RequestId.of("notes-test"), CorrelationId.of("notes-test")),
        security = RequestSecurity(csrf = csrf),
    )
