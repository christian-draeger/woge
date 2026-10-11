package dev.woge.host

import dev.woge.protocol.PageEpoch
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.TargetRevision
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class SocketCommand(
    val op: String,
    val id: String = "",
    val epoch: String = "",
    val revisions: Map<String, String> = emptyMap(),
    val payload: String = "",
) {
    fun subscription(): WebSocketSubscription {
        require(revisions.isNotEmpty() && revisions.size <= SOCKET_MAX_TARGETS)
        require(revisions.values.all { value -> value.toLong().toString() == value })
        return WebSocketSubscription(
            id,
            PageEpoch.of(epoch),
            revisions.mapKeys { RegionTargetId.of(it.key) }.mapValues { TargetRevision.of(it.value.toLong()) },
        )
    }
}

internal fun socketCommand(
    text: String,
    limits: WebSocketLimits,
): SocketCommand {
    require(text.toByteArray(Charsets.UTF_8).size <= limits.inboundBytes)
    val command = Json.decodeFromString<SocketCommand>(text)
    require(command.op in setOf("subscribe", "unsubscribe", "message", "pong"))
    require(command.op == "pong" || command.id.length in 1..SOCKET_MAX_ID && command.id.all(::socketIdCharacter))
    return command
}

internal fun socketIdCharacter(value: Char): Boolean =
    value in 'a'..'z' || value in 'A'..'Z' || value in '0'..'9' || value in "_-"
