package dev.woge.host

import dev.woge.html.BufferedHtmlSink
import dev.woge.html.applicationUrl
import dev.woge.html.input
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FormSubmissionTest {
    private val decoder = FormDecoder(Command.serializer())
    private val context =
        RequestContext(RequestMethod.POST, RequestTrace(RequestId.of("native"), CorrelationId.of("native")))

    @Test
    fun `field errors retain bounded text without executing the mutation`() =
        runBlocking {
            var mutations = 0
            val executor =
                ActionExecutor<Command> {
                    mutations++
                    redirect(applicationUrl("/complete"))
                }
            val validated =
                executor.withFormValidation(
                    PageUseCase { request ->
                        assertEquals(listOf(FormFieldError("count", FormErrorCode.MALFORMED)), request.input.errors)
                        assertEquals(listOf("private"), request.input.values.all("title"))
                        assertFalse(request.input.toString().contains("private"))
                        assertEquals(context, request.context)
                        htmlPage(metadata = ResponseMetadata(status = ResponseStatus.BAD_REQUEST)) {
                            input(attributes = {
                                attribute(
                                    "value",
                                    request.input.values
                                        .first("title")
                                        .orEmpty(),
                                )
                            })
                        }
                    },
                )
            val submission = submit("title=private&count=no")
            assertFalse(submission.toString().contains("private"))
            val invalid = validated.execute(PageRequest(submission, context))
            val sink = BufferedHtmlSink()
            (invalid as PageResult.Document).writeTo(sink)
            assertEquals("""<input value="private">""", sink.content())
            assertEquals(ResponseStatus.BAD_REQUEST, invalid.metadata.status)
            assertEquals(0, mutations)

            val success = validated.execute(PageRequest(submit("title=ok&count=1"), context))
            assertEquals(ResponseStatus.SEE_OTHER, success.metadata.status)
            assertEquals("/complete", (success as PageResult.Redirect).location.value)
            assertEquals(1, mutations)
        }

    @Test
    fun `malformed encoding and exceeded budgets never expose partial fields or invoke validation`() =
        runBlocking {
            val validated =
                ActionExecutor<Command> { error("Mutation must not run") }
                    .withFormValidation(PageUseCase { error("Field renderer must not run") })
            val malformed = submit("title=private&count=%GG")
            assertEquals(null, malformed.values.first("title"))
            assertEquals(
                ResponseStatus.BAD_REQUEST,
                validated.execute(PageRequest(malformed, context)).metadata.status,
            )
            val limited = FormDecoder(Command.serializer(), FormLimits(bodyBytes = 1))
            val body = limited.body()
            body.accept("title=private".toByteArray())
            val exhausted = limited.submission(body)
            assertEquals(null, exhausted.values.first("title"))
            assertEquals(
                ResponseStatus.PAYLOAD_TOO_LARGE,
                validated.execute(PageRequest(exhausted, context)).metadata.status,
            )
        }

    @Test
    fun `native action markup fixes method encoding and URL and text snapshots are immutable`() =
        runBlocking {
            val descriptor =
                object : ActionDescriptor<Command>(ActionId.of("native")) {
                    override suspend fun execute(request: PageRequest<Command>): PageResult =
                        redirect(applicationUrl("/complete"))
                }
            val sink = BufferedHtmlSink()
            htmlPage { actionForm(descriptor) { input(attributes = { attribute("name", "title") }) } }.writeTo(sink)
            assertEquals(
                """<form method="post" enctype="application/x-www-form-urlencoded" accept-charset="UTF-8" """ +
                    """action="/woge-actions/native"><input name="title"></form>""",
                sink.content(),
            )
            val original = mutableListOf("first", "second")
            val values = FormValues(mapOf("title" to original))
            original.clear()
            assertEquals(listOf("first", "second"), values.all("title"))
            assertThrows(UnsupportedOperationException::class.java) { (values.all("title") as MutableList).clear() }
        }

    private fun submit(encoded: String): FormSubmission<Command> {
        val body = decoder.body()
        body.accept(encoded.toByteArray())
        return decoder.submission(body)
    }

    @Serializable
    private data class Command(
        val title: String,
        val count: Int,
    )
}
