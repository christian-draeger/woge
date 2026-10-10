package dev.woge.protocol.internal

import dev.woge.protocol.AppendPatch
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.Patch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchItemId
import dev.woge.protocol.PatchOperation
import dev.woge.protocol.PatchProtocolVersion
import dev.woge.protocol.PatchStreamErrorCode
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.RemovePatch
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.TargetRevision
import dev.woge.protocol.TargetRevisionStep
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class PatchMetadata(
    val protocolVersion: PatchProtocolVersion,
    val patchId: PatchId,
    val pageEpoch: PageEpoch,
    val target: RegionTargetId,
    val interactionSequence: InteractionSequence,
    val revision: TargetRevisionStep,
    val operation: PatchOperation = PatchOperation.REPLACE,
    val itemId: PatchItemId? = null,
    val focusTarget: RegionTargetId? = null,
)

internal fun encodePatchMetadata(patch: Patch): String =
    encodePatchMetadata(
        PatchMetadata(
            protocolVersion = patch.protocolVersion,
            patchId = patch.patchId,
            pageEpoch = patch.target.pageEpoch,
            target = patch.target.region,
            interactionSequence = patch.interactionSequence,
            revision = patch.revision,
            operation = patch.operation,
            itemId =
                when (patch) {
                    is AppendPatch -> patch.item.id
                    is RemovePatch -> patch.itemId
                    is ReplacePatch -> null
                },
            focusTarget = (patch as? RemovePatch)?.focusTarget,
        ),
    )

internal fun decodePatchMetadata(value: String): PatchMetadata =
    parseMetadata(value, { objectValue ->
        when (objectValue.requiredString(OPERATION)) {
            REPLACE_OPERATION -> PATCH_METADATA_KEYS
            APPEND_OPERATION -> PATCH_METADATA_KEYS + ITEM_ID
            REMOVE_OPERATION -> PATCH_METADATA_KEYS + ITEM_ID + FOCUS_TARGET
            else -> invalidMetadata()
        }
    }) { objectValue ->
        val operation =
            when (objectValue.requiredString(OPERATION)) {
                REPLACE_OPERATION -> PatchOperation.REPLACE
                APPEND_OPERATION -> PatchOperation.APPEND
                REMOVE_OPERATION -> PatchOperation.REMOVE
                else -> invalidMetadata()
            }

        val metadata =
            try {
                PatchMetadata(
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
                    operation = operation,
                    itemId =
                        if (operation ==
                            PatchOperation.REPLACE
                        ) {
                            null
                        } else {
                            PatchItemId.of(objectValue.requiredString(ITEM_ID))
                        },
                    focusTarget =
                        if (operation ==
                            PatchOperation.REMOVE
                        ) {
                            RegionTargetId.of(objectValue.requiredString(FOCUS_TARGET))
                        } else {
                            null
                        },
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
        if (encodePatchMetadata(metadata) != value) invalidMetadata()
        metadata
    }

private fun encodePatchMetadata(metadata: PatchMetadata): String =
    buildJsonObject {
        put(PROTOCOL_VERSION, metadata.protocolVersion.value)
        put(OPERATION, metadata.operation.metadataValue())
        put(PATCH_ID, metadata.patchId.value)
        put(EPOCH, metadata.pageEpoch.value)
        put(TARGET, metadata.target.value)
        put(INTERACTION_SEQUENCE, metadata.interactionSequence.value)
        put(BASE_REVISION, metadata.revision.base.value)
        put(NEXT_REVISION, metadata.revision.next.value)
        metadata.itemId?.let { put(ITEM_ID, it.value) }
        metadata.focusTarget?.let { put(FOCUS_TARGET, it.value) }
    }.toString()

private fun PatchOperation.metadataValue(): String =
    when (this) {
        PatchOperation.REPLACE -> REPLACE_OPERATION
        PatchOperation.APPEND -> APPEND_OPERATION
        PatchOperation.REMOVE -> REMOVE_OPERATION
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
private const val APPEND_OPERATION: String = "append"
private const val REMOVE_OPERATION: String = "remove"
private const val ITEM_ID: String = "itemId"
private const val FOCUS_TARGET: String = "focusTarget"
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
