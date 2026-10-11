package dev.woge.host

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale
import kotlin.reflect.KProperty1

/** Binds a flat native form to a command's generated Kotlin serializer, without reflection. */
@OptIn(ExperimentalSerializationApi::class)
public class FormDecoder<Command : Any>(
    private val serializer: KSerializer<Command>,
    public val limits: FormLimits = FormLimits(),
    private val unknownFields: UnknownFormFields = UnknownFormFields.REJECT,
) {
    private val descriptor = serializer.descriptor
    private var localization: FormLocalization? = null

    /** Localized fields are opt-in and require a configured locale and date pattern. */
    public constructor(
        serializer: KSerializer<Command>,
        localization: FormLocalization,
        limits: FormLimits = FormLimits(),
        unknownFields: UnknownFormFields = UnknownFormFields.REJECT,
    ) : this(serializer, limits, unknownFields) {
        localization.validate(descriptor)
        this.localization = localization
    }

    public fun <Value> path(
        property: KProperty1<Command, Value>,
        serializedName: String = property.name,
    ): FormPath<Command, Value> = FormPath(serializedName, descriptor.formChild(serializedName))

    init {
        require(descriptor.kind == StructureKind.CLASS && !descriptor.isInline) { "Form command must be a class" }
        require(descriptor.formStructure()) { "Unsupported or recursively nested form command" }
    }

    public fun body(): FormBody = FormBody(limits)

    /** Explicit [serializedName] is required when the command property uses a different @SerialName. */
    public fun <Value> field(
        property: KProperty1<Command, Value>,
        id: FormElementId,
        serializedName: String = property.name,
    ): FormField<Command> {
        require(descriptor.getElementIndex(serializedName) != CompositeDecoder.UNKNOWN_NAME) {
            "Form field '$serializedName' is not present in the command serializer"
        }
        return FormField(serializedName, id)
    }

    /** Consumes a reader exactly once. Input failures never invoke the command serializer. */
    public fun decode(body: FormBody): FormResult<Command> = submission(body).result

    /** Retains bounded text for explicit native field-error rendering; transport errors retain none. */
    public fun submission(body: FormBody): FormSubmission<Command> {
        require(body.limits == limits) { "Form body must use the decoder's limits" }
        val fields = body.finish()
        val result = fields.problem?.let { FormResult.Rejected(it) } ?: decodeFields(fields.values)
        return FormSubmission(result, FormValues(if (fields.problem == null) fields.values else emptyMap()))
    }

    /** Binds already bounded multipart text with the same serializer and field-validation rules. */
    internal fun multipartSubmission(fields: Map<String, List<String>>): FormSubmission<Command> =
        FormSubmission(decodeFields(fields), FormValues(fields))

    private fun decodeFields(fields: Map<String, List<String>>): FormResult<Command> {
        val tree = FormTree(fields, unknownFields, localization)
        val values = tree.bind(descriptor)
        return if (tree.errors.isNotEmpty()) {
            FormResult.Rejected(FormProblem.Fields(tree.errors.toList()))
        } else {
            FormResult.Decoded(Json.decodeFromJsonElement(serializer, values))
        }
    }

    /** Convenience for an already bounded body; adapters use [body] to enforce limits while reading. */
    public fun decode(bytes: ByteArray): FormResult<Command> =
        body().let {
            it.accept(bytes)
            decode(it)
        }

    /** Native forms use UTF-8, with no charset parameter or an explicit UTF-8 parameter. */
    public fun requireContentType(value: String?) {
        if (value == null || !FORM_CONTENT_TYPE.matches(value.trim())) {
            throw FormDecodingException(FormProblem.UnsupportedContentType)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.formScalar(): Boolean =
    when {
        isInline -> getElementDescriptor(0).formScalar()
        kind is PrimitiveKind -> true
        kind == SerialKind.ENUM ->
            (0 until elementsCount)
                .map { getElementName(it).lowercase(Locale.ROOT).replace('_', '-') }
                .let { it.size == it.toSet().size }
        else -> false
    }

@OptIn(ExperimentalSerializationApi::class)
@Suppress("CyclomaticComplexMethod")
internal fun SerialDescriptor.formValue(raw: String): JsonElement? {
    if (isNullable && raw.isEmpty()) return JsonNull
    return if (isInline) {
        getElementDescriptor(0).formValue(raw)
    } else {
        when (kind) {
            PrimitiveKind.STRING -> JsonPrimitive(raw)
            PrimitiveKind.CHAR -> raw.takeIf { it.length == 1 }?.let(::JsonPrimitive)
            PrimitiveKind.BOOLEAN -> raw.toBooleanStrictOrNull()?.let(::JsonPrimitive)
            PrimitiveKind.BYTE -> raw.toByteOrNull()?.let(::JsonPrimitive)
            PrimitiveKind.SHORT -> raw.toShortOrNull()?.let(::JsonPrimitive)
            PrimitiveKind.INT -> raw.toIntOrNull()?.let(::JsonPrimitive)
            PrimitiveKind.LONG -> raw.toLongOrNull()?.let(::JsonPrimitive)
            PrimitiveKind.FLOAT ->
                raw
                    .takeIf(DECIMAL_NUMBER::matches)
                    ?.toFloatOrNull()
                    ?.takeIf {
                        it.isFinite()
                    }?.let(::JsonPrimitive)
            PrimitiveKind.DOUBLE ->
                raw
                    .takeIf(DECIMAL_NUMBER::matches)
                    ?.toDoubleOrNull()
                    ?.takeIf {
                        it.isFinite()
                    }?.let(::JsonPrimitive)
            SerialKind.ENUM ->
                (0 until elementsCount)
                    .map(::getElementName)
                    .firstOrNull { it.lowercase(Locale.ROOT).replace('_', '-') == raw }
                    ?.let(::JsonPrimitive)
            else -> null
        }
    }
}

private val DECIMAL_NUMBER = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
private val FORM_CONTENT_TYPE =
    Regex(
        "application/x-www-form-urlencoded(?:\\s*;\\s*charset\\s*=\\s*(?:UTF-8|\"UTF-8\"))?",
        RegexOption.IGNORE_CASE,
    )
