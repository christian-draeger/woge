package dev.woge.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PatchTest {
    @Test
    fun `replace patch carries the complete semantic contract`() {
        val patch = examplePatch()

        assertEquals(PatchProtocolVersion.CURRENT, patch.protocolVersion)
        assertEquals(PatchOperation.REPLACE, patch.operation)
        assertEquals("patch-1", patch.patchId.value)
        assertEquals("epoch-a", patch.target.pageEpoch.value)
        assertEquals("summary-1", patch.target.region.value)
        assertEquals(41, patch.interactionSequence.value)
        assertEquals(7, patch.revision.base.value)
        assertEquals(8, patch.revision.next.value)
        assertEquals("<p>Tasks &lt;today&gt;</p>", patch.html.value)
    }

    @Test
    fun `replace patch serializes deterministically for a golden fixture`() {
        val expected =
            checkNotNull(javaClass.getResource("/fixtures/replace-patch-v1.json"))
                .readText()
                .trim()

        assertEquals(expected, serializeFixture(examplePatch()))
    }

    @Test
    fun `target identity rejects CSS selectors and unsafe attribute text`() {
        val invalidTargets = listOf("#summary", "[data-region]", ".summary", "summary value", "summary/child")

        invalidTargets.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                RegionTargetId.of(value)
            }
        }
    }

    @Test
    fun `revision step rejects duplicates gaps and overflow`() {
        val revision = TargetRevision.of(7)

        assertEquals(TargetRevision.of(8), TargetRevisionStep.after(revision).next)
        assertThrows(IllegalArgumentException::class.java) {
            TargetRevisionStep(revision, TargetRevision.of(7))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetRevisionStep(revision, TargetRevision.of(9))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetRevisionStep.after(TargetRevision.of(Long.MAX_VALUE))
        }
    }

    @Test
    fun `unknown protocol versions cannot form a replace patch`() {
        assertThrows(IllegalArgumentException::class.java) {
            examplePatch(protocolVersion = PatchProtocolVersion.of(2))
        }
    }

    @Test
    fun `patch diagnostics redact rendered HTML`() {
        val patch = examplePatch()

        assertFalse(patch.toString().contains("Tasks"))
        assertTrue(patch.toString().contains("content=<redacted>"))
    }
}

private fun examplePatch(protocolVersion: PatchProtocolVersion = PatchProtocolVersion.CURRENT): ReplacePatch =
    ReplacePatch(
        patchId = PatchId.of("patch-1"),
        target = PatchTarget(PageEpoch.of("epoch-a"), RegionTargetId.of("summary-1")),
        interactionSequence = InteractionSequence.of(41),
        revision = TargetRevisionStep(TargetRevision.of(7), TargetRevision.of(8)),
        html =
            patchHtml {
                element("p") { text("Tasks <today>") }
            },
        protocolVersion = protocolVersion,
    )

private fun serializeFixture(patch: Patch): String =
    when (patch) {
        is ReplacePatch ->
            "{" +
                "\"protocolVersion\":${patch.protocolVersion.value}," +
                "\"operation\":\"${patch.operation.fixtureName()}\"," +
                "\"patchId\":${patch.patchId.value.asJsonString()}," +
                "\"epoch\":${patch.target.pageEpoch.value.asJsonString()}," +
                "\"target\":${patch.target.region.value.asJsonString()}," +
                "\"interactionSequence\":${patch.interactionSequence.value}," +
                "\"baseRevision\":${patch.revision.base.value}," +
                "\"nextRevision\":${patch.revision.next.value}," +
                "\"html\":${patch.html.value.asJsonString()}" +
                "}"
    }

private fun PatchOperation.fixtureName(): String =
    when (this) {
        PatchOperation.REPLACE -> "replace"
    }

private fun String.asJsonString(): String =
    buildString {
        append('"')
        this@asJsonString.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
