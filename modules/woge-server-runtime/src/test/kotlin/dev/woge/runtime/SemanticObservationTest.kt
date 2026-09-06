package dev.woge.runtime

import dev.woge.host.CorrelationId
import dev.woge.host.RequestId
import dev.woge.host.RequestTrace
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObservationEvent
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.WogeOperationFinished
import dev.woge.host.WogeOperationStarted
import dev.woge.host.WogeOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SemanticObservationTest {
    @Test
    fun `operation emits ordered start and successful finish with correlation`() =
        runTest {
            val events = mutableListOf<WogeObservationEvent>()
            val context =
                WogeObservationContext(
                    requestTrace = RequestTrace(RequestId.of("request-1"), CorrelationId.of("correlation-1")),
                )

            val result =
                WogeObserver(events::add).observeOperation(WogeOperation.PAGE_REQUEST, context) {
                    "result"
                }

            assertEquals("result", result)
            assertEquals(2, events.size)
            assertTrue(events[0] is WogeOperationStarted)
            val finished = events[1] as WogeOperationFinished
            assertEquals(WogeOutcome.SUCCEEDED, finished.outcome)
            assertEquals(context, finished.context)
            assertTrue(finished.duration.isFinite() && finished.duration >= kotlin.time.Duration.ZERO)
        }

    @Test
    fun `flow collection classifies cancellation and emits one finish`() =
        runTest {
            val events = mutableListOf<WogeObservationEvent>()

            val thrown =
                runCatching {
                    flowOf("before")
                        .observeCollection(WogeObserver(events::add), WogeOperation.SHELL_RENDER)
                        .collect { throw CancellationException("client disconnected") }
                }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
            assertEquals(listOf(WogeOperation.SHELL_RENDER, WogeOperation.SHELL_RENDER), events.map { it.operation })
            assertEquals(WogeOutcome.CANCELLED, (events.last() as WogeOperationFinished).outcome)
        }

    @Test
    fun `observer failure never changes successful or failed application work`() =
        runTest {
            val observer = WogeObserver { error("telemetry unavailable") }
            assertEquals(42, observer.observeOperation(WogeOperation.ACTION) { 42 })

            val applicationFailure = IllegalStateException("application failure")
            val thrown =
                runCatching {
                    observer.observeOperation(WogeOperation.ACTION) { throw applicationFailure }
                }.exceptionOrNull()
            assertEquals(applicationFailure, thrown)
        }
}
