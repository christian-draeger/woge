package dev.woge.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionPatchTest {
    private val target = PatchTarget(PageEpoch.of("epoch-a"), RegionTargetId.of("summary-1"))

    @Test
    fun `all operations match the shared golden stream and decode at every transport boundary`() {
        val patches =
            listOf(
                ReplacePatch(PatchId.of("patch-1"), target, InteractionSequence.INITIAL, step(0), patchHtml {}),
                AppendPatch(
                    PatchId.of("patch-2"),
                    target,
                    InteractionSequence.INITIAL,
                    step(1),
                    patchItem(PatchItemId.of("item-1"), elementName = "li") { text("First") },
                ),
                RemovePatch(
                    PatchId.of("patch-3"),
                    target,
                    InteractionSequence.INITIAL,
                    step(2),
                    PatchItemId.of("item-1"),
                    target.region,
                ),
            )
        val bytes = PatchStreamV1.encode(patches)
        val golden = requireNotNull(javaClass.getResource("/fixtures/collection-patch-stream-v1.hex")).readText().trim()
        assertEquals(golden, bytes.joinToString("") { "%02x".format(it) })
        for (split in 0..bytes.size) {
            val decoder = PatchStreamV1.decoder()
            val events = decoder.feed(bytes.copyOfRange(0, split)) + decoder.feed(bytes.copyOfRange(split, bytes.size))
            decoder.finish()
            assertEquals(
                listOf(PatchOperation.REPLACE, PatchOperation.APPEND, PatchOperation.REMOVE),
                events.filterIsInstance<PatchStreamEvent.PatchFrame>().map { it.patch.operation },
            )
            val append = assertInstanceOf(AppendPatch::class.java, (events[1] as PatchStreamEvent.PatchFrame).patch)
            assertEquals(PatchItemId.of("item-1"), append.item.id)
            assertEquals("<li data-woge-item=\"item-1\">First</li>", append.item.html.value)
            assertEquals(PatchStreamEvent.Complete(3), events.last())
        }
    }

    @Test
    fun `item roots cannot override identity or admit an unsupported protocol version`() {
        assertThrows(IllegalArgumentException::class.java) {
            patchItem(PatchItemId.of("item-1"), attributes = { attribute("data-woge-item", "other") }) { text("First") }
        }
        val remove =
            RemovePatch(
                PatchId.of("remove"),
                target,
                InteractionSequence.INITIAL,
                step(0),
                PatchItemId.of("item-1"),
                target.region,
            )
        val metadata =
            dev.woge.protocol.internal
                .encodePatchMetadata(remove)
        val failure =
            assertThrows(PatchStreamException::class.java) {
                dev.woge.protocol.internal.decodePatchMetadata(
                    metadata.replace("\"protocolVersion\":1", "\"protocolVersion\":2"),
                )
            }
        assertEquals(PatchStreamErrorCode.UNSUPPORTED_VERSION, failure.code)
    }

    @Test
    fun `missing item metadata and nonempty removal payloads fail through stable protocol errors`() {
        val remove =
            RemovePatch(
                PatchId.of("remove"),
                target,
                InteractionSequence.INITIAL,
                step(0),
                PatchItemId.of("item-1"),
                target.region,
            )
        val metadata =
            dev.woge.protocol.internal
                .encodePatchMetadata(remove)
        val invalid =
            assertThrows(PatchStreamException::class.java) {
                dev.woge.protocol.internal
                    .decodePatchMetadata(metadata.replace("\"itemId\"", "\"unknown\""))
            }
        assertEquals(PatchStreamErrorCode.INVALID_METADATA, invalid.code)
        val bytes = PatchStreamV1.encode(listOf(remove))
        bytes[14] = 1 // Last byte of the first frame's declared payload length.
        val payload = assertThrows(PatchStreamException::class.java) { PatchStreamV1.decoder().feed(bytes) }
        assertEquals(PatchStreamErrorCode.INVALID_LENGTH, payload.code)
    }

    @Test
    fun `append validates its identified root including normal table rows before encoding`() {
        val row =
            AppendPatch(
                PatchId.of("row"),
                target,
                InteractionSequence.INITIAL,
                step(0),
                patchItem(PatchItemId.of("row-1"), elementName = "tr") { element("td") { text("Cell") } },
            )
        val decoded = PatchStreamV1.decoder().feed(PatchStreamV1.encode(listOf(row)))
        assertEquals(PatchOperation.APPEND, (decoded.first() as PatchStreamEvent.PatchFrame).patch.operation)
        val malformed =
            AppendPatch(
                PatchId.of("invalid"),
                target,
                InteractionSequence.INITIAL,
                step(0),
                PatchItem(PatchItemId.of("item-1"), patchHtml { element("li") { text("No identity") } }),
            )
        val problem = assertThrows(PatchStreamException::class.java) { PatchStreamV1.encode(listOf(malformed)) }
        assertEquals(PatchStreamErrorCode.INVALID_ITEM, problem.code)
    }

    private fun step(base: Long) = TargetRevisionStep.after(TargetRevision.of(base))
}
