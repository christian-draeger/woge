package dev.woge.examples.invoice

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

internal class SavedInvoiceHttpTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @EnumSource(InvoiceHttpAdapter::class)
    fun `localized native create and edit preserve invalid nested data and committed files`(
        adapter: InvoiceHttpAdapter,
    ) {
        val app = InvoiceDesk(directory)
        val ids = mutableListOf<Long>()
        withInvoiceServer(adapter, app) { port ->
            SavedInvoiceHttp(port, app.application.save.path).use { host ->
                InvoiceLanguage.entries.forEach { language -> ids.add(createAndEdit(host, app, language)) }
            }
        }

        // Warm request state and temporary files are gone. An independent server opens this same database.
        val reopened = InvoiceDesk(directory)
        val other = InvoiceHttpAdapter.entries.single { it != adapter }
        withInvoiceServer(other, reopened) { port ->
            SavedInvoiceHttp(port, reopened.application.save.path).use { host ->
                val language = InvoiceLanguage.ARABIC
                val pageOne = host.page(language)
                assertEquals("الصفحة ١", pageOne.selectFirst("#saved-page")!!.text())
                assertEquals(2, pageOne.select("ul a").size)
                assertEquals("الصفحة التالية", pageOne.selectFirst("nav a")!!.text())
                val pageTwo = host.page(language, "/localized-invoices?page=2")
                assertEquals("الصفحة ٢", pageTwo.selectFirst("#saved-page")!!.text())
                assertEquals(1, pageTwo.select("ul a").size)
                val invoice = host.page(language, "/localized-invoices?invoice=${ids.first()}")
                assertEquals("٥٠٠٫٣١", invoice.selectFirst("#saved-amount")!!.attr("value"))
                assertEquals("١٨/١١/٢٠٢٦", invoice.selectFirst("#saved-due-day")!!.attr("value"))
                assertEquals("2026-10-25T03:30", invoice.selectFirst("#saved-payment-time")!!.attr("value"))
                assertArrayEquals(REPLACEMENT_RECEIPT, committedReceipts(reopened).getValue(ids.first()))
            }
        }
    }

    private fun createAndEdit(
        host: SavedInvoiceHttp,
        app: InvoiceDesk,
        language: InvoiceLanguage,
    ): Long {
        val fields = validFields(language)
        assertRejectedAllocations(host, app, language, fields)

        val created = host.submit(language, fields, listOf(receipt()))
        assertEquals(303, created.statusCode())
        val id = created.locationId()
        val saved = checkNotNull(app.store.find(id))
        assertEquals(1L, saved.revision)
        assertEquals(BigDecimal("90071992547409.93"), saved.draft.values.amount)
        assertEquals(LocalDate.of(2026, 10, 11), saved.draft.values.serviceDay)
        assertEquals(LocalDate.of(2026, 11, 18), saved.draft.values.dueDay)
        assertEquals(Instant.parse("2026-10-25T02:30:00Z"), saved.draft.values.payment)
        assertEquals(InvoiceAllocations(BigDecimal("1.20"), BigDecimal("2.30")), saved.draft.services)
        assertArrayEquals(RECEIPT, committedReceipts(app).getValue(id))
        assertUploadsClean(app)
        assertEdits(host, app, language, fields, saved)
        return id
    }

    private fun assertRejectedAllocations(
        host: SavedInvoiceHttp,
        app: InvoiceDesk,
        language: InvoiceLanguage,
        fields: List<Pair<String, String>>,
    ) {
        val savedBefore = committedReceipts(app).size
        val invalid = "${language.formatAmount(BigDecimal("1.20"))} + & <ñ>"
        val broken =
            fields.toMutableList().apply {
                removeAll { (name) -> name == materialsFields.amounts.name }
                add(materialsFields.amounts.name to " ")
                add(materialsFields.amounts.name to invalid)
            }
        val rejected = host.submit(language, broken, listOf(receipt()))
        assertEquals(400, rejected.statusCode())
        val page = rejected.document()
        assertEquals(language.tag, page.selectFirst("html")!!.attr("lang"))
        assertEquals(language.direction, page.selectFirst("html")!!.attr("dir"))
        assertEquals(
            listOf(" ", invalid),
            page.select("[name='${materialsFields.amounts.name}']").map { it.attr("value") },
        )
        for (control in page.select("[name='${materialsFields.amounts.name}']")) {
            assertEquals("true", control.attr("aria-invalid"))
            assertEquals(materialsFields.amounts.errorId.value, control.attr("aria-errormessage"))
        }
        assertEquals(
            "#${materialsFields.amounts.id.value}",
            page.selectFirst("#saved-invoice-errors a")!!.attr("href"),
        )
        assertTrue(
            page
                .selectFirst(
                    "#saved-invoice-errors",
                )!!
                .text()
                .contains(language.messages.getString("summary")),
        )
        assertTrue(page.selectFirst("#${receiptField.id.value}")!!.attr("value").isEmpty())
        assertEquals(savedBefore, committedReceipts(app).size)
        assertUploadsClean(app)
    }

    private fun assertEdits(
        host: SavedInvoiceHttp,
        app: InvoiceDesk,
        language: InvoiceLanguage,
        fields: List<Pair<String, String>>,
        saved: SavedInvoice,
    ) {
        val id = saved.id
        val translated = host.page(language, "/localized-invoices?invoice=$id")
        assertEquals(
            language.formatAmount(saved.draft.values.amount),
            translated.selectFirst("#saved-amount")!!.attr("value"),
        )
        assertTrue(
            translated
                .selectFirst(
                    "#stored-receipt",
                )!!
                .text()
                .contains(language.localizedNumber(RECEIPT.size)),
        )
        val editing = fields + listOf(savedId to "$id", savedRevision to "1")
        val badFile = host.submit(language, editing, listOf(receipt(byteArrayOf(0x80.toByte()))))
        assertEquals(400, badFile.statusCode())
        assertEquals(
            language.messages.getString("savedReceiptError"),
            badFile.document().selectFirst("#${receiptField.errorId.value}")!!.text(),
        )
        assertEquals(1L, app.store.find(id)!!.revision)
        assertUploadsClean(app)

        val changed =
            editing.map { (key, value) ->
                key to if (key == savedAmount.name) language.formatAmount(BigDecimal("500.31")) else value
            }
        assertEquals(303, host.submit(language, changed).statusCode())
        assertEquals(2L, app.store.find(id)!!.revision)
        assertEquals(
            BigDecimal("500.31"),
            app.store
                .find(id)!!
                .draft.values.amount,
        )
        assertArrayEquals(RECEIPT, committedReceipts(app).getValue(id))

        val currentFields = changed.map { (key, value) -> key to if (key == savedRevision) "2" else value }
        assertEquals(303, host.submit(language, currentFields, listOf(receipt(REPLACEMENT_RECEIPT))).statusCode())
        assertEquals(3L, app.store.find(id)!!.revision)
        assertEquals(409, host.submit(language, currentFields, listOf(receipt())).statusCode())
        assertArrayEquals(REPLACEMENT_RECEIPT, committedReceipts(app).getValue(id))
        assertUploadsClean(app)
    }

    @ParameterizedTest
    @EnumSource(InvoiceHttpAdapter::class)
    fun `tampered direct requests never write an invoice`(adapter: InvoiceHttpAdapter) {
        val app = InvoiceDesk(directory)
        val language = InvoiceLanguage.ENGLISH
        val fields = validFields(language)
        withInvoiceServer(adapter, app) { port ->
            SavedInvoiceHttp(port, app.application.save.path).use { host ->
                val wrongPath = fields.map { (name, value) -> name.replace("rows[7]", "rows[9]") to value }
                assertEquals(400, host.submit(language, wrongPath, listOf(receipt())).statusCode())
                val forgedKey =
                    fields.map { (name, value) ->
                        name to
                            if (name == servicesFields.keyName) "42" else value
                    }
                assertEquals(400, host.submit(language, forgedKey, listOf(receipt())).statusCode())
                assertEquals(400, host.submit(language, fields).statusCode())
                assertEquals(
                    403,
                    host.submit(language, fields, listOf(receipt(field = "rows[42].receipt"))).statusCode(),
                )
                assertEquals(
                    413,
                    host.submit(language, fields, listOf(receipt(ByteArray(MAX_RECEIPT_BYTES + 1)))).statusCode(),
                )
                assertEquals(
                    403,
                    host.submit(language, fields, listOf(receipt()), "https://example.invalid").statusCode(),
                )
                val changedLanguage = host.submit(InvoiceLanguage.GERMAN, fields, listOf(receipt()))
                assertEquals(400, changedLanguage.statusCode())
                assertTrue(
                    changedLanguage
                        .document()
                        .selectFirst("#saved-invoice-errors")!!
                        .text()
                        .contains(InvoiceLanguage.GERMAN.messages.getString("languageChanged")),
                )
                assertTrue(committedReceipts(app).isEmpty())
                assertUploadsClean(app)
            }
        }
    }

    @ParameterizedTest
    @EnumSource(InvoiceHttpAdapter::class)
    fun `browser native and opt-in upload journeys`(adapter: InvoiceHttpAdapter) {
        assumeTrue(System.getenv("WOGE_INVOICE_BROWSER") == "true")
        val script =
            Path
                .of(System.getProperty("woge.repository.root"))
                .resolve("client/woge-fallback-client/scripts/test-saved-invoices.mjs")
        val app = InvoiceDesk(directory)
        withInvoiceServer(adapter, app) { port ->
            val process = ProcessBuilder("node", script.toString(), "http://127.0.0.1:$port").inheritIO().start()
            try {
                assertTrue(process.waitFor(120, TimeUnit.SECONDS), "Chromium invoice journeys exceeded their deadline")
                assertEquals(0, process.exitValue())
                assertEquals(6, committedReceipts(app).size)
                assertUploadsClean(app)
            } finally {
                if (process.isAlive) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            }
        }
    }
}

private data class InvoiceUpload(
    val field: String,
    val content: ByteArray,
)

private val RECEIPT = "Beleg + إيصال ñ\n".toByteArray()
private val REPLACEMENT_RECEIPT = "Beleg ersetzt + إيصال جديد ñ\n".toByteArray()

private fun receipt(
    content: ByteArray = RECEIPT,
    field: String = receiptField.name,
) = InvoiceUpload(field, content)

private fun validFields(language: InvoiceLanguage): List<Pair<String, String>> =
    listOf(
        savedLanguage to language.tag,
        savedAmount.name to language.formatAmount(BigDecimal("90071992547409.93")),
        savedService.name to "2026-10-11",
        savedDue.name to language.date.format(LocalDate.of(2026, 11, 18)),
        savedPayment.name to "2026-10-25T03:30",
        servicesFields.keyName to servicesFields.key.toString(),
        servicesFields.amounts.name to language.formatAmount(BigDecimal("1.20")),
        servicesFields.amounts.name to language.formatAmount(BigDecimal("2.30")),
        materialsFields.keyName to materialsFields.key.toString(),
        materialsFields.amounts.name to language.formatAmount(BigDecimal("3.40")),
        materialsFields.amounts.name to language.formatAmount(BigDecimal("4.50")),
    )

private class SavedInvoiceHttp(
    port: Int,
    private val action: String,
) : AutoCloseable {
    private val origin = "http://127.0.0.1:$port"
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun page(
        language: InvoiceLanguage,
        path: String = "/localized-invoices",
    ): Document {
        val response = client.send(request(language, path).GET().build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        return response.document()
    }

    fun submit(
        language: InvoiceLanguage,
        fields: List<Pair<String, String>>,
        files: List<InvoiceUpload> = emptyList(),
        source: String = origin,
    ): HttpResponse<String> {
        val boundary = "woge-corpus-invoice-fixture"
        val bytes = ByteArrayOutputStream()
        fields.forEach { (name, value) ->
            bytes.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
        }
        files.forEach { file ->
            bytes.write(
                "--$boundary\r\nContent-Disposition: form-data; name=\"${file.field}\"; filename=\"example.txt\"\r\n"
                    .toByteArray(),
            )
            bytes.write("Content-Type: text/plain\r\n\r\n".toByteArray())
            bytes.write(file.content)
            bytes.write("\r\n".toByteArray())
        }
        bytes.write("--$boundary--\r\n".toByteArray())
        val post =
            request(language, action)
                .header("Origin", source)
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()))
                .build()
        return client.send(post, HttpResponse.BodyHandlers.ofString())
    }

    private fun request(
        language: InvoiceLanguage,
        path: String,
    ) = HttpRequest
        .newBuilder(URI.create(origin + path))
        .timeout(Duration.ofSeconds(10))
        .header("Accept-Language", language.tag)

    override fun close() = client.close()
}

private fun HttpResponse<String>.locationId(): Long =
    headers()
        .firstValue("Location")
        .orElseThrow()
        .removePrefix("/localized-invoices?invoice=")
        .toLong()

private fun HttpResponse<String>.document(): Document = Jsoup.parse(body())

private fun committedReceipts(app: InvoiceDesk): Map<Long, ByteArray> =
    DriverManager.getConnection(app.url).use { connection ->
        connection.createStatement().use { query ->
            query.executeQuery("SELECT id, receipt FROM invoice_record").use { result ->
                buildMap { while (result.next()) put(result.getLong(1), result.getBytes(2)) }
            }
        }
    }

private fun assertUploadsClean(app: InvoiceDesk) {
    assertTrue(Files.list(app.uploads).use { it.findAny().isEmpty })
}
