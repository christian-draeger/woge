package dev.woge.protocol

import dev.woge.css.declarations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class PatchStreamCodecTest {
    @Test
    fun `replace and completion survive every two-chunk split`() {
        val encoded = PatchStreamV1.encode(listOf(examplePatch()))

        for (split in 0..encoded.size) {
            val decoder = PatchStreamV1.decoder()
            val events =
                decoder.feed(encoded.copyOfRange(0, split)) +
                    decoder.feed(encoded.copyOfRange(split, encoded.size))
            decoder.finish()
            assertDecodedExample(events)
        }
    }

    @Test
    fun `replace and completion survive one-byte reads`() {
        val decoder = PatchStreamV1.decoder()
        val events = PatchStreamV1.encode(listOf(examplePatch())).flatMap { decoder.feed(byteArrayOf(it)) }

        decoder.finish()

        assertDecodedExample(events)
    }

    @Test
    fun `golden hex locks the complete version-one representation`() {
        val expected =
            checkNotNull(javaClass.getResource("/fixtures/patch-stream-v1.hex"))
                .readText()
                .filterNot(Char::isWhitespace)

        assertEquals(expected, PatchStreamV1.encode(listOf(examplePatch())).toHex())
    }

    @Test
    fun `metadata is rejected before an active HTML payload is inspected`() {
        val unsupportedMetadata =
            "{" +
                "\"protocolVersion\":2," +
                "\"operation\":\"replace\"," +
                "\"patchId\":\"patch-1\"," +
                "\"epoch\":\"epoch-a\"," +
                "\"target\":\"summary-1\"," +
                "\"interactionSequence\":41," +
                "\"baseRevision\":7," +
                "\"nextRevision\":8}"
        val encoded = rawStream(rawFrame(PATCH_KIND, PATCH_CONTENT_TYPE, unsupportedMetadata, "<script>x()</script>"))

        assertProtocolFailure(PatchStreamErrorCode.UNSUPPORTED_VERSION) {
            PatchStreamV1.decoder().feed(encoded)
        }
    }

    @Test
    fun `encoder rejects executable patch content before emitting bytes`() {
        val activePayloads =
            listOf(
                "<script>alert(1)</script>",
                "<template><script>alert(1)</script></template>",
                "<img src=x onerror=alert(1)>",
                "<a href=\"java&#x73;cript:alert(1)\">bad</a>",
                "<iframe srcdoc=\"<p>active</p>\"></iframe>",
                "<img srcset=\"safe.png 1x, other.png 2x\">",
            )

        activePayloads.forEach { html ->
            val writes = mutableListOf<ByteArray>()
            val encoder = PatchStreamV1.encoder(ByteSink(writes::add))

            assertProtocolFailure(PatchStreamErrorCode.ACTIVE_CONTENT) {
                encoder.write(examplePatch(html = PatchHtml(html)))
            }
            assertTrue(writes.isEmpty())
        }
    }

    @Test
    fun `active-content policy permits modern styling custom elements and safe links`() {
        val html =
            patchHtml {
                element(
                    "woge-card",
                    attributes = {
                        classes("grid", "md:grid-cols-[1fr_auto]")
                        styles(declarations("container-type: inline-size;"))
                        styles(declarations("--accent: oklch(62% 0.2 250);"))
                        data("state", "ready")
                    },
                ) {
                    element("a", attributes = { url("href", dev.woge.html.applicationUrl("/projects")) }) {
                        text("Projects")
                    }
                }
            }

        val encoded = PatchStreamV1.encode(listOf(examplePatch(html)))

        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun `payload limit is enforced before HTML parsing or output`() {
        val writes = mutableListOf<ByteArray>()
        val encoder = PatchStreamV1.encoder(ByteSink(writes::add))
        val oversized = PatchHtml("x".repeat(PatchStreamV1.MAX_PAYLOAD_BYTES + 1))

        assertProtocolFailure(PatchStreamErrorCode.PAYLOAD_TOO_LARGE) {
            encoder.write(examplePatch(oversized))
        }
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `decoder rejects executable patch content before returning an event`() {
        val metadata = replaceMetadata()
        val encoded = rawStream(rawFrame(PATCH_KIND, PATCH_CONTENT_TYPE, metadata, "<svg onload=alert(1)></svg>"))

        assertProtocolFailure(PatchStreamErrorCode.ACTIVE_CONTENT) {
            PatchStreamV1.decoder().feed(encoded)
        }
    }

    @Test
    fun `malformed truncation and terminal violations use typed errors`() {
        assertProtocolFailure(PatchStreamErrorCode.INVALID_PREAMBLE) {
            PatchStreamV1.decoder().feed("NOPE!".toByteArray())
        }

        val unknownKind = PatchStreamV1.encode(listOf(examplePatch())).also { it[PREAMBLE_BYTES] = 99 }
        assertProtocolFailure(PatchStreamErrorCode.UNKNOWN_FRAME_KIND) {
            PatchStreamV1.decoder().feed(unknownKind)
        }

        val oversizedHeader =
            preamble() +
                frameHeader(
                    kind = PATCH_KIND,
                    contentTypeLength = 1,
                    metadataLength = PatchStreamV1.MAX_METADATA_BYTES + 1,
                    payloadLength = 0,
                )
        assertProtocolFailure(PatchStreamErrorCode.METADATA_TOO_LARGE) {
            PatchStreamV1.decoder().feed(oversizedHeader)
        }

        val encoded = PatchStreamV1.encode(listOf(examplePatch()))
        val truncated = PatchStreamV1.decoder()
        truncated.feed(encoded.copyOf(encoded.size - 1))
        assertProtocolFailure(PatchStreamErrorCode.TRUNCATED_STREAM) { truncated.finish() }

        val withoutTerminal = PatchStreamV1.decoder()
        withoutTerminal.feed(rawStream(rawFrame(PATCH_KIND, PATCH_CONTENT_TYPE, replaceMetadata(), SAFE_HTML)))
        assertProtocolFailure(PatchStreamErrorCode.MISSING_TERMINAL) { withoutTerminal.finish() }

        val afterTerminal = encoded + byteArrayOf(0)
        assertProtocolFailure(PatchStreamErrorCode.BYTES_AFTER_TERMINAL) {
            PatchStreamV1.decoder().feed(afterTerminal)
        }
    }

    @Test
    fun `non-canonical unknown and malformed metadata fail closed`() {
        val nonCanonical = replaceMetadata().replace("{", "{ ")
        val unknown = replaceMetadata().dropLast(1) + ",\"extra\":true}"
        val duplicate = replaceMetadata().dropLast(1) + ",\"target\":\"other\"}"
        val invalidUtf8 = byteArrayOf(0xc3.toByte(), 0x28)

        listOf(nonCanonical, unknown, duplicate).forEach { metadata ->
            assertProtocolFailure(PatchStreamErrorCode.INVALID_METADATA) {
                PatchStreamV1.decoder().feed(rawStream(rawFrame(PATCH_KIND, PATCH_CONTENT_TYPE, metadata, SAFE_HTML)))
            }
        }
        assertProtocolFailure(PatchStreamErrorCode.INVALID_UTF8) {
            PatchStreamV1.decoder().feed(
                rawStream(
                    rawFrame(
                        kind = PATCH_KIND,
                        contentType = PATCH_CONTENT_TYPE,
                        metadata = invalidUtf8,
                        payload = SAFE_HTML.toByteArray(),
                    ),
                ),
            )
        }
    }

    @Test
    fun `terminal error frame round trips only safe diagnostics`() {
        val failure =
            RemotePatchFailure(
                code = RemoteFailureCode.of("WOGE_RENDER_FAILED"),
                correlationId = RemoteCorrelationId.of("trace-42"),
                recovery = RecoveryIntent.RELOAD,
            )
        val output = mutableListOf<ByteArray>()
        PatchStreamV1.encoder(ByteSink(output::add)).error(failure)
        val decoder = PatchStreamV1.decoder()

        val events = decoder.feed(concatenate(output))
        decoder.finish()

        assertEquals(listOf(PatchStreamEvent.Error(failure)), events)
    }

    @Test
    fun `downstream failure propagates unchanged and makes encoder terminal`() {
        val writeFailure = IOException("connection closed")
        val encoder = PatchStreamV1.encoder(ByteSink { throw writeFailure })

        val first = assertThrows(IOException::class.java) { encoder.write(examplePatch()) }
        val second = assertThrows(IOException::class.java) { encoder.complete() }

        assertSame(writeFailure, first)
        assertSame(writeFailure, second)
    }

    private fun assertDecodedExample(events: List<PatchStreamEvent>) {
        assertEquals(2, events.size)
        val frame = assertInstanceOf(PatchStreamEvent.PatchFrame::class.java, events[0])
        val patch = assertInstanceOf(ReplacePatch::class.java, frame.patch)
        assertEquals("patch-1", patch.patchId.value)
        assertEquals("epoch-a", patch.target.pageEpoch.value)
        assertEquals("summary-1", patch.target.region.value)
        assertEquals(41, patch.interactionSequence.value)
        assertEquals(7, patch.revision.base.value)
        assertEquals(8, patch.revision.next.value)
        assertEquals(SAFE_HTML, patch.html.value)
        assertEquals(PatchStreamEvent.Complete(1), events[1])
    }
}

private fun examplePatch(html: PatchHtml = PatchHtml(SAFE_HTML)): ReplacePatch =
    ReplacePatch(
        patchId = PatchId.of("patch-1"),
        target = PatchTarget(PageEpoch.of("epoch-a"), RegionTargetId.of("summary-1")),
        interactionSequence = InteractionSequence.of(41),
        revision = TargetRevisionStep(TargetRevision.of(7), TargetRevision.of(8)),
        html = html,
    )

private fun replaceMetadata(): String =
    "{" +
        "\"protocolVersion\":1," +
        "\"operation\":\"replace\"," +
        "\"patchId\":\"patch-1\"," +
        "\"epoch\":\"epoch-a\"," +
        "\"target\":\"summary-1\"," +
        "\"interactionSequence\":41," +
        "\"baseRevision\":7," +
        "\"nextRevision\":8}"

private fun rawStream(vararg frames: ByteArray): ByteArray = preamble() + concatenate(frames.asList())

private fun rawFrame(
    kind: Int,
    contentType: String,
    metadata: String,
    payload: String,
): ByteArray =
    rawFrame(
        kind,
        contentType,
        metadata.toByteArray(StandardCharsets.UTF_8),
        payload.toByteArray(StandardCharsets.UTF_8),
    )

private fun rawFrame(
    kind: Int,
    contentType: String,
    metadata: ByteArray,
    payload: ByteArray,
): ByteArray {
    val contentTypeBytes = contentType.toByteArray(StandardCharsets.US_ASCII)
    return frameHeader(kind, contentTypeBytes.size, metadata.size, payload.size) +
        contentTypeBytes +
        metadata +
        payload
}

private fun frameHeader(
    kind: Int,
    contentTypeLength: Int,
    metadataLength: Int,
    payloadLength: Int,
): ByteArray =
    ByteBuffer
        .allocate(FRAME_HEADER_BYTES)
        .put(kind.toByte())
        .put(contentTypeLength.toByte())
        .putInt(metadataLength)
        .putInt(payloadLength)
        .array()

private fun preamble(): ByteArray = byteArrayOf(0x57, 0x4f, 0x47, 0x45, 0x01)

private fun concatenate(parts: List<ByteArray>): ByteArray {
    val result = ByteArray(parts.sumOf(ByteArray::size))
    var offset = 0
    parts.forEach { part ->
        part.copyInto(result, destinationOffset = offset)
        offset += part.size
    }
    return result
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun assertProtocolFailure(
    code: PatchStreamErrorCode,
    block: () -> Unit,
) {
    val failure = assertThrows(PatchStreamException::class.java, block)
    assertEquals(code, failure.code)
}

private const val PREAMBLE_BYTES: Int = 5
private const val FRAME_HEADER_BYTES: Int = 10
private const val PATCH_KIND: Int = 1
private const val PATCH_CONTENT_TYPE: String = "text/html; charset=utf-8"
private const val SAFE_HTML: String = "<p>Tasks &lt;today&gt;</p>"
