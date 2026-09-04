package dev.woge.protocol.internal

import dev.woge.protocol.RecoveryIntent
import dev.woge.protocol.RemoteCorrelationId
import dev.woge.protocol.RemoteFailureCode
import dev.woge.protocol.RemotePatchFailure
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun encodeCompletionMetadata(patchCount: Int): String {
    require(patchCount >= 0) { "Patch count must not be negative" }
    return buildJsonObject { put(PATCHES, patchCount) }.toString()
}

internal fun decodeCompletionMetadata(value: String): Int =
    parseMetadata(value, COMPLETION_METADATA_KEYS) { objectValue ->
        val patchCount = objectValue.requiredInt(PATCHES)
        if (patchCount < 0 || encodeCompletionMetadata(patchCount) != value) invalidMetadata()
        patchCount
    }

internal fun encodeRemoteFailureMetadata(failure: RemotePatchFailure): String =
    buildJsonObject {
        put(CODE, failure.code.value)
        put(CORRELATION_ID, failure.correlationId.value)
        put(RECOVERY, failure.recovery.metadataValue())
    }.toString()

internal fun decodeRemoteFailureMetadata(value: String): RemotePatchFailure =
    parseMetadata(value, ERROR_METADATA_KEYS) { objectValue ->
        val failure =
            try {
                RemotePatchFailure(
                    code = RemoteFailureCode.of(objectValue.requiredString(CODE)),
                    correlationId = RemoteCorrelationId.of(objectValue.requiredString(CORRELATION_ID)),
                    recovery = recoveryIntent(objectValue.requiredString(RECOVERY)),
                )
            } catch (_: IllegalArgumentException) {
                invalidMetadata()
            }
        if (encodeRemoteFailureMetadata(failure) != value) invalidMetadata()
        failure
    }

private fun RecoveryIntent.metadataValue(): String =
    when (this) {
        RecoveryIntent.NONE -> "none"
        RecoveryIntent.RELOAD -> "reload"
    }

private fun recoveryIntent(value: String): RecoveryIntent =
    when (value) {
        "none" -> RecoveryIntent.NONE
        "reload" -> RecoveryIntent.RELOAD
        else -> invalidMetadata()
    }

private const val PATCHES: String = "patches"
private const val CODE: String = "code"
private const val CORRELATION_ID: String = "correlationId"
private const val RECOVERY: String = "recovery"
private val COMPLETION_METADATA_KEYS: Set<String> = setOf(PATCHES)
private val ERROR_METADATA_KEYS: Set<String> = setOf(CODE, CORRELATION_ID, RECOVERY)
