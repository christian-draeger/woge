package dev.woge.host

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PatchStreamLimitsTest {
    @Test
    fun `patch count and wire bytes admit exact thresholds and preserve terminal failure`() {
        val count = PatchStreamBudget(PatchStreamLimits(maxPatches = 1))
        count.admitPatch()
        val countFailure = assertThrows(ResourceLimitException::class.java) { count.admitPatch() }
        assertEquals(ResourceLimitExceeded(ResourceLimit.PATCH_COUNT, 1), countFailure.exceededLimit)
        assertSame(countFailure, assertThrows(ResourceLimitException::class.java) { count.consumeBytes(0) })

        val bytes = PatchStreamBudget(PatchStreamLimits(maxBytes = 4))
        bytes.consumeBytes(1)
        bytes.consumeBytes(3)
        bytes.consumeBytes(0)
        val byteFailure = assertThrows(ResourceLimitException::class.java) { bytes.consumeBytes(1) }
        assertEquals(ResourceLimitExceeded(ResourceLimit.PATCH_STREAM_BYTES, 4), byteFailure.exceededLimit)
        assertSame(byteFailure, assertThrows(ResourceLimitException::class.java) { bytes.admitPatch() })
        assertEquals("WOGE_RESOURCE_LIMIT_EXCEEDED: PATCH_STREAM_BYTES threshold=4", byteFailure.message)
    }

    @Test
    fun `invalid limits and byte counts are rejected without overflowing large allowances`() {
        assertThrows(IllegalArgumentException::class.java) { PatchStreamLimits(maxBytes = 0) }
        assertThrows(IllegalArgumentException::class.java) { PatchStreamLimits(maxPatches = 0) }
        val budget = PatchStreamBudget(PatchStreamLimits(maxBytes = Long.MAX_VALUE, maxPatches = Int.MAX_VALUE))
        budget.consumeBytes(Int.MAX_VALUE)
        budget.consumeBytes(Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { budget.consumeBytes(-1) }
    }
}
