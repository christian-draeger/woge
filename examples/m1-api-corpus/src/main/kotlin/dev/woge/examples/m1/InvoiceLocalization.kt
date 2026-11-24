package dev.woge.examples.m1

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.DecimalStyle
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.ResourceBundle

private const val LAST_DECIMAL_DIGIT = 9

internal enum class InvoiceLanguage(
    val locale: Locale,
    datePattern: String,
    val direction: String = "ltr",
) {
    GERMAN(Locale.GERMANY, "dd.MM.uuuu"),
    ENGLISH(Locale.US, "MM/dd/uuuu"),
    ARABIC(Locale.forLanguageTag("ar-EG"), "dd/MM/uuuu", "rtl"),
    ;

    val tag = locale.toLanguageTag()
    private val decimalStyle = DecimalStyle.of(locale)
    val date: DateTimeFormatter =
        DateTimeFormatter
            .ofPattern(datePattern, locale)
            .withDecimalStyle(decimalStyle)
            .withResolverStyle(ResolverStyle.STRICT)
    private val zero = decimalStyle.zeroDigit
    private val separator = decimalStyle.decimalSeparator
    private val digits = "[$zero-${zero + LAST_DECIMAL_DIGIT}]"
    private val amountPattern = Regex("$digits+(?:${Regex.escape(separator.toString())}$digits{1,2})?")
    val messages: ResourceBundle =
        ResourceBundle.getBundle(
            "dev.woge.examples.m1.InvoiceMessages",
            locale,
            ResourceBundle.Control.getNoFallbackControl(
                ResourceBundle.Control.FORMAT_PROPERTIES,
            ),
        )

    fun amount(raw: String): BigDecimal? {
        if (!amountPattern.matches(raw)) return null
        return buildString {
            raw.forEach { append(if (it == separator) '.' else '0' + (it - zero)) }
        }.toBigDecimal().setScale(2)
    }

    fun formatAmount(amount: BigDecimal): String =
        buildString {
            amount.toPlainString().forEach { append(if (it == '.') separator else zero + (it - '0')) }
        }
}

private val paymentZone = ZoneId.of("Europe/Berlin")
private val paymentTimePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}")
private val paymentTimeFormat =
    DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

internal sealed interface PaymentTime {
    data class Valid(
        val instant: Instant,
    ) : PaymentTime

    data class Invalid(
        val messageKey: String,
    ) : PaymentTime
}

internal fun parsePaymentTime(raw: String): PaymentTime {
    val local =
        try {
            if (paymentTimePattern.matches(raw)) LocalDateTime.parse(raw, paymentTimeFormat) else null
        } catch (_: DateTimeParseException) {
            null
        } ?: return PaymentTime.Invalid("paymentTimeError")

    val offsets = paymentZone.rules.getValidOffsets(local)
    return when (offsets.size) {
        0 -> PaymentTime.Invalid("paymentTimeGap")
        1 -> PaymentTime.Valid(local.toInstant(offsets.single()))
        else -> PaymentTime.Invalid("paymentTimeOverlap")
    }
}
