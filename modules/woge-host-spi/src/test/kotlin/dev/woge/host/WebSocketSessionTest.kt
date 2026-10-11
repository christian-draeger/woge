package dev.woge.host

import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.Patch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.TargetRevision
import dev.woge.protocol.TargetRevisionStep
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketSessionTest {
    @Test
    fun `heartbeat and idle timeout use a test clock and release subscriptions`() =
        runTest {
            val peer = TestSocketPeer()
            val released = CompletableDeferred<Unit>()
            val useCase =
                TestSocketUseCase(
                    flow {
                        try {
                            awaitCancellation()
                        } finally {
                            released.complete(Unit)
                        }
                    },
                )
            val limits = WebSocketLimits(heartbeat = 10.milliseconds, idleTimeout = 30.milliseconds)
            launch { serveWebSocket(peer, socketContext(), useCase, limits) { testScheduler.currentTime } }
            peer.commands.send(SUBSCRIBE)
            runCurrent()
            advanceTimeBy(11)
            runCurrent()
            assertEquals(listOf("{\"op\":\"ping\"}"), peer.texts)
            peer.commands.send("{\"op\":\"pong\"}")
            advanceTimeBy(20)
            runCurrent()
            assertTrue(!peer.closed.isCompleted)
            advanceTimeBy(20)
            runCurrent()
            assertEquals(1001, peer.closed.await().first)
            assertTrue(released.isCompleted)
        }

    @Test
    fun `bounded queue closes a slow consumer and cancels its producer`() =
        runTest {
            val peer = TestSocketPeer(blockWrites = true)
            val released = CompletableDeferred<Unit>()
            val patches =
                flow {
                    try {
                        repeat(20) { emit(socketPatch(it.toLong())) }
                    } finally {
                        released.complete(Unit)
                    }
                }
            launch {
                serveWebSocket(peer, socketContext(), TestSocketUseCase(patches), WebSocketLimits(queuedFrames = 2))
            }
            peer.commands.send(SUBSCRIBE)
            runCurrent()
            assertEquals(1013 to "slow_consumer", peer.closed.await())
            assertTrue(released.isCompleted)
        }

    @Test
    fun `inbound rate limit and malformed commands close with policy status`() =
        runTest {
            for (commands in listOf(List(4) { "{\"op\":\"pong\"}" }, listOf("{\"op\":\"unknown\"}"))) {
                val peer = TestSocketPeer()
                launch {
                    serveWebSocket(
                        peer,
                        socketContext(),
                        TestSocketUseCase(flow {}),
                        WebSocketLimits(messagesPerSecond = 3),
                    )
                }
                commands.forEach { peer.commands.send(it) }
                runCurrent()
                assertEquals(1008, peer.closed.await().first)
            }
        }

    @Test
    fun `patch bytes decode with the existing IR and gaps are refused`() {
        val request = SocketCommand("subscribe", "one", "page", mapOf("cursor" to "0")).subscription()
        val revisions = request.revisions.toMutableMap()
        val frame = socketPatchFrame(request, revisions, socketPatch(0), WebSocketLimits())
        val decoder = PatchStreamV1.decoder()
        val events = decoder.feed(frame.copyOfRange(4, frame.size))
        decoder.finish()
        val decoded = (events.first() as PatchStreamEvent.PatchFrame).patch as ReplacePatch
        assertEquals(socketPatch(0).revision, decoded.revision)
        assertEquals(socketPatch(0).html, decoded.html)
        assertEquals(socketPatch(0).target, decoded.target)
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            socketPatchFrame(request, revisions, socketPatch(3), WebSocketLimits())
        }
    }
}

private class TestSocketPeer(
    private val blockWrites: Boolean = false,
) : WebSocketPeer {
    val commands = Channel<String>(64)
    val texts = mutableListOf<String>()
    val closed = CompletableDeferred<Pair<Int, String>>()
    override val incoming: Flow<String> = commands.receiveAsFlow()

    override suspend fun binary(bytes: ByteArray) {
        if (blockWrites) awaitCancellation()
    }

    override suspend fun text(value: String) {
        texts.add(value)
    }

    override suspend fun close(
        code: Int,
        reason: String,
    ) {
        closed.complete(code to reason)
    }
}

private class TestSocketUseCase(
    private val patches: Flow<Patch>,
) : WebSocketUseCase {
    override suspend fun subscribe(
        request: WebSocketSubscription,
        context: RequestContext,
    ): Flow<Patch> = patches

    override suspend fun message(
        request: WebSocketSubscription,
        payload: String,
        context: RequestContext,
    ): Unit = Unit
}

private fun socketContext(): RequestContext =
    RequestContext(RequestMethod.GET, RequestTrace(RequestId.of("socket"), CorrelationId.of("socket")))

private fun socketPatch(revision: Long): ReplacePatch =
    ReplacePatch(
        patchId = PatchId.of("patch-$revision"),
        target = PatchTarget(PageEpoch.of("page"), RegionTargetId.of("cursor")),
        interactionSequence = InteractionSequence.INITIAL,
        revision = TargetRevisionStep.after(TargetRevision.of(revision)),
        html = patchHtml { element("p") { text("Cursor") } },
    )

private const val SUBSCRIBE = """{"op":"subscribe","id":"one","epoch":"page","revisions":{"cursor":"0"}}"""
