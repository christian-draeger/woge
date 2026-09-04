package dev.woge.protocol.internal

import dev.woge.protocol.PatchStreamErrorCode
import dev.woge.protocol.PatchStreamException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal inline fun <T> parseMetadata(
    value: String,
    expectedKeys: Set<String>,
    read: (JsonObject) -> T,
): T {
    val element =
        try {
            Json.parseToJsonElement(value)
        } catch (_: SerializationException) {
            invalidMetadata()
        }
    val objectValue = element as? JsonObject ?: invalidMetadata()
    if (objectValue.keys != expectedKeys) invalidMetadata()
    return read(objectValue)
}

internal fun JsonObject.requiredString(name: String): String {
    val primitive = this[name] as? JsonPrimitive ?: invalidMetadata()
    if (!primitive.isString) invalidMetadata()
    return primitive.content
}

internal fun JsonObject.requiredInt(name: String): Int {
    val primitive = this[name] as? JsonPrimitive ?: invalidMetadata()
    if (primitive.isString) invalidMetadata()
    return primitive.intOrNull ?: invalidMetadata()
}

internal fun JsonObject.requiredLong(name: String): Long {
    val primitive = this[name] as? JsonPrimitive ?: invalidMetadata()
    if (primitive.isString) invalidMetadata()
    return primitive.longOrNull ?: invalidMetadata()
}

internal fun invalidMetadata(): Nothing =
    protocolFailure(PatchStreamErrorCode.INVALID_METADATA, "Patch frame metadata is invalid or non-canonical")

internal fun protocolFailure(
    code: PatchStreamErrorCode,
    message: String,
): Nothing = throw PatchStreamException(code, message)
