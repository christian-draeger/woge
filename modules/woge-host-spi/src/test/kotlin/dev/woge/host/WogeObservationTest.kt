package dev.woge.host

import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class WogeObservationTest {
    @Test
    fun `semantic names and outcomes are stable low-cardinality values`() {
        assertEquals("page.request", WogeOperation.PAGE_REQUEST.semanticName)
        assertEquals("patch.apply", WogeOperation.PATCH_APPLY.semanticName)
        assertEquals("timed_out", WogeOutcome.TIMED_OUT.semanticName)
    }

    @Test
    fun `context contains correlation identifiers but no payload field`() {
        val context =
            WogeObservationContext(
                requestTrace = RequestTrace(RequestId.of("request-1"), CorrelationId.of("correlation-1")),
                target = PatchTarget(PageEpoch.of("page-1"), RegionTargetId.of("summary")),
                patchId = PatchId.of("patch-1"),
            )
        val event =
            WogeOperationFinished(
                WogeObservationId.of(1),
                WogeOperation.PATCH_ENCODE,
                WogeOutcome.SUCCEEDED,
                2.milliseconds,
                context,
            )

        assertEquals(
            "correlation-1",
            event.context.requestTrace
                ?.correlationId
                ?.value,
        )
        assertEquals(
            "summary",
            event.context.target
                ?.region
                ?.value,
        )
        assertFalse(WogeObservationContext::class.java.declaredFields.any { it.name in PAYLOAD_FIELD_NAMES })
    }

    @Test
    fun `finished event rejects unusable durations`() {
        assertThrows(IllegalArgumentException::class.java) {
            WogeOperationFinished(
                WogeObservationId.of(1),
                WogeOperation.PAGE_REQUEST,
                WogeOutcome.SUCCEEDED,
                Duration.INFINITE,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WogeOperationFinished(
                WogeObservationId.of(1),
                WogeOperation.PAGE_REQUEST,
                WogeOutcome.SUCCEEDED,
                (-1).milliseconds,
            )
        }
    }

    private companion object {
        val PAYLOAD_FIELD_NAMES: Set<String> =
            setOf("body", "command", "cookie", "exception", "headers", "html", "input", "message", "token")
    }
}
