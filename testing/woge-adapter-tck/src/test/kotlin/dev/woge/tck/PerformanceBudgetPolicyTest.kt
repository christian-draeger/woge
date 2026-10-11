package dev.woge.tck

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PerformanceBudgetPolicyTest {
    private val policy =
        Json
            .parseToJsonElement(
                """{"renderAllocatedBytesCeiling":200,"allocationTolerance":0.5}""",
            ).jsonObject

    @Test
    fun `allocation gate permits variance but rejects material growth`() {
        checkRenderAllocation(150, 100, policy)
        assertThrows<IllegalStateException> { checkRenderAllocation(151, 100, policy) }
        assertThrows<IllegalStateException> { checkRenderAllocation(0, 100, policy) }
    }

    @Test
    fun `a slower adapter baseline cannot move the portable ceiling`() {
        assertThrows<IllegalStateException> { checkRenderAllocation(201, 1_000, policy) }
    }
}
