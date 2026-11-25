package dev.woge.examples.m1

import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.LanguageTag
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.host.ResponseStatus
import dev.woge.host.writeTo
import dev.woge.html.BufferedHtmlSink
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

class LocalizedInvoiceTest {
    @Test
    fun `the same exact decimal and calendar values survive all locale contracts`() =
        runBlocking {
            for ((locale, amount, due) in listOf(
                Triple("de-DE", "90071992547409,93", "11.10.2026"),
                Triple("en-US", "90071992547409.93", "10/11/2026"),
                Triple("ar-EG", "٩٠٠٧١٩٩٢٥٤٧٤٠٩٫٩٣", "١١/١٠/٢٠٢٦"),
            )) {
                val page = localizedInvoicePage(context(locale, RequestMethod.GET)).document().markup()
                val direction = if (locale == "ar-EG") "rtl" else "ltr"
                assertTrue(page.contains("""<html lang="$locale" dir="$direction">"""))
                assertTrue(page.contains("""name="serviceDay" id="invoice-service-day" """))
                assertTrue(page.contains("""type="date""""))
                assertTrue(page.contains("""name="formLanguage" value="$locale""""))

                val result = submit(locale, amount, "2026-02-28", due)
                assertEquals(ResponseStatus.SEE_OTHER, result.nativeResult.metadata.status)
                val status =
                    result.patches
                        .single()
                        .html.value
                assertTrue(status.contains("$amount EUR"), status)
                assertTrue(status.contains("2026-02-28"), status)
                assertTrue(status.contains(due), status)
                assertTrue(status.contains("2026-10-11T10:30:00Z"), status)
            }
        }

    @Test
    fun `invalid text and translated field links survive the native document and enhanced replacement`() =
        runBlocking {
            for ((locale, invalidAmount, invalidDue) in listOf(
                Triple("de-DE", "1.234,56", "31.02.2026<&"),
                Triple("en-US", "1,234.56", "02/31/2026<&"),
            )) {
                val validDue = if (locale == "de-DE") "28.02.2026" else "02/28/2026"
                val summary = if (locale == "de-DE") "Bitte Eingaben prüfen" else "Check your entries"
                val result = submit(locale, invalidAmount, "2026-02-29", invalidDue)

                assertEquals(ResponseStatus.BAD_REQUEST, result.metadata.status)
                assertEquals(ResponseStatus.BAD_REQUEST, result.nativeResult.metadata.status)
                assertEquals("invoice-errors", result.focusSummary?.value)
                for (html in listOf(
                    result.nativeResult.document().markup(),
                    result.patches
                        .single()
                        .html.value,
                )) {
                    assertTrue(html.contains(summary), html)
                    assertTrue(html.contains("""value="$invalidAmount""""), html)
                    assertTrue(html.contains("""value="2026-02-29""""), html)
                    assertTrue(html.contains("""value="${invalidDue.removeSuffix("<&")}&lt;&amp;""""), html)
                    for (id in listOf("invoice-amount", "invoice-service-day", "invoice-due-day")) {
                        assertTrue(html.contains("""href="#$id""""), html)
                        assertTrue(html.contains("""aria-describedby="$id-error""""), html)
                    }
                }

                val corrected = submit(locale, if (locale == "de-DE") "1,20" else "1.20", "2026-02-28", validDue)
                assertEquals(ResponseStatus.SEE_OTHER, corrected.nativeResult.metadata.status)
            }
        }

    @Test
    fun `locale switch and tampered form fields are rejected without guessing`() =
        runBlocking {
            assertEquals(
                ResponseStatus.BAD_REQUEST,
                localizedInvoicePage(context("en-GB", RequestMethod.GET)).metadata.status,
            )
            val switched = submit("en-US", "1,20", "2026-02-28", "28.02.2026", "de-DE")
            val patched =
                switched.patches
                    .single()
                    .html.value
            assertEquals(ResponseStatus.BAD_REQUEST, switched.nativeResult.metadata.status)
            assertTrue(patched.contains("The form language changed."))
            assertTrue(patched.contains("""value="1,20""""))
            assertTrue(patched.contains("""name="formLanguage" value="en-US""""))

            for (raw in listOf("", "1,20", "1.234", "1.2e2")) {
                assertEquals(
                    ResponseStatus.BAD_REQUEST,
                    submit("en-US", raw, "2026-02-28", "02/28/2026").nativeResult.metadata.status,
                )
            }

            val tampered =
                execute(
                    "en-US",
                    "amount=1.20&amount=2.40&serviceDay=2026-02-28&dueDay=02%2F28%2F2026" +
                        "&paymentTime=2026-10-11T12%3A30&formLanguage=en-US",
                )
            assertEquals(ResponseStatus.BAD_REQUEST, tampered.nativeResult.metadata.status)
            assertTrue(
                tampered.patches
                    .single()
                    .html.value
                    .contains("""href="#invoice-amount""""),
            )
        }

    @Test
    fun `an offset an alternate zone or ambiguous numeric symbols cannot change the wire policy`() =
        runBlocking {
            val invalidTimes =
                listOf(
                    "",
                    "2026-03-29T02:30+01:00",
                    "2026-03-29T03:30[UTC]",
                    "٢٠٢٦-٠٣-٢٩T٠٣:٣٠",
                    "2026-02-29T12:30",
                )
            val fields =
                mapOf(
                    "amount" to "1.20",
                    "serviceDay" to "2026-02-28",
                    "dueDay" to "02/28/2026",
                    "formLanguage" to "en-US",
                )
            for (time in invalidTimes) {
                val invalid = submit("en-US", fields + ("paymentTime" to time))
                assertEquals(ResponseStatus.BAD_REQUEST, invalid.nativeResult.metadata.status)
                assertTrue(
                    invalid.patches
                        .single()
                        .html.value
                        .contains("""href="#invoice-payment-time""""),
                )
                assertTrue(
                    invalid.patches
                        .single()
                        .html.value
                        .contains("""value="$time""""),
                )
            }

            for (amount in listOf("1.20", "١.٢٠", "١٫20", "١٬٢٣٤٫٥٦", "١٫٢e٢")) {
                val invalid = submit("ar-EG", amount, "2026-02-28", "٢٨/٠٢/٢٠٢٦")
                assertEquals(ResponseStatus.BAD_REQUEST, invalid.nativeResult.metadata.status)
                assertTrue(
                    invalid.patches
                        .single()
                        .html.value
                        .contains("""value="$amount""""),
                )
            }
        }

    private suspend fun submit(
        locale: String,
        amount: String,
        serviceDay: String,
        dueDay: String,
        formLanguage: String = locale,
    ): PageResult.RegionUpdates =
        submit(
            locale,
            mapOf(
                "amount" to amount,
                "serviceDay" to serviceDay,
                "dueDay" to dueDay,
                "paymentTime" to "2026-10-11T12:30",
                "formLanguage" to formLanguage,
            ),
        )

    private suspend fun submit(
        locale: String,
        fields: Map<String, String>,
    ): PageResult.RegionUpdates =
        execute(
            locale,
            fields.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, UTF_8)}" },
        )

    private suspend fun execute(
        locale: String,
        wire: String,
    ): PageResult.RegionUpdates {
        val body = localizedInvoiceDecoder.body().apply { accept(wire.toByteArray(UTF_8)) }
        val result =
            localizedInvoiceAction.execute(
                PageRequest(localizedInvoiceDecoder.submission(body), context(locale, RequestMethod.POST)),
            )
        return assertInstanceOf(PageResult.RegionUpdates::class.java, result)
    }

    private fun context(
        locale: String,
        method: RequestMethod,
    ): RequestContext =
        RequestContext(
            method,
            RequestTrace(RequestId.of("invoice-request"), CorrelationId.of("invoice-test")),
            language = LanguageTag.of(locale.lowercase()),
            security = RequestSecurity(csrf = CsrfVerification.VERIFIED),
        )

    private fun PageResult.document(): PageResult.Document = assertInstanceOf(PageResult.Document::class.java, this)

    private suspend fun PageResult.Document.markup(): String = BufferedHtmlSink().also { writeTo(it) }.content()
}
