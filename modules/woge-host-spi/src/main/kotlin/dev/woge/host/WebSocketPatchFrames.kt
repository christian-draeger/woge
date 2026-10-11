package dev.woge.host

import dev.woge.protocol.Patch
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.PatchTransaction
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.TargetRevision

internal fun socketPatchFrame(
    request: WebSocketSubscription,
    revisions: MutableMap<RegionTargetId, TargetRevision>,
    patch: Patch,
    limits: WebSocketLimits,
): ByteArray {
    val patches = (patch as? PatchTransaction)?.patches ?: listOf(patch)
    val updated = revisions.toMutableMap()
    patches.forEach { member ->
        require(member.target.pageEpoch == request.epoch && updated[member.target.region] == member.revision.base)
        updated[member.target.region] = member.revision.next
    }
    val id = request.id.toByteArray(Charsets.US_ASCII)
    val bytes = PatchStreamV1.encode(listOf(patch))
    if (bytes.size + id.size + SOCKET_ENVELOPE_BYTES >
        limits.frameBytes
    ) {
        throw SocketClosed(SOCKET_OVERLOAD, "frame_limit")
    }
    revisions.clear()
    revisions.putAll(updated)
    return byteArrayOf(id.size.toByte()) + id + bytes
}

internal class SocketClosed(
    val code: Int,
    val reason: String,
) : IllegalStateException(reason)
