package dev.woge.host

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Explicit localized input policy. Fields use serializer paths; replace numeric row keys with `[]`. */
public class FormLocalization(
    private val locale: Locale,
    datePattern: String,
    fields: Set<String>,
) {
    private val fields = fields.toSet()
    private val date = DateTimeFormatter.ofPattern(datePattern, locale).withResolverStyle(ResolverStyle.STRICT)

    init {
        require(locale.language.isNotBlank() && datePattern.isNotBlank()) { "Configure a locale and date pattern" }
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun validate(root: SerialDescriptor) {
        fields.forEach { path ->
            var descriptor = root
            path.split('.').forEach { segment ->
                val name = segment.substringBefore('[')
                descriptor = descriptor.formChild(name)
                val suffix = segment.removePrefix(name)
                require(suffix.matches(Regex("(?:\\[\\])*"))) { "Use [] for localized row fields" }
                repeat(suffix.length / 2) {
                    require(descriptor.kind == StructureKind.LIST) { "Localized [] needs a list field" }
                    descriptor = descriptor.getElementDescriptor(0)
                }
            }
            while (descriptor.isInline) descriptor = descriptor.getElementDescriptor(0)
            require(descriptor.kind in LOCALIZED_KINDS) { "Localized fields must be numbers or date strings" }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun normalize(
        descriptor: SerialDescriptor,
        path: String,
        raw: String,
    ): String? {
        if (path.replace(ROW_INDEX, "[]") !in fields || raw.isEmpty() && descriptor.isNullable) return raw
        return when {
            descriptor.isInline -> normalize(descriptor.getElementDescriptor(0), path, raw)
            descriptor.kind == PrimitiveKind.STRING -> {
                try {
                    LocalDate.parse(raw, date).toString()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
            else -> number(raw)
        }
    }

    private fun number(raw: String): String? {
        val format = NumberFormat.getNumberInstance(locale) as DecimalFormat
        format.isParseBigDecimal = true
        val grouping = Regex.escape(format.decimalFormatSymbols.groupingSeparator.toString())
        val decimal = Regex.escape(format.decimalFormatSymbols.decimalSeparator.toString())
        val size = format.groupingSize
        val pattern = Regex("-?(?:[0-9]+|[0-9]{1,$size}(?:$grouping[0-9]{$size})+)(?:$decimal[0-9]+)?")
        if (!pattern.matches(raw)) return null
        val position = ParsePosition(0)
        val number = format.parse(raw, position)
        return if (number != null && position.index == raw.length) number.toString() else null
    }
}

private val ROW_INDEX = Regex("\\[[0-9]+]")

private val LOCALIZED_KINDS =
    setOf(
        PrimitiveKind.STRING,
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        PrimitiveKind.LONG,
        PrimitiveKind.FLOAT,
        PrimitiveKind.DOUBLE,
    )
