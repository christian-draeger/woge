package dev.woge.tck

import dev.woge.host.AuthenticationFacts
import dev.woge.host.RequestContext
import dev.woge.host.WebSocketAuthorizer
import dev.woge.host.WebSocketSubscription
import dev.woge.host.WebSocketUpgradePolicy
import dev.woge.host.WebSocketUseCase
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.Patch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.TargetRevision
import dev.woge.protocol.TargetRevisionStep
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter

/** Two collaborative cursor rooms: client movement in, authoritative HTML patches out, with bounded replay. */
public class CursorWebSocketFixture : WebSocketUseCase {
    private val rooms = mapOf("cursor-left" to CursorRoom("cursor-left"), "cursor-right" to CursorRoom("cursor-right"))

    override suspend fun subscribe(
        request: WebSocketSubscription,
        context: RequestContext,
    ): Flow<Patch>? {
        if (context.authentication !is AuthenticationFacts.Authenticated) return null
        val entry = request.revisions.entries.singleOrNull()
        val room = entry?.let { rooms[it.key.value] }
        return if (request.epoch.value == "socket-page" &&
            room != null &&
            entry.value.value <= room.revision
        ) {
            room.patches.filter { it.revision.next.value > entry.value.value }
        } else {
            null
        }
    }

    override suspend fun message(
        request: WebSocketSubscription,
        payload: String,
        context: RequestContext,
    ) {
        require(context.authentication is AuthenticationFacts.Authenticated)
        require(payload.matches(Regex("[0-9]{1,3},[0-9]{1,3}")))
        rooms
            .getValue(
                request.revisions.keys
                    .single()
                    .value,
            ).move(payload)
    }
}

public fun cursorWebSocketPolicy(): WebSocketUpgradePolicy =
    WebSocketUpgradePolicy(
        "http://woge.test",
        WebSocketAuthorizer { context, proof ->
            context.authentication is AuthenticationFacts.Authenticated && proof == "fixture-proof"
        },
    )

private class CursorRoom(
    private val target: String,
) {
    val patches = MutableSharedFlow<Patch>(replay = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    var revision = 0L
        private set

    init {
        move("0,0")
    }

    @Synchronized
    fun move(position: String) {
        val step = TargetRevisionStep.after(TargetRevision.of(revision))
        patches.tryEmit(
            ReplacePatch(
                patchId = PatchId.of("$target-${step.next.value}"),
                target = PatchTarget(PageEpoch.of("socket-page"), RegionTargetId.of(target)),
                interactionSequence = InteractionSequence.INITIAL,
                revision = step,
                html = patchHtml { element("p") { text("Cursor: $position") } },
            ),
        )
        revision = step.next.value
    }
}
