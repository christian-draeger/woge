package dev.woge.host

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.Locale

class FormDecoderTest {
    private val decoder = FormDecoder(Command.serializer())

    @Test
    fun `binds native UTF-8 values without locale sensitive parsing`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val command =
                decoder
                    .decode(
                        (
                            "title=A%2BB+%E2%82%AC&count=-42&ratio=1.5e2&enabled=true&state=in-progress" +
                                "&tags=a&tags=b&note=&id=7"
                        ).toByteArray(),
                    ).getOrThrow()
            assertEquals(
                Command("A+B \u20ac", -42, 150.0, true, State.IN_PROGRESS, listOf("a", "b"), null, Id(7)),
                command,
            )
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `missing defaults lists and nullable fields follow distinct rules`() {
        assertEquals(Command("hello"), decoder.decode("title=hello".toByteArray()).getOrThrow())
        assertEquals("", decoder.decode("title=".toByteArray()).getOrThrow().title)
        assertEquals(
            listOf(FormFieldError("title", FormErrorCode.MISSING)),
            fieldErrors(decoder.decode(byteArrayOf())),
        )
    }

    @Test
    fun `all field errors are structured and duplicates never select a winner`() {
        val result =
            decoder.decode(
                "title=a&title=b&count=bad&enabled=on&state=IN_PROGRESS&tags=x&unknown=secret".toByteArray(),
            )
        assertEquals(
            setOf(
                FormFieldError("title", FormErrorCode.REPEATED),
                FormFieldError("count", FormErrorCode.MALFORMED),
                FormFieldError("enabled", FormErrorCode.MALFORMED),
                FormFieldError("state", FormErrorCode.MALFORMED),
                FormFieldError("unknown", FormErrorCode.UNKNOWN),
            ),
            fieldErrors(result).toSet(),
        )
        assertFalse(result.toString().contains("secret"))
        assertFalse(decoder.decode("title=secret".toByteArray()).toString().contains("secret"))
    }

    @Test
    fun `ignoring unknown fields is explicit and still bounded`() {
        val ignored = FormDecoder(Command.serializer(), unknownFields = UnknownFormFields.IGNORE)
        assertEquals("ok", ignored.decode("title=ok&_csrf=token".toByteArray()).getOrThrow().title)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "title=%", "title=%0", "title=%GG", "title=%C0%AF", "title=%ED%A0%80",
            "=a", "&", "title=a&&x=b", "title=a&", "%00=x",
        ],
    )
    fun `hostile and ambiguous encodings are rejected`(encoded: String) {
        assertEquals(FormResult.Rejected(FormProblem.MalformedEncoding), decoder.decode(encoded.toByteArray()))
    }

    @ParameterizedTest
    @ValueSource(strings = ["NaN", "Infinity", "1,5", "1e999", "0x1p0", "1f", "+1+"])
    fun `non decimal non finite and whitespace numbers fail`(value: String) {
        assertEquals(
            listOf(FormFieldError("ratio", FormErrorCode.MALFORMED)),
            fieldErrors(decoder.decode("title=a&ratio=$value".toByteArray())),
        )
    }

    @Test
    fun `numbers reject overflow and lists report malformed elements`() {
        assertEquals(
            setOf(FormFieldError("count", FormErrorCode.MALFORMED), FormFieldError("ids", FormErrorCode.MALFORMED)),
            fieldErrors(decoder.decode("title=a&count=2147483648&ids=1&ids=no".toByteArray())).toSet(),
        )
    }

    @Test
    fun `every byte split including UTF-8 and percent escapes has identical results`() {
        val bytes = "title=%E2%82%AC+hello%2B&tags=x&tags=y".toByteArray()
        val expected = decoder.decode(bytes)
        for (split in 0..bytes.size) {
            val body = decoder.body()
            assertTrue(body.accept(bytes, length = split))
            assertTrue(body.accept(bytes, offset = split))
            assertEquals(expected, decoder.decode(body), "split $split")
        }
        val bytewise = decoder.body()
        bytes.forEach { assertTrue(bytewise.accept(byteArrayOf(it))) }
        assertEquals(expected, decoder.decode(bytewise))
    }

    @Test
    fun `limits accept exact thresholds and reject the next byte or field`() {
        assertLimit(FormLimits(bodyBytes = 7), "title=a", "title=ab", FormLimit.BODY_BYTES, 7)
        assertLimit(FormLimits(fieldCount = 1), "title=a", "title=a&tags=b", FormLimit.FIELD_COUNT, 1)
        assertLimit(FormLimits(nameBytes = 5), "title=a", "titles=a", FormLimit.NAME_BYTES, 5)
        assertLimit(FormLimits(valueBytes = 3), "title=%E2%82%AC", "title=%E2%82%ACa", FormLimit.VALUE_BYTES, 3)
    }

    @Test
    fun `incremental rejection is terminal and a body cannot bypass decoder limits`() {
        val small = FormDecoder(Command.serializer(), FormLimits(bodyBytes = 7))
        val body = small.body()
        assertTrue(body.accept("title=a".toByteArray()))
        assertFalse(body.accept("b".toByteArray()))
        assertFalse(body.accept("secret".toByteArray()))
        assertEquals(FormResult.Rejected(FormProblem.LimitExceeded(FormLimit.BODY_BYTES, 7)), small.decode(body))
        assertThrows(IllegalStateException::class.java) { body.accept(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { small.decode(decoder.body()) }
    }

    @Test
    fun `UTF-8 content type is explicit and multipart JSON and other charsets are refused`() {
        listOf("application/x-www-form-urlencoded", "Application/X-WWW-Form-Urlencoded; charset=\"UTF-8\"")
            .forEach(decoder::requireContentType)
        listOf(
            null,
            "application/json",
            "multipart/form-data",
            "application/x-www-form-urlencoded; charset=ISO-8859-1",
            "application/x-www-form-urlencoded; charset=\"UTF-8",
            "application/x-www-form-urlencoded; charset=UTF-8; charset=UTF-8",
        ).forEach {
            val error = assertThrows(FormDecodingException::class.java) { decoder.requireContentType(it) }
            assertEquals(FailureCategory.UNSUPPORTED_MEDIA_TYPE, error.category)
        }
        assertEquals(
            FailureCategory.PAYLOAD_TOO_LARGE,
            FormDecodingException(FormProblem.LimitExceeded(FormLimit.BODY_BYTES, 1)).category,
        )
    }

    @Test
    fun `unsupported nested command shapes fail at configuration time`() {
        assertThrows(IllegalArgumentException::class.java) { FormDecoder(Nested.serializer()) }
        assertThrows(IllegalArgumentException::class.java) { FormDecoder(Ambiguous.serializer()) }
    }

    @Test
    fun `required nullable lists serialized names and numeric primitives bind consistently`() {
        val required = FormDecoder(Required.serializer())
        assertEquals(
            Required(null, emptyList(), State.DONE),
            required.decode("state=finished".toByteArray()).getOrThrow(),
        )
        assertEquals(
            Required(null, listOf(1, 2), State.DONE),
            required.decode("note=&numbers=1&numbers=2&state=finished".toByteArray()).getOrThrow(),
        )
        val primitives = FormDecoder(Primitives.serializer())
        assertEquals(
            Primitives(127, -32768, Long.MAX_VALUE, 1.5f, 'x', "ok"),
            primitives
                .decode(
                    "tiny=127&small=-32768&large=9223372036854775807&number=1.5&letter=x&task-title=ok".toByteArray(),
                ).getOrThrow(),
        )
        assertTrue(
            primitives.decode(
                "tiny=128&small=-32769&large=9223372036854775808&number=NaN&letter=xx".toByteArray(),
            ) is FormResult.Rejected,
        )
    }

    private fun fieldErrors(result: FormResult<Command>): List<FormFieldError> =
        ((result as FormResult.Rejected).problem as FormProblem.Fields).errors

    private fun assertLimit(
        limits: FormLimits,
        exact: String,
        exceeded: String,
        limit: FormLimit,
        threshold: Int,
    ) {
        val bounded = FormDecoder(Command.serializer(), limits)
        assertTrue(bounded.decode(exact.toByteArray()) is FormResult.Decoded)
        assertEquals(
            FormResult.Rejected(FormProblem.LimitExceeded(limit, threshold)),
            bounded.decode(exceeded.toByteArray()),
        )
    }

    @Serializable
    private data class Command(
        val title: String,
        val count: Int = 1,
        val ratio: Double = 0.0,
        val enabled: Boolean = false,
        val state: State = State.IN_PROGRESS,
        val tags: List<String> = emptyList(),
        val note: String? = null,
        val id: Id = Id(1),
        val ids: List<Int> = emptyList(),
    )

    @Serializable
    private enum class State {
        IN_PROGRESS,

        @SerialName("finished")
        DONE,
    }

    @Serializable
    @JvmInline
    private value class Id(
        val value: Long,
    )

    @Serializable
    private data class Nested(
        val command: Command,
    )

    @Serializable
    private data class Required(
        val note: String?,
        val numbers: List<Int>,
        val state: State,
    )

    @Serializable
    private data class Primitives(
        val tiny: Byte,
        val small: Short,
        val large: Long,
        val number: Float,
        val letter: Char,
        @SerialName("task-title") val title: String,
    )

    @Serializable
    private data class Ambiguous(
        val value: AmbiguousState,
    )

    @Serializable
    private enum class AmbiguousState {
        FIRST,

        @SerialName("first")
        SECOND,
    }
}
