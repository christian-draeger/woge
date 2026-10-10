package dev.woge.html

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HtmlByteBudgetTest {
    @Test
    fun `cumulative allowance admits exact bytes and preserves its first failure`() {
        val budget = HtmlByteBudget(4)
        budget.consume(1)
        budget.consume(3)
        budget.consume(0)
        val exceeded = assertThrows(HtmlByteLimitException::class.java) { budget.consume(1) }
        assertEquals(4, exceeded.threshold)
        assertSame(exceeded, assertThrows(HtmlByteLimitException::class.java) { budget.consume(0) })
        assertEquals("WOGE_RESOURCE_LIMIT_EXCEEDED: HTML_BYTES threshold=4", exceeded.message)
    }

    @Test
    fun `byte accounting rejects invalid counts and avoids overflow`() {
        assertThrows(IllegalArgumentException::class.java) { HtmlByteBudget(0) }
        assertThrows(IllegalArgumentException::class.java) { HtmlByteBudget(-1) }
        val budget = HtmlByteBudget(Long.MAX_VALUE)
        budget.consume(Int.MAX_VALUE)
        budget.consume(Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { budget.consume(-1) }
    }
}
