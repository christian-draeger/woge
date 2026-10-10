package dev.woge.runtime

import dev.woge.host.PatchStreamLimits
import dev.woge.host.ResourceLimit
import dev.woge.host.ResourceLimitExceeded
import dev.woge.host.ResourceLimitException
import dev.woge.host.WogeObservationEvent
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperationFinished
import dev.woge.host.WogeOutcome
import dev.woge.protocol.AppendPatch
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.Patch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchItemId
import dev.woge.protocol.PatchOperation
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.RemovePatch
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.TargetRevision
import dev.woge.protocol.TargetRevisionStep
import dev.woge.protocol.patchHtml
import dev.woge.protocol.patchItem
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SemanticPatchStreamTest {
    @Test
    fun `cumulative stream byte allowance includes framing and terminal and resets on fresh collection`() =
        runTest {
            val patches = collectionPatches()
            val baseline = patches.asFlow().encodePatchStream().toList()
            val total = baseline.sumOf { it.bytes.size.toLong() }
            val exact = patches.asFlow().encodePatchStream(limits = PatchStreamLimits(maxBytes = total))
            repeat(2) {
                assertEquals(total, exact.toList().sumOf { it.bytes.size.toLong() })
            }
            val events = mutableListOf<WogeObservationEvent>()
            val chunks = mutableListOf<EncodedPatchChunk>()
            val exceeded =
                runCatching {
                    patches
                        .asFlow()
                        .encodePatchStream(
                            observer = WogeObserver(events::add),
                            limits = PatchStreamLimits(maxBytes = total - 1),
                        ).toList(chunks)
                }.exceptionOrNull() as ResourceLimitException
            assertEquals(ResourceLimitExceeded(ResourceLimit.PATCH_STREAM_BYTES, total - 1), exceeded.exceededLimit)
            assertTrue(chunks.none { it.terminal })
            assertTrue(chunks.sumOf { it.bytes.size.toLong() } <= total - 1)
            val rejected = events.filterIsInstance<WogeOperationFinished>().single { it.context.exceededLimit != null }
            assertEquals(exceeded.exceededLimit, rejected.context.exceededLimit)
            assertEquals(WogeOutcome.REJECTED, rejected.outcome)
        }

    @Test
    fun `incremental patch count exhaustion stops infinite upstream and emits no terminal`() =
        runTest {
            var prepared = 0
            val events = mutableListOf<WogeObservationEvent>()
            val chunks = mutableListOf<EncodedPatchChunk>()
            val exceeded =
                runCatching {
                    flow {
                        while (true) {
                            prepared += 1
                            emit(collectionPatches().first())
                        }
                    }.encodePatchStream(
                        observer = WogeObserver(events::add),
                        limits = PatchStreamLimits(maxPatches = 1),
                    ).toList(chunks)
                }.exceptionOrNull() as ResourceLimitException
            assertEquals(2, prepared)
            assertEquals(2, chunks.size)
            assertTrue(chunks.none { it.terminal })
            assertEquals(ResourceLimitExceeded(ResourceLimit.PATCH_COUNT, 1), exceeded.exceededLimit)
            assertTrue(
                events.filterIsInstance<WogeOperationFinished>().any {
                    it.outcome == WogeOutcome.REJECTED && it.context.exceededLimit == exceeded.exceededLimit
                },
            )
        }

    @Test
    fun `preamble exhaustion reports rejection without emitting any bytes`() =
        runTest {
            val events = mutableListOf<WogeObservationEvent>()
            val chunks = mutableListOf<EncodedPatchChunk>()
            val exceeded =
                runCatching {
                    collectionPatches()
                        .asFlow()
                        .encodePatchStream(
                            observer = WogeObserver(events::add),
                            limits = PatchStreamLimits(maxBytes = 1),
                        ).toList(chunks)
                }.exceptionOrNull() as ResourceLimitException
            assertEquals(ResourceLimitExceeded(ResourceLimit.PATCH_STREAM_BYTES, 1), exceeded.exceededLimit)
            assertTrue(chunks.isEmpty())
            assertEquals(WogeOutcome.REJECTED, events.filterIsInstance<WogeOperationFinished>().single().outcome)
        }

    @Test
    fun `every operation has one flush boundary and successful observation`() =
        runTest {
            val observations = mutableListOf<WogeObservationEvent>()
            val patches = collectionPatches()
            val chunks = patches.asFlow().encodePatchStream(WogeObserver(observations::add)).toList()
            val decoder = PatchStreamV1.decoder()

            assertEquals(5, chunks.size)
            assertTrue(decoder.feed(chunks.first().bytes).isEmpty())
            assertFalse(chunks.first().terminal)
            val operations =
                chunks.drop(1).dropLast(1).map { chunk ->
                    assertFalse(chunk.terminal)
                    (decoder.feed(chunk.bytes).single() as PatchStreamEvent.PatchFrame).patch.operation
                }
            assertEquals(listOf(PatchOperation.REPLACE, PatchOperation.APPEND, PatchOperation.REMOVE), operations)
            assertTrue(chunks.last().terminal)
            assertEquals(listOf(PatchStreamEvent.Complete(3)), decoder.feed(chunks.last().bytes))
            decoder.finish()
            val finished = observations.filterIsInstance<WogeOperationFinished>()
            assertEquals(patches.map { it.patchId }, finished.map { it.context.patchId })
            assertTrue(finished.all { it.outcome == WogeOutcome.SUCCEEDED })
        }

    @Test
    fun `upstream failure preserves emitted patches but never creates a successful terminal`() =
        runTest {
            val failure = IllegalStateException("collection unavailable")
            val chunks = mutableListOf<EncodedPatchChunk>()
            val thrown =
                runCatching {
                    flow {
                        emit(collectionPatches()[1])
                        throw failure
                    }.encodePatchStream().toList(chunks)
                }.exceptionOrNull()

            assertSame(failure, thrown)
            assertEquals(2, chunks.size)
            assertTrue(chunks.none { it.terminal })
        }

    private fun collectionPatches(): List<Patch> {
        val target = PatchTarget(PageEpoch.of("page-1"), RegionTargetId.of("items"))
        val itemId = PatchItemId.of("item-1")
        return listOf(
            ReplacePatch(PatchId.of("replace"), target, InteractionSequence.INITIAL, step(0), patchHtml {}),
            AppendPatch(
                PatchId.of("append"),
                target,
                InteractionSequence.INITIAL,
                step(1),
                patchItem(itemId, elementName = "li") { text("First") },
            ),
            RemovePatch(PatchId.of("remove"), target, InteractionSequence.INITIAL, step(2), itemId, target.region),
        )
    }

    private fun step(base: Long) = TargetRevisionStep.after(TargetRevision.of(base))
}
