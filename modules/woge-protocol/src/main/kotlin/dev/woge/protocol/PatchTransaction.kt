package dev.woge.protocol

import dev.woge.protocol.internal.protocolFailure
import java.util.Collections

/** One bounded atomic unit of distinct replacements in the same document and interaction. */
public class PatchTransaction(
    patches: List<ReplacePatch>,
    public val viewTransition: Boolean = false,
) : Patch {
    public val patches: List<ReplacePatch> = Collections.unmodifiableList(patches.toList())

    init {
        require(patches.size in 1..MAX_MEMBERS) { "A transaction requires 1 to 128 replacements" }
        require(patches.map { it.target.region }.distinct().size == patches.size) {
            "Transaction targets must be distinct"
        }
        require(
            patches.all {
                it.target.pageEpoch == patches.first().target.pageEpoch &&
                    it.interactionSequence == patches.first().interactionSequence
            },
        ) { "Transaction members must share an epoch and interaction" }
    }

    override val protocolVersion: PatchProtocolVersion = PatchProtocolVersion.CURRENT
    override val operation: PatchOperation = PatchOperation.TRANSACTION
    override val patchId: PatchId get() = patches.first().patchId
    override val target: PatchTarget get() = patches.first().target
    override val interactionSequence: InteractionSequence get() = patches.first().interactionSequence
    override val revision: TargetRevisionStep get() = patches.first().revision

    override fun toString(): String = "PatchTransaction(members=${patches.size}, viewTransition=$viewTransition)"

    public companion object {
        public const val MAX_MEMBERS: Int = 128
    }
}

internal fun decodeTransaction(
    metadata: String,
    payload: ByteArray,
): PatchTransaction {
    if (metadata != transactionMetadata(false) && metadata != transactionMetadata(true)) {
        protocolFailure(PatchStreamErrorCode.INVALID_METADATA, "Transaction metadata is non-canonical")
    }
    // Reject nested envelopes before decoding their payloads, bounding depth as well as bytes.
    val decoder = VersionOnePatchStreamDecoder(allowTransactions = false)
    val events = decoder.feed(payload)
    decoder.finish()
    val members = events.mapNotNull { (it as? PatchStreamEvent.PatchFrame)?.patch as? ReplacePatch }
    if (events.size != members.size + 1 || events.lastOrNull() !is PatchStreamEvent.Complete) {
        protocolFailure(PatchStreamErrorCode.INVALID_METADATA, "A transaction contains only replacements")
    }
    return try {
        PatchTransaction(members, metadata == transactionMetadata(true))
    } catch (_: IllegalArgumentException) {
        protocolFailure(PatchStreamErrorCode.INVALID_METADATA, "Invalid transaction membership")
    }
}

internal fun transactionMetadata(viewTransition: Boolean): String = "{\"viewTransition\":$viewTransition}"

internal fun encodeTransactionMembers(patches: List<ReplacePatch>): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val encoder =
        PatchStreamV1.encoder(
            ByteSink { bytes ->
                if (bytes.size > PatchStreamV1.MAX_PAYLOAD_BYTES - output.size()) {
                    protocolFailure(
                        PatchStreamErrorCode.PAYLOAD_TOO_LARGE,
                        "Transaction payload exceeds the frame limit",
                    )
                }
                output.write(bytes)
            },
        )
    patches.forEach(encoder::write)
    encoder.complete()
    return output.toByteArray()
}
