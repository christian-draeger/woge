package dev.woge.examples.m1

import dev.woge.host.ACTION_VALIDATION_HEADER
import dev.woge.host.PageUseCase
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import dev.woge.spring.webflux.WebFluxPageInput
import dev.woge.spring.webflux.WogeWebFluxHandlers
import dev.woge.spring.webflux.webFluxSubmission
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.adapter.WebHttpHandlerBuilder
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver
import reactor.netty.http.server.HttpServer
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.Locale

internal enum class InvoiceHttpAdapter {
    WEBFLUX,
    KTOR,
}

internal class LocalizedInvoiceHttpTest {
    @ParameterizedTest
    @EnumSource(InvoiceHttpAdapter::class)
    fun `resolved locale drives native and enhanced invoice validation and correction`(adapter: InvoiceHttpAdapter) {
        withInvoiceHost(adapter) { host ->
            for (invoice in InvoiceCase.entries) {
                val page = host.page(invoice.preferences)
                assertEquals(invoice.language, page.required("html").attr("lang"))
                assertEquals(invoice.direction, page.required("html").attr("dir"))
                assertEquals(invoice.direction, page.required("form").attr("dir"))
                assertEquals(invoice.amountLabel, page.required("label[for=invoice-amount]").text())
                assertEquals("date", page.required("#invoice-service-day").attr("type"))
                assertEquals("text", page.required("#invoice-payment-time").attr("type"))
                assertEquals("ltr", page.required("#invoice-payment-time").attr("dir"))
                assertEquals(ValidateLocalizedInvoiceAction.path, page.required("form").attr("action"))
                assertEquals(invoice.language, page.required("input[name=formLanguage]").attr("value"))

                val invalid = invoice.form(invoice.invalidAmount, "2026-02-29", INVALID_DUE_DAY, INVALID_PAYMENT_TIME)
                val native = host.submit(invoice.preferences, invalid)
                assertEquals(400, native.statusCode())
                assertTrue(native.header("Content-Type").startsWith("text/html"))
                val nativeForm = native.document()
                assertEquals(invoice.language, nativeForm.required("html").attr("lang"))
                assertEquals(invoice.direction, nativeForm.required("html").attr("dir"))

                val enhanced = host.submit(invoice.preferences, invalid, page.enhancement())
                assertEquals("invoice-errors", enhanced.header(ACTION_VALIDATION_HEADER))
                val validation = enhanced.singlePatch(400)
                assertEquals(page.formRegion(), validation.target.region.value)
                val enhancedForm = Jsoup.parseBodyFragment(validation.html.value)
                for (wireHtml in listOf(native.body().toString(UTF_8), validation.html.value)) {
                    assertTrue(wireHtml.contains("31/31/2026 ñ&lt;&amp;"))
                    assertTrue(wireHtml.contains("2026-03-29T02:30&lt;&amp;"))
                }
                for (rendered in listOf(nativeForm, enhancedForm)) {
                    assertEquals(invoice.language, rendered.required("form").attr("lang"))
                    assertEquals(invoice.direction, rendered.required("form").attr("dir"))
                    assertEquals(invoice.summary, rendered.required("#invoice-errors h2").text())
                    assertEquals("-1", rendered.required("#invoice-errors").attr("tabindex"))
                    assertFieldErrors(rendered, invalid)
                }
                assertEquals(
                    nativeForm.select("[id$=-error]").map { it.text() },
                    enhancedForm.select("[id$=-error]").map { it.text() },
                )

                val corrected = invoice.form()
                val redirect = host.submit(invoice.preferences, corrected)
                assertEquals(303, redirect.statusCode())
                assertEquals(INVOICE_PATH, redirect.header("Location"))

                val accepted =
                    host.submit(invoice.preferences, corrected, page.enhancement(validation)).singlePatch(200)
                assertEquals(page.required("#invoice-status").attr("data-woge-region"), accepted.target.region.value)
                assertEquals(2L, accepted.interactionSequence.value)
                val confirmation = Jsoup.parseBodyFragment(accepted.html.value)
                assertEquals(invoice.accepted, confirmation.text())
                assertEquals(invoice.language, confirmation.required("span").attr("lang"))
                assertEquals(invoice.direction, confirmation.required("span").attr("dir"))
            }
        }
    }

    @ParameterizedTest
    @EnumSource(InvoiceHttpAdapter::class)
    fun `a German form submitted in English gets a translated stale-form error`(adapter: InvoiceHttpAdapter) {
        withInvoiceHost(adapter) { host ->
            val page = host.page(InvoiceCase.GERMAN.preferences)
            val displayedLanguage = page.required("input[name=formLanguage]").attr("value")
            assertEquals("de-DE", displayedLanguage)
            val stale = InvoiceCase.GERMAN.form() + ("formLanguage" to displayedLanguage)

            // None of these preferred locales is supported. The host uses the configured US default.
            val unsupportedPreferences = "de-AT,fr-FR;q=0.8"
            val native = host.submit(unsupportedPreferences, stale)
            assertEquals(400, native.statusCode())
            val nativeForm = native.document()
            assertEquals("en-US", nativeForm.required("html").attr("lang"))

            val enhanced = host.submit(unsupportedPreferences, stale, page.enhancement())
            assertEquals("invoice-errors", enhanced.header(ACTION_VALIDATION_HEADER))
            val validation = enhanced.singlePatch(400)
            assertEquals(page.formRegion(), validation.target.region.value)

            for (rendered in listOf(nativeForm, Jsoup.parseBodyFragment(validation.html.value))) {
                assertEquals("Amount (EUR)", rendered.required("label[for=invoice-amount]").text())
                assertEquals("Check your entries", rendered.required("#invoice-errors h2").text())
                assertTrue(rendered.required("#invoice-errors").text().contains("The form language changed."))
                assertTrue(rendered.select("#invoice-errors a, [aria-invalid=true]").isEmpty())
                assertEquals("en-US", rendered.required("input[name=formLanguage]").attr("value"))
                assertValues(rendered, stale)
            }

            val corrected = InvoiceCase.ENGLISH.form()
            val redirect = host.submit(unsupportedPreferences, corrected)
            assertEquals(303, redirect.statusCode())
            assertEquals(INVOICE_PATH, redirect.header("Location"))

            val accepted = host.submit(unsupportedPreferences, corrected, page.enhancement(validation)).singlePatch(200)
            assertEquals(InvoiceCase.ENGLISH.accepted, Jsoup.parseBodyFragment(accepted.html.value).text())
        }
    }

    @ParameterizedTest
    @EnumSource(InvoiceHttpAdapter::class)
    fun `Berlin rejects DST gaps and overlaps and keeps the submitted payment time`(adapter: InvoiceHttpAdapter) {
        withInvoiceHost(adapter) { host ->
            val invoice = InvoiceCase.ARABIC
            val page = host.page(invoice.preferences)
            for ((time, message) in listOf(
                "2026-03-29T02:30" to "هذا الوقت المحلي غير موجود في برلين بسبب تقديم الساعة. اختر وقتًا آخر.",
                "2026-10-25T02:30" to "يتكرر هذا الوقت المحلي في برلين بسبب تأخير الساعة. اختر وقتًا لا يتكرر.",
            )) {
                val fields = invoice.form(paymentTime = time)
                val native = host.submit(invoice.preferences, fields)
                assertEquals(400, native.statusCode())
                val enhanced = host.submit(invoice.preferences, fields, page.enhancement())
                val patch = enhanced.singlePatch(400)
                for (rendered in listOf(native.document(), Jsoup.parseBodyFragment(patch.html.value))) {
                    assertValues(rendered, fields)
                    assertEquals(listOf("invoice-payment-time"), rendered.select("[aria-invalid=true]").map { it.id() })
                    assertEquals(message, rendered.required("#invoice-payment-time-error").text())
                    assertEquals("#invoice-payment-time", rendered.required("#invoice-errors a").attr("href"))
                }
                assertEquals("invoice-errors", enhanced.header(ACTION_VALIDATION_HEADER))
            }

            for ((time, utc) in listOf(
                "2026-03-29T01:30" to "2026-03-29T00:30:00Z",
                "2026-03-29T03:30" to "2026-03-29T01:30:00Z",
                "2026-10-25T01:30" to "2026-10-24T23:30:00Z",
                "2026-10-25T03:30" to "2026-10-25T02:30:00Z",
            )) {
                val corrected = invoice.form(paymentTime = time)
                assertEquals(303, host.submit(invoice.preferences, corrected).statusCode())
                val accepted = host.submit(invoice.preferences, corrected, page.enhancement()).singlePatch(200)
                assertTrue(Jsoup.parseBodyFragment(accepted.html.value).text().contains(utc))
            }
        }
    }

    @ParameterizedTest
    @EnumSource(InvoiceHttpAdapter::class)
    fun `changing the resolved language to Arabic preserves LTR literals and changes the replaced form direction`(
        adapter: InvoiceHttpAdapter,
    ) {
        withInvoiceHost(adapter) { host ->
            val page = host.page(InvoiceCase.ENGLISH.preferences)
            val invoice = InvoiceCase.ARABIC
            val stale = InvoiceCase.ENGLISH.form()
            val native = host.submit(invoice.preferences, stale)
            assertEquals(400, native.statusCode())
            assertEquals("rtl", native.document().required("html").attr("dir"))

            val enhanced = host.submit(invoice.preferences, stale, page.enhancement()).singlePatch(400)
            for (rendered in listOf(native.document(), Jsoup.parseBodyFragment(enhanced.html.value))) {
                assertValues(rendered, stale)
                assertEquals("rtl", rendered.required("form").attr("dir"))
                assertEquals(invoice.language, rendered.required("form").attr("lang"))
                assertEquals(invoice.language, rendered.required("[name=formLanguage]").attr("value"))
                assertTrue(rendered.select("[aria-invalid=true]").isEmpty())
            }

            val corrected = invoice.form()
            assertEquals(303, host.submit(invoice.preferences, corrected).statusCode())
            val accepted = host.submit(invoice.preferences, corrected, page.enhancement(enhanced)).singlePatch(200)
            assertEquals(invoice.accepted, Jsoup.parseBodyFragment(accepted.html.value).text())
        }
    }

    @Test
    fun `Ktor skips excluded preferences and retains same-origin verification`() {
        withInvoiceHost(InvoiceHttpAdapter.KTOR) { host ->
            val german = host.page("en-US;q=0,DE-de;q=0.2")
            assertEquals("de-DE", german.required("html").attr("lang"))
            val fallback = host.page("de-DE;q=0,fr-FR")
            assertEquals("en-US", fallback.required("html").attr("lang"))

            val rejected =
                host.submit(
                    InvoiceCase.GERMAN.preferences,
                    InvoiceCase.GERMAN.form(),
                    german.enhancement(),
                    requestOrigin = "https://other.example",
                )
            assertEquals(403, rejected.statusCode())
        }
    }

    private fun assertFieldErrors(
        markup: Element,
        fields: Map<String, String>,
    ) {
        assertValues(markup, fields)
        for (id in FIELD_IDS.values) {
            assertEquals("true", markup.required("#$id").attr("aria-invalid"))
            assertEquals("$id-error", markup.required("#$id").attr("aria-describedby"))
            assertEquals("#$id", markup.required("#invoice-errors a[href='#$id']").attr("href"))
            assertTrue(markup.required("#$id-error").text().isNotBlank())
        }
    }

    private fun assertValues(
        markup: Element,
        fields: Map<String, String>,
    ) {
        for ((name, id) in FIELD_IDS) {
            val field = markup.required("#$id")
            assertEquals(name, field.attr("name"))
            assertEquals(fields.getValue(name), field.attr("value"))
        }
    }

    private fun Document.formRegion(): String = required("[data-woge-region]:has(form)").attr("data-woge-region")

    private fun Document.enhancement(previous: ReplacePatch? = null): Map<String, String> =
        mapOf(
            "Accept" to PatchStreamV1.MEDIA_TYPE,
            "Woge-Page-Epoch" to required("meta[name=woge-page-epoch]").attr("content"),
            "Woge-Interaction" to ((previous?.interactionSequence?.value ?: 0) + 1).toString(),
            "Woge-Region-Revisions" to
                select("[data-woge-region]").joinToString(",") {
                    val region = it.attr("data-woge-region")
                    val revision =
                        if (previous?.target?.region?.value == region) {
                            previous.revision.next.value
                                .toString()
                        } else {
                            it.attr("data-woge-revision")
                        }
                    "$region:$revision"
                },
        )

    private fun HttpResponse<ByteArray>.singlePatch(status: Int): ReplacePatch {
        assertEquals(status, statusCode())
        assertTrue(header("Content-Type").startsWith("application/vnd.woge.patch-stream"))
        val decoder = PatchStreamV1.decoder()
        val events = decoder.feed(body())
        decoder.finish()
        assertEquals(PatchStreamEvent.Complete(1), events.last())
        val frame = events.filterIsInstance<PatchStreamEvent.PatchFrame>().single()
        return assertInstanceOf(ReplacePatch::class.java, frame.patch)
    }
}

private fun withInvoiceHost(
    adapter: InvoiceHttpAdapter,
    verify: (InvoiceHost) -> Unit,
) {
    when (adapter) {
        InvoiceHttpAdapter.WEBFLUX -> {
            val server =
                HttpServer
                    .create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle(invoiceHttpHandler())
                    .bindNow()
            try {
                InvoiceHost(server.port()).use(verify)
            } finally {
                server.disposeNow(Duration.ofSeconds(5))
            }
        }

        InvoiceHttpAdapter.KTOR -> withKtorInvoiceServer { port -> InvoiceHost(port).use(verify) }
    }
}

private class InvoiceHost(
    port: Int,
) : AutoCloseable {
    private val origin = "http://127.0.0.1:$port"
    private val client =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    fun page(preferences: String): Document {
        val response = send(request(INVOICE_PATH, preferences).GET().build())
        assertEquals(200, response.statusCode())
        return response.document()
    }

    fun submit(
        preferences: String,
        fields: Map<String, String>,
        enhanced: Map<String, String> = emptyMap(),
        requestOrigin: String = origin,
    ): HttpResponse<ByteArray> {
        val body = fields.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, UTF_8)}" }
        val request =
            request(ValidateLocalizedInvoiceAction.path, preferences)
                .header("Origin", requestOrigin)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .apply { enhanced.forEach(::header) }
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        return send(request)
    }

    override fun close() = client.close()

    private fun request(
        path: String,
        preferences: String,
    ): HttpRequest.Builder =
        HttpRequest
            .newBuilder(URI.create(origin + path))
            .timeout(Duration.ofSeconds(10))
            .header("Accept-Language", preferences)

    private fun send(request: HttpRequest): HttpResponse<ByteArray> =
        client.send(request, HttpResponse.BodyHandlers.ofByteArray())
}

private fun invoiceHttpHandler(): ReactorHttpHandlerAdapter {
    val handlers = WogeWebFluxHandlers()
    val page = handlers.page(PageUseCase<Unit> { localizedInvoicePage(it.context) }, WebFluxPageInput { })
    val action = handlers.action(localizedInvoiceAction, localizedInvoiceDecoder.webFluxSubmission())
    val routes =
        coRouter {
            GET(INVOICE_PATH, page::handle)
            POST(ValidateLocalizedInvoiceAction.path, action::handle)
        }
    val localeResolver =
        AcceptHeaderLocaleContextResolver().apply {
            supportedLocales = InvoiceLanguage.entries.map { it.locale }
            defaultLocale = Locale.US
        }
    val httpHandler =
        WebHttpHandlerBuilder
            .webHandler(RouterFunctions.toWebHandler(routes))
            .localeContextResolver(localeResolver)
            .build()
    return ReactorHttpHandlerAdapter(httpHandler)
}

private enum class InvoiceCase(
    val language: String,
    val amount: String,
    val dueDay: String,
    val invalidAmount: String,
    val amountLabel: String,
    val summary: String,
) {
    GERMAN(
        "de-DE",
        "90071992547409,93",
        "11.10.2026",
        "1.234,56",
        "Betrag (EUR)",
        "Bitte Eingaben prüfen",
    ),
    ENGLISH(
        "en-US",
        "90071992547409.93",
        "10/11/2026",
        "1,234.56",
        "Amount (EUR)",
        "Check your entries",
    ),
    ARABIC(
        "ar-EG",
        "٩٠٠٧١٩٩٢٥٤٧٤٠٩٫٩٣",
        "١١/١٠/٢٠٢٦",
        "١٬٢٣٤٫٥٦",
        "المبلغ (EUR)",
        "راجع البيانات المدخلة",
    ),
    ;

    val preferences = "fr-FR,$language;q=0.8"
    val direction: String get() = if (this == ARABIC) "rtl" else "ltr"
    val accepted: String
        get() =
            when (this) {
                GERMAN ->
                    "Geprüft: $amount EUR, Leistungsdatum 2026-02-28, fällig am $dueDay. " +
                        "Zahlungszeitpunkt: $PAYMENT_INSTANT."
                ENGLISH ->
                    "Validated: $amount EUR, service date 2026-02-28, due on $dueDay. " +
                        "Payment instant: $PAYMENT_INSTANT."
                ARABIC ->
                    "تم التحقق: $amount EUR، تاريخ الخدمة 2026-02-28، الاستحقاق $dueDay. وقت الدفع: $PAYMENT_INSTANT."
            }

    fun form(
        amount: String = this.amount,
        serviceDay: String = "2026-02-28",
        dueDay: String = this.dueDay,
        paymentTime: String = "2026-10-11T12:30",
    ): Map<String, String> =
        mapOf(
            "amount" to amount,
            "serviceDay" to serviceDay,
            "dueDay" to dueDay,
            "paymentTime" to paymentTime,
            "formLanguage" to language,
        )
}

private fun HttpResponse<ByteArray>.document(): Document = Jsoup.parse(body().toString(UTF_8))

private fun HttpResponse<*>.header(name: String): String = headers().firstValue(name).orElseThrow()

private fun Element.required(selector: String): Element = requireNotNull(selectFirst(selector)) { "Missing $selector" }

internal const val INVOICE_PATH = "/localized-invoice"
private const val INVALID_DUE_DAY = "31/31/2026 ñ<&"
private const val INVALID_PAYMENT_TIME = "2026-03-29T02:30<&"
private const val PAYMENT_INSTANT = "2026-10-11T10:30:00Z"
private val FIELD_IDS =
    mapOf(
        "amount" to "invoice-amount",
        "serviceDay" to "invoice-service-day",
        "dueDay" to "invoice-due-day",
        "paymentTime" to "invoice-payment-time",
    )
