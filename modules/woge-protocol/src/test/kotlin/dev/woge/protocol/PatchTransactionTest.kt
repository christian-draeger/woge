package dev.woge.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PatchTransactionTest {
    @Test
    fun `transaction matches browser golden and decodes as one unit at every boundary`() {
        val transaction = PatchTransaction(listOf(member("alpha"), member("beta")), viewTransition = true)
        val bytes = PatchStreamV1.encode(listOf(transaction))
        val golden =
            requireNotNull(
                javaClass.getResource("/fixtures/transaction-patch-stream-v1.hex"),
            ).readText().trim()
        assertEquals(golden, bytes.joinToString("") { "%02x".format(it) })
        for (split in 0..bytes.size) {
            val decoder = PatchStreamV1.decoder()
            val events = decoder.feed(bytes.copyOfRange(0, split)) + decoder.feed(bytes.copyOfRange(split, bytes.size))
            decoder.finish()
            assertEquals(2, events.size)
            val decoded =
                assertInstanceOf(
                    PatchTransaction::class.java,
                    (events.first() as PatchStreamEvent.PatchFrame).patch,
                )
            assertEquals(listOf("alpha", "beta"), decoded.patches.map { it.target.region.value })
            assertEquals(true, decoded.viewTransition)
            assertEquals(PatchStreamEvent.Complete(1), events.last())
        }
    }

    @Test
    fun `membership requires bounded distinct targets sharing epoch and interaction`() {
        for (members in listOf(
            emptyList(),
            listOf(member("alpha"), member("alpha")),
            listOf(member("alpha"), member("beta", epoch = "other")),
            listOf(member("alpha"), member("beta", interaction = 1)),
            (0..128).map { member("target-$it") },
        )) {
            assertThrows(IllegalArgumentException::class.java) { PatchTransaction(members) }
        }
        assertEquals(128, PatchTransaction((0 until 128).map { member("target-$it") }).patches.size)
    }

    @Test
    fun `members are an immutable snapshot`() {
        val source = mutableListOf(member("alpha"))
        val transaction = PatchTransaction(source)
        source.clear()
        assertEquals(1, transaction.patches.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (transaction.patches as MutableList).clear()
        }
    }

    @Test
    fun `combined payload rejects overflow before writing the outer preamble`() {
        val large = patchHtml { text("x".repeat(PatchStreamV1.MAX_PAYLOAD_BYTES / 2)) }
        val members = listOf(member("alpha", html = large), member("beta", html = large))
        var written = 0
        val encoder = PatchStreamV1.encoder(ByteSink { written += it.size })
        val problem = assertThrows(PatchStreamException::class.java) { encoder.write(PatchTransaction(members)) }
        assertEquals(PatchStreamErrorCode.PAYLOAD_TOO_LARGE, problem.code)
        assertEquals(0, written)
    }

    @Test
    fun `nested transactions nonreplace members and noncanonical metadata fail closed`() {
        val append =
            AppendPatch(
                PatchId.of("append"),
                member("alpha").target,
                InteractionSequence.INITIAL,
                TargetRevisionStep.after(TargetRevision.INITIAL),
                patchItem(PatchItemId.of("one")) {},
            )
        val nested = PatchTransaction(listOf(member("alpha")))
        for (payload in listOf(PatchStreamV1.encode(listOf(append)), PatchStreamV1.encode(listOf(nested)))) {
            val failure =
                assertThrows(PatchStreamException::class.java) {
                    PatchStreamV1.decoder().feed(envelope(payload))
                }
            assertEquals(PatchStreamErrorCode.INVALID_METADATA, failure.code)
        }
        val problem =
            assertThrows(PatchStreamException::class.java) {
                PatchStreamV1.decoder().feed(
                    envelope(PatchStreamV1.encode(listOf(member("alpha"))), "{\"viewTransition\":0}"),
                )
            }
        assertEquals(PatchStreamErrorCode.INVALID_METADATA, problem.code)
    }

    @Test
    fun `truncated transaction cannot emit its complete first member`() {
        val bytes = PatchStreamV1.encode(listOf(PatchTransaction(listOf(member("alpha"), member("beta")))))
        val decoder = PatchStreamV1.decoder()
        assertEquals(emptyList<PatchStreamEvent>(), decoder.feed(bytes.copyOfRange(0, bytes.size / 2)))
        assertEquals(
            PatchStreamErrorCode.TRUNCATED_STREAM,
            assertThrows(PatchStreamException::class.java) { decoder.finish() }.code,
        )
    }

    private fun member(
        id: String,
        epoch: String = "epoch-a",
        interaction: Long = 0,
        html: PatchHtml = patchHtml { element("p") { text(id) } },
    ): ReplacePatch =
        ReplacePatch(
            PatchId.of("patch-1"),
            PatchTarget(PageEpoch.of(epoch), RegionTargetId.of(id)),
            InteractionSequence.of(interaction),
            TargetRevisionStep.after(TargetRevision.INITIAL),
            html,
        )

    private fun envelope(
        payload: ByteArray,
        metadata: String = transactionMetadata(false),
    ): ByteArray {
        val contentType = "application/json; charset=utf-8".toByteArray()
        val metadataBytes = metadata.toByteArray()
        return PREAMBLE + frameHeader(FrameKind.TRANSACTION, contentType.size, metadataBytes.size, payload.size) +
            contentType + metadataBytes + payload
    }
}
