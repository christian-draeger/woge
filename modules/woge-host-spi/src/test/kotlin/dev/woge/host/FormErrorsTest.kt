package dev.woge.host

import dev.woge.html.applicationUrl
import dev.woge.html.input
import dev.woge.html.renderHtml
import dev.woge.protocol.PageEpoch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FormErrorsTest {
    private val decoder = FormDecoder(Command.serializer())
    private val field = decoder.field(Command::title, FormElementId.of("title"), serializedName = "task-title")

    @Test
    fun `typed fields require explicit serialized aliases and preserve only selected bounded values`() {
        assertThrows(IllegalArgumentException::class.java) {
            decoder.field(Command::title, FormElementId.of("wrong"))
        }
        val body = decoder.body()
        body.accept("task-title=%3Cprivate%3E".toByteArray())
        val submission = decoder.submission(body)
        val errors = FormErrors(submission.values, listOf(FormError(field, "Invalid <title>")))
        assertEquals("<private>", errors.value(field))
        val html =
            renderHtml {
                input(attributes = {
                    formField(field, errors, listOf(FormElementId.of("title-help")))
                    attribute("value", errors.value(field).orEmpty())
                })
                formFieldErrors(field, errors)
                formErrorSummary(errors, FormElementId.of("errors"), "Check the form")
            }
        assertTrue(html.contains("""name="task-title""""))
        assertTrue(html.contains("""data-woge-state-key="title""""))
        assertTrue(html.contains("""aria-describedby="title-help title-error""""))
        assertTrue(html.contains("""value="&lt;private&gt;""""))
        assertTrue(html.contains("""href="#title""""))
        assertTrue(html.contains("Invalid &lt;title&gt;"))
        assertFalse(html.contains("aria-live") || html.contains("""role="alert""""))
        assertFalse(errors.toString().contains("<private>"))
        assertFalse(
            errors.errors
                .first()
                .toString()
                .contains("Invalid"),
        )
    }

    @Test
    fun `form-wide errors have no fabricated field and error collections are snapshots`() {
        val source = mutableListOf(FormError<Command>(null, "Try another title"))
        val errors = FormErrors(FormValues.EMPTY, source)
        source.clear()
        assertEquals(1, errors.errors.size)
        val html = renderHtml { formErrorSummary(errors, FormElementId.of("errors"), "Check the form") }
        assertTrue(html.contains("<li>Try another title</li>"))
        assertFalse(html.contains("<a "))
        assertThrows(IllegalArgumentException::class.java) { FormError<Command>(null, " ") }
        assertThrows(IllegalArgumentException::class.java) { FormElementId.of("unsafe#fragment") }
    }

    @Test
    fun `validation update result preserves native HTML status and names one focus summary`() {
        val native = htmlPage(ResponseMetadata(status = ResponseStatus.BAD_REQUEST)) { text("Native errors") }
        val target =
            object : PageRegion<String>("errors") {
                override fun render(
                    writer: dev.woge.html.HtmlWriter,
                    input: String,
                ) {
                    writer.text(input)
                }
            }.target(PageIdentity(PageEpoch.of("page-1"), RenderIdentitySecret.of(ByteArray(32))))
        val result = actionValidationUpdates(native, FormElementId.of("errors")) { replace(target, "Errors") }
        assertEquals(native, result.nativeResult)
        assertEquals(ResponseStatus.BAD_REQUEST, result.metadata.status)
        assertEquals(FormElementId.of("errors"), result.focusSummary)
        assertThrows(IllegalArgumentException::class.java) {
            actionValidationUpdates(htmlPage { text("OK") }, FormElementId.of("errors")) { replace(target, "Invalid") }
        }
        val success = actionRegionUpdates(applicationUrl("/done")) { replace(target, "Done") }
        assertEquals(null, success.focusSummary)
    }

    @Serializable
    private data class Command(
        @SerialName("task-title") val title: String,
    )
}
