package dev.woge.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class PatchStreamFuzzTest {
    @Test
    fun `seeded chunks truncations and byte mutations stay bounded and typed`() {
        val iterations = fuzzIterations()
        val random = Random(FUZZ_SEED)
        val bytes = PatchStreamV1.encode(listOf(fuzzPatch()))
        val expected = decode(bytes)

        repeat(iterations) {
            val decoder = PatchStreamV1.decoder()
            val actual = mutableListOf<PatchStreamEvent>()
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(bytes.size, offset + random.nextInt(1, MAX_CHUNK_BYTES + 1))
                actual += decoder.feed(bytes.copyOfRange(offset, end))
                offset = end
            }
            decoder.finish()
            assertEquals(expected.map(::eventSummary), actual.map(::eventSummary))

            val cut = random.nextInt(bytes.size)
            assertTypedTruncation(bytes.copyOfRange(0, cut))

            val mutated = bytes.copyOf()
            val index = random.nextInt(mutated.size)
            mutated[index] = random.nextInt(256).toByte()
            assertTypedOrValid(mutated)
        }
    }

    @Test
    fun `regression fixtures have stable documented parser failures`() {
        val expected =
            mapOf(
                "unsupported-version" to PatchStreamErrorCode.UNSUPPORTED_VERSION,
                "oversized-metadata" to PatchStreamErrorCode.METADATA_TOO_LARGE,
                "truncated-header" to PatchStreamErrorCode.TRUNCATED_STREAM,
                "unknown-frame-kind" to PatchStreamErrorCode.UNKNOWN_FRAME_KIND,
                "oversized-payload" to PatchStreamErrorCode.PAYLOAD_TOO_LARGE,
            )
        expected.forEach { (name, code) ->
            val bytes = readFixture(name)
            val decoder = PatchStreamV1.decoder()
            val failure =
                assertThrows(PatchStreamException::class.java) {
                    decoder.feed(bytes)
                    decoder.finish()
                }
            assertEquals(code, failure.code, name)
        }
    }

    @Test
    fun `declared oversized lengths fail on the header without buffering the declaration`() {
        listOf(
            header(metadataLength = PatchStreamV1.MAX_METADATA_BYTES + 1, payloadLength = 0) to
                PatchStreamErrorCode.METADATA_TOO_LARGE,
            header(metadataLength = 0, payloadLength = PatchStreamV1.MAX_PAYLOAD_BYTES + 1) to
                PatchStreamErrorCode.PAYLOAD_TOO_LARGE,
        ).forEach { (stream, code) ->
            val decoder = PatchStreamV1.decoder()
            val failure = assertThrows(PatchStreamException::class.java) { decoder.feed(stream) }
            assertEquals(code, failure.code)
        }
    }

    private fun assertTypedTruncation(prefix: ByteArray) {
        val decoder = PatchStreamV1.decoder()
        val partialEvents = mutableListOf<PatchStreamEvent>()
        val feedFailure = runCatching { partialEvents += decoder.feed(prefix) }.exceptionOrNull()
        assertTrue(partialEvents.none { it is PatchStreamEvent.Complete })
        val failure =
            if (feedFailure == null) {
                assertThrows(PatchStreamException::class.java) { decoder.finish() }
            } else {
                assertTrue(feedFailure is PatchStreamException)
                feedFailure as PatchStreamException
            }
        assertTrue(
            failure.code in
                setOf(
                    PatchStreamErrorCode.INVALID_PREAMBLE,
                    PatchStreamErrorCode.TRUNCATED_STREAM,
                    PatchStreamErrorCode.MISSING_TERMINAL,
                ),
        )
    }

    private fun assertTypedOrValid(bytes: ByteArray) {
        val decoder = PatchStreamV1.decoder()
        try {
            decoder.feed(bytes)
            decoder.finish()
        } catch (failure: PatchStreamException) {
            assertTrue(failure.code in PatchStreamErrorCode.entries)
        }
    }

    private fun readFixture(name: String): ByteArray {
        val text =
            checkNotNull(javaClass.getResource("/fuzz-regressions/$name.hex"))
                .readText()
                .filterNot(Char::isWhitespace)
        return text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun header(
        metadataLength: Int,
        payloadLength: Int,
    ): ByteArray =
        byteArrayOf(0x57, 0x4f, 0x47, 0x45, 0x01) +
            byteArrayOf(1, 24) +
            metadataLength.toUIntBytes() + payloadLength.toUIntBytes()

    private fun Int.toUIntBytes(): ByteArray =
        byteArrayOf((this ushr 24).toByte(), (this ushr 16).toByte(), (this ushr 8).toByte(), toByte())

    private fun eventSummary(event: PatchStreamEvent): String =
        when (event) {
            is PatchStreamEvent.PatchFrame ->
                "patch:${event.patch.patchId.value}:${event.patch.target.pageEpoch.value}:" +
                    "${event.patch.target.region.value}:${event.patch.interactionSequence.value}:" +
                    "${event.patch.revision.base.value}:${event.patch.revision.next.value}:" +
                    when (val patch = event.patch) {
                        is ReplacePatch -> patch.html.value
                        is AppendPatch -> patch.item.html.value
                        is RemovePatch -> ""
                        is PatchTransaction -> error("No transaction in this fixture")
                    }
            is PatchStreamEvent.Complete -> "complete:${event.patchCount}"
            is PatchStreamEvent.Error -> "error:${event.failure.code.value}:${event.failure.recovery}"
        }

    private fun decode(bytes: ByteArray): List<PatchStreamEvent> {
        val decoder = PatchStreamV1.decoder()
        val events = decoder.feed(bytes)
        decoder.finish()
        return events
    }

    private fun fuzzPatch(): ReplacePatch =
        ReplacePatch(
            patchId = PatchId.of("fuzz-patch"),
            target = PatchTarget(PageEpoch.of("epoch-fuzz"), RegionTargetId.of("region-fuzz")),
            interactionSequence = InteractionSequence.of(1),
            revision = TargetRevisionStep.after(TargetRevision.of(0)),
            html = PatchHtml("<p>€ bounded fuzz input</p>"),
        )

    private fun fuzzIterations(): Int =
        (System.getProperty("woge.fuzz.iterations") ?: System.getenv("WOGE_FUZZ_ITERATIONS"))
            ?.toIntOrNull()
            ?.coerceIn(1, MAX_FUZZ_ITERATIONS)
            ?: DEFAULT_FUZZ_ITERATIONS

    private companion object {
        const val FUZZ_SEED: Long = 0x574f4745
        const val MAX_CHUNK_BYTES: Int = 32
        const val DEFAULT_FUZZ_ITERATIONS: Int = 128
        const val MAX_FUZZ_ITERATIONS: Int = 100_000
    }
}
