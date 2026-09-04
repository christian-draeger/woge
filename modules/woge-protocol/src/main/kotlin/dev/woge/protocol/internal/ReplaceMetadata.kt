package dev.woge.protocol.internal

import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchOperation
import dev.woge.protocol.PatchProtocolVersion
import dev.woge.protocol.PatchStreamErrorCode
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.TargetRevision
import dev.woge.protocol.TargetRevisionStep
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class ReplaceMetadata(
    val protocolVersion: PatchProtocolVersion,
    val patchId: PatchId,
    val pageEpoch: PageEpoch,
    val target: RegionTargetId,
    val interactionSequence: InteractionSequence,
    val revision: TargetRevisionStep,
)

internal fun encodeReplaceMetadata(patch: ReplacePatch): String =
    encodeReplaceMetadata(
        ReplaceMetadata(
            protocolVersion = patch.protocolVersion,
            patchId = patch.patchId,
            pageEpoch = patch.target.pageEpoch,
            target = patch.target.region,
            interactionSequence = patch.interactionSequence,
            revision = patch.revision,
        ),
    )

internal fun decodeReplaceMetadata(value: String): ReplaceMetadata =
    parseMetadata(value, PATCH_METADATA_KEYS) { objectValue ->
        if (objectValue.requiredString(OPERATION) != REPLACE_OPERATION) invalidMetadata()

        val metadata =
            try {
                ReplaceMetadata(
                    protocolVersion = PatchProtocolVersion.of(objectValue.requiredInt(PROTOCOL_VERSION)),
                    patchId = PatchId.of(objectValue.requiredString(PATCH_ID)),
                    pageEpoch = PageEpoch.of(objectValue.requiredString(EPOCH)),
                    target = RegionTargetId.of(objectValue.requiredString(TARGET)),
                    interactionSequence =
                        InteractionSequence.of(objectValue.requiredLong(INTERACTION_SEQUENCE)),
                    revision =
                        TargetRevisionStep(
                            base = TargetRevision.of(objectValue.requiredLong(BASE_REVISION)),
                            next = TargetRevision.of(objectValue.requiredLong(NEXT_REVISION)),
                        ),
                )
            } catch (_: IllegalArgumentException) {
                invalidMetadata()
            }

        if (metadata.protocolVersion != PatchProtocolVersion.CURRENT) {
            protocolFailure(
                PatchStreamErrorCode.UNSUPPORTED_VERSION,
                "Patch metadata uses an unsupported protocol version",
            )
        }
        if (encodeReplaceMetadata(metadata) != value) invalidMetadata()
        metadata
    }

private fun encodeReplaceMetadata(metadata: ReplaceMetadata): String =
    buildJsonObject {
        put(PROTOCOL_VERSION, metadata.protocolVersion.value)
        put(OPERATION, PatchOperation.REPLACE.metadataValue())
        put(PATCH_ID, metadata.patchId.value)
        put(EPOCH, metadata.pageEpoch.value)
        put(TARGET, metadata.target.value)
        put(INTERACTION_SEQUENCE, metadata.interactionSequence.value)
        put(BASE_REVISION, metadata.revision.base.value)
        put(NEXT_REVISION, metadata.revision.next.value)
    }.toString()

private fun PatchOperation.metadataValue(): String =
    when (this) {
        PatchOperation.REPLACE -> REPLACE_OPERATION
    }

private const val PROTOCOL_VERSION: String = "protocolVersion"
private const val OPERATION: String = "operation"
private const val PATCH_ID: String = "patchId"
private const val EPOCH: String = "epoch"
private const val TARGET: String = "target"
private const val INTERACTION_SEQUENCE: String = "interactionSequence"
private const val BASE_REVISION: String = "baseRevision"
private const val NEXT_REVISION: String = "nextRevision"
private const val REPLACE_OPERATION: String = "replace"
private val PATCH_METADATA_KEYS: Set<String> =
    setOf(
        PROTOCOL_VERSION,
        OPERATION,
        PATCH_ID,
        EPOCH,
        TARGET,
        INTERACTION_SEQUENCE,
        BASE_REVISION,
        NEXT_REVISION,
    )
