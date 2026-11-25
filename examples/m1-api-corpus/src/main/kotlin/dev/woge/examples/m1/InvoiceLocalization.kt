package dev.woge.examples.m1

import java.math.BigDecimal
import java.text.MessageFormat
import java.time.Instant
import java.time.LocalDate
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

    fun accepted(values: InvoiceValues): String =
        MessageFormat(messages.getString("accepted"), locale).format(
            arrayOf(formatAmount(values.amount), values.serviceDay, date.format(values.dueDay), values.payment),
        )

    fun localizedNumber(value: Int): String = value.toString().map { zero + (it - '0') }.joinToString("")

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

internal val paymentZone = ZoneId.of("Europe/Berlin")
private val paymentTimePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}")
internal val paymentTimeFormat =
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

internal data class InvoiceValues(
    val amount: BigDecimal,
    val serviceDay: LocalDate,
    val dueDay: LocalDate,
    val payment: Instant,
)

internal sealed interface InvoiceValidation {
    data class Valid(
        val values: InvoiceValues,
    ) : InvoiceValidation

    data class Invalid(
        val issues: Map<String, String>,
    ) : InvoiceValidation
}

internal fun parseInvoiceFields(
    form: LocalizedInvoiceFields,
    language: InvoiceLanguage,
): InvoiceValidation {
    val amount = language.amount(form.amount)
    val serviceDay = parseInvoiceDay(form.serviceDay, DateTimeFormatter.ISO_LOCAL_DATE)
    val dueDay = parseInvoiceDay(form.dueDay, language.date)
    val payment = parsePaymentTime(form.paymentTime)
    val validAmountsAndDays = amount != null && serviceDay != null && dueDay != null
    if (validAmountsAndDays && payment is PaymentTime.Valid) {
        return InvoiceValidation.Valid(InvoiceValues(amount, serviceDay, dueDay, payment.instant))
    }
    return InvoiceValidation.Invalid(
        buildMap {
            if (amount == null) put(LocalizedInvoiceFields::amount.name, "amountError")
            if (serviceDay == null) put(LocalizedInvoiceFields::serviceDay.name, "serviceDayError")
            if (dueDay == null) put(LocalizedInvoiceFields::dueDay.name, "dueDayError")
            if (payment is PaymentTime.Invalid) put(LocalizedInvoiceFields::paymentTime.name, payment.messageKey)
        },
    )
}

private fun parseInvoiceDay(
    raw: String,
    format: DateTimeFormatter,
): LocalDate? =
    try {
        LocalDate.parse(raw, format)
    } catch (_: DateTimeParseException) {
        null
    }
