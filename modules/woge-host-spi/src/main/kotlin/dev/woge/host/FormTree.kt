package dev.woge.host

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.formStructure(depth: Int = 0): Boolean =
    depth <= MAX_FORM_DEPTH &&
        when {
            formScalar() -> true
            kind == StructureKind.LIST -> getElementDescriptor(0).formStructure(depth + 1)
            kind == StructureKind.CLASS && !isInline ->
                (0 until elementsCount).all {
                    val child = getElementDescriptor(it)
                    val flat =
                        child.formScalar() ||
                            child.kind == StructureKind.LIST &&
                            child.getElementDescriptor(0).formScalar()
                    (flat || getElementName(it).none { character -> character in ".[]" }) &&
                        child.formStructure(depth + 1)
                }
            else -> false
        }

/** Sparse numeric row keys are sorted, never used to allocate a sparse array. */
@OptIn(ExperimentalSerializationApi::class)
internal class FormTree(
    private val fields: Map<String, List<String>>,
    private val unknown: UnknownFormFields,
    private val localization: FormLocalization?,
) {
    val errors = mutableListOf<FormFieldError>()
    private val consumed = mutableSetOf<String>()

    fun bind(descriptor: SerialDescriptor): JsonObject {
        val result = objectValue(descriptor, "")
        if (unknown == UnknownFormFields.REJECT) {
            fields.keys.filterNot { it in consumed }.forEach { error(it, FormErrorCode.UNKNOWN) }
        }
        return result
    }

    private fun objectValue(
        descriptor: SerialDescriptor,
        prefix: String,
    ): JsonObject {
        val values = linkedMapOf<String, JsonElement>()
        repeat(descriptor.elementsCount) { index ->
            val name = descriptor.getElementName(index)
            val path = if (prefix.isEmpty()) name else "$prefix.$name"
            val child = descriptor.getElementDescriptor(index)
            if (present(path)) {
                value(child, path)?.let { values[name] = it }
            } else if (!descriptor.isElementOptional(index)) {
                when {
                    child.isNullable -> values[name] = JsonNull
                    child.kind == StructureKind.LIST -> values[name] = JsonArray(emptyList())
                    else -> error(path, FormErrorCode.MISSING)
                }
            }
        }
        return JsonObject(values)
    }

    private fun present(path: String): Boolean =
        fields.keys.any { it == path || it.startsWith("$path.") || it.startsWith("$path[") }

    private fun value(
        descriptor: SerialDescriptor,
        path: String,
    ): JsonElement? =
        when {
            descriptor.formScalar() -> scalar(descriptor, path)
            descriptor.isNullable && fields[path] == listOf("") -> {
                consumed.add(path)
                JsonNull
            }
            descriptor.kind == StructureKind.CLASS -> objectValue(descriptor, path)
            else -> listValue(descriptor.getElementDescriptor(0), path)
        }

    private fun listValue(
        item: SerialDescriptor,
        path: String,
    ): JsonArray {
        val raw = fields[path]
        val pattern = Regex("^${Regex.escape(path)}\\[([0-9]+)](?:[.\\[].*|$)")
        val keys =
            fields.keys
                .mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1) }
                .filter { it.toIntOrNull()?.toString() == it }
                .map(String::toInt)
                .distinct()
                .sorted()
        if (raw != null && item.formScalar()) {
            consumed.add(path)
            if (keys.isNotEmpty()) error(path, FormErrorCode.REPEATED)
            return JsonArray(raw.mapNotNull { parse(item, path, it) })
        }
        return JsonArray(keys.mapNotNull { value(item, "$path[$it]") })
    }

    private fun scalar(
        descriptor: SerialDescriptor,
        path: String,
    ): JsonElement? {
        val raw = fields[path]
        if (raw == null) {
            error(path, FormErrorCode.MISSING)
            return null
        }
        consumed.add(path)
        return if (raw.size != 1) {
            error(path, FormErrorCode.REPEATED)
            null
        } else {
            parse(descriptor, path, raw.single())
        }
    }

    private fun parse(
        descriptor: SerialDescriptor,
        path: String,
        raw: String,
    ): JsonElement? {
        val normalized = if (localization == null) raw else localization.normalize(descriptor, path, raw)
        val result = normalized?.let(descriptor::formValue)
        if (result == null) error(path, FormErrorCode.MALFORMED)
        return result
    }

    private fun error(
        path: String,
        code: FormErrorCode,
    ) {
        errors.add(FormFieldError(path, code))
    }
}

private const val MAX_FORM_DEPTH = 8
