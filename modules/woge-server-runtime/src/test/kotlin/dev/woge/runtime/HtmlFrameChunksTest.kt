package dev.woge.runtime

import dev.woge.host.ResourceLimit
import dev.woge.host.ResourceLimitExceeded
import dev.woge.host.WogeObservationEvent
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperationFinished
import dev.woge.host.WogeOutcome
import dev.woge.html.HtmlByteBudget
import dev.woge.html.HtmlByteLimitException
import dev.woge.protocol.htmlFrame
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HtmlFrameChunksTest {
    @Test
    fun `shared renderer bounds retained UTF-8 chunks cumulatively and reports safe rejection`() =
        runBlocking {
            val budget = HtmlByteBudget(4)
            val events = mutableListOf<WogeObservationEvent>()
            val observer = WogeObserver(events::add)
            val first = htmlFrame { text("\u00e9") }.renderByteChunks(budget, observer)
            assertEquals(2, first.sumOf { it.size })
            val exceeded =
                assertThrows(HtmlByteLimitException::class.java) {
                    runBlocking { htmlFrame { text("\u00e9x") }.renderByteChunks(budget, observer) }
                }
            assertEquals(4, exceeded.threshold)
            val rejected = events.filterIsInstance<WogeOperationFinished>().single()
            assertEquals(WogeOutcome.REJECTED, rejected.outcome)
            assertEquals(ResourceLimitExceeded(ResourceLimit.PAGE_BYTES, 4), rejected.context.exceededLimit)
        }
}
