package dev.woge.runtime

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
