package dev.woge.host

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Locale

class AdvancedFormsTest {
    private val decoder = FormDecoder(Command.serializer())

    @Test
    fun `nested lists use stable sparse keys and typed serializer names`() {
        val field =
            decoder
                .path(Command::rows)
                .row(FormRowKey.of(7))
                .child(Row::title)
                .field(FormElementId.of("row-7-title"))
        assertEquals("rows[7].title", field.name)
        assertEquals("row-7-title-error", field.errorId.value)
        assertEquals(
            Command(listOf(Row("first", listOf(1, 2)), Row("last"))),
            decoder
                .decode("rows[7].title=last&rows[2].title=first&rows[2].numbers=1&rows[2].numbers=2".toByteArray())
                .getOrThrow(),
        )
        assertEquals(
            Command(listOf(Row("last"))),
            decoder.decode("rows[7].title=last".toByteArray()).getOrThrow(),
        )
        assertThrows<IllegalArgumentException> { FormRowKey.of(-1) }
        assertThrows<IllegalArgumentException> { decoder.path(Command::rows).child(List<Row>::size) }
    }

    @Test
    fun `malformed aliases unknown children duplicate scalars and missing nested fields reject`() {
        for (body in listOf(
            "rows[01].title=bad",
            "rows[-1].title=bad",
            "rows[99999999999].title=bad",
            "rows[7].title=a&rows[7].title=b",
            "rows[7].unknown=value",
            "rows[7].numbers=x",
            "rows[7].title=ok&rows[7].numbers=1&rows[7].numbers[3]=2",
        )) {
            assertTrue(decoder.decode(body.toByteArray()) is FormResult.Rejected, body)
        }
    }

    @Test
    fun `localized numbers and dates require explicit locale and strict configured formats`() {
        val localized =
            FormDecoder(
                Localized.serializer(),
                FormLocalization(Locale.GERMANY, "dd.MM.uuuu", setOf("amount", "date")),
            )
        assertEquals(
            Localized(1234.5, "2026-10-11"),
            localized.decode("amount=1.234,5&date=11.10.2026".toByteArray()).getOrThrow(),
        )
        for (body in listOf(
            "amount=1.2.3&date=11.10.2026",
            "amount=1,5junk&date=11.10.2026",
            "amount=1,5&date=31.02.2026",
            "amount=1,5&date=2026-10-11",
        )) {
            assertTrue(localized.decode(body.toByteArray()) is FormResult.Rejected, body)
        }
        assertTrue(FormDecoder(Localized.serializer()).decode("amount=1,5&date=x".toByteArray()) is FormResult.Rejected)
        assertThrows<IllegalArgumentException> { FormLocalization(Locale.ROOT, "dd.MM.uuuu", setOf("amount")) }
        assertThrows<IllegalArgumentException> { FormLocalization(Locale.GERMANY, "", setOf("amount")) }
    }

    @Test
    fun `existing flat serialized names retain literal dots and brackets`() {
        val flat = FormDecoder(FlatNames.serializer())
        assertEquals(
            FlatNames("unchanged", listOf("one", "two")),
            flat.decode("legacy.title=unchanged&legacy[tags]=one&legacy[tags]=two".toByteArray()).getOrThrow(),
        )
    }

    @Test
    fun `recursive schemas fail loudly instead of recursing or allocating without a bound`() {
        assertThrows<IllegalArgumentException> { FormDecoder(Recursive.serializer()) }
    }

    @Serializable
    private data class Command(
        val rows: List<Row>,
    )

    @Serializable
    private data class Row(
        val title: String,
        val numbers: List<Int> = emptyList(),
    )

    @Serializable
    private data class Localized(
        val amount: Double,
        val date: String,
    )

    @Serializable
    private data class FlatNames(
        @SerialName("legacy.title") val title: String,
        @SerialName("legacy[tags]") val tags: List<String>,
    )

    @Serializable
    private data class Recursive(
        val child: Recursive? = null,
    )
}
