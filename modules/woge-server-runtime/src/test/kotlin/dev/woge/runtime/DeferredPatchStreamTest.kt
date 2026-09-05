package dev.woge.runtime

import dev.woge.host.DeferredRegion
import dev.woge.host.deferredRegion
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.TargetRevision
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeferredPatchStreamTest {
    @Test
    fun `region update maps to one initial-interaction revision step`() {
        val update = resolved("summary", "Ready", initialRevision = 7)

        val patch = update.toReplacePatch(PatchId.of("patch-1"))

        assertEquals("patch-1", patch.patchId.value)
        assertEquals(update.region.target, patch.target)
        assertEquals(InteractionSequence.INITIAL, patch.interactionSequence)
        assertEquals(7, patch.revision.base.value)
        assertEquals(8, patch.revision.next.value)
        assertEquals("<p>Ready</p>", patch.html.value)
    }

    @Test
    fun `first completed region is encoded before the stream completes`() =
        runTest {
            val slow = CompletableDeferred<dev.woge.protocol.PatchHtml>()
            val fast = CompletableDeferred<dev.woge.protocol.PatchHtml>()
            var patchNumber = 0
            val chunks = Channel<EncodedPatchChunk>(Channel.UNLIMITED)
            val collection =
                async {
                    DeferredRegionExecutor()
                        .execute(
                            listOf(
                                region("slow") { slow.await() },
                                region("fast") { fast.await() },
                            ),
                        ).encodeDeferredPatchStream {
                            patchNumber += 1
                            PatchId.of("patch-$patchNumber")
                        }.collect(chunks::send)
                }
            val decoder = PatchStreamV1.decoder()

            runCurrent()
            fast.complete(html("Fast result"))
            runCurrent()
            val first = chunks.receive()
            val firstEvent =
                assertInstanceOf(
                    PatchStreamEvent.PatchFrame::class.java,
                    decoder.feed(first.bytes).single(),
                )
            assertEquals("fast", firstEvent.patch.target.region.value)
            assertFalse(first.terminal)
            assertFalse(collection.isCompleted)

            slow.complete(html("Slow result"))
            runCurrent()
            val second = chunks.receive()
            val secondEvent =
                assertInstanceOf(
                    PatchStreamEvent.PatchFrame::class.java,
                    decoder.feed(second.bytes).single(),
                )
            assertEquals("slow", secondEvent.patch.target.region.value)
            assertFalse(second.terminal)

            val terminal = chunks.receive()
            assertEquals(PatchStreamEvent.Complete(2), decoder.feed(terminal.bytes).single())
            assertTrue(terminal.terminal)
            collection.await()
            decoder.finish()
        }

    @Test
    fun `encoded deferred stream matches the shared browser golden fixture`() =
        runTest {
            val patchIds = ArrayDeque(listOf(PatchId.of("deferred-1"), PatchId.of("deferred-2")))
            val encoded =
                flowOf(
                    resolved("fast", "Fast result"),
                    resolved("slow", "Slow result"),
                ).encodeDeferredPatchStream { patchIds.removeFirst() }
                    .toList()
                    .flatMap { it.bytes.asIterable() }
                    .toByteArray()
            val expected =
                checkNotNull(javaClass.getResource("/fixtures/deferred-patch-stream-v1.hex"))
                    .readText()
                    .filterNot(Char::isWhitespace)

            assertEquals(expected, encoded.toHex())
        }
}

private fun resolved(
    id: String,
    text: String,
    initialRevision: Long = 0,
): DeferredRegionUpdate.Resolved {
    val region = region(id, initialRevision = initialRevision) { html(text) }
    return DeferredRegionUpdate.Resolved(region, html(text))
}

private fun region(
    id: String,
    initialRevision: Long = 0,
    content: suspend () -> dev.woge.protocol.PatchHtml,
): DeferredRegion =
    deferredRegion(
        target = PatchTarget(PageEpoch.of("page-1"), RegionTargetId.of(id)),
        initialRevision = TargetRevision.of(initialRevision),
        loading = { element("p") { text("Loading") } },
        onFailure = { patchHtml { element("p") { text("Unavailable") } } },
        content = content,
    )

private fun html(text: String): dev.woge.protocol.PatchHtml = patchHtml { element("p") { text(text) } }

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
