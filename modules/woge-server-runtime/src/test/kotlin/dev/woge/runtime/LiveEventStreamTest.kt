package dev.woge.runtime

import dev.woge.host.CorrelationId
import dev.woge.host.FailureCategory
import dev.woge.host.LiveLimits
import dev.woge.host.LiveSessionKey
import dev.woge.host.LiveUseCase
import dev.woge.host.PageIdentity
import dev.woge.host.PageRegion
import dev.woge.host.PageRequest
import dev.woge.host.RegionTarget
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestTrace
import dev.woge.host.ResourceLimit
import dev.woge.host.WogeObservationEvent
import dev.woge.host.WogeOperationFinished
import dev.woge.host.WogeOutcome
import dev.woge.host.liveRefused
import dev.woge.host.liveSubscription
import dev.woge.html.HtmlWriter
import dev.woge.protocol.PageEpoch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LiveEventStreamTest {
    private val page = PageIdentity(PageEpoch.of("page-1"), RenderIdentitySecret.of(ByteArray(32)))
    private val tasks = target("tasks")
    private val summary = target("summary")
    private val other = target("other")

    @Test
    fun `stream starts with a retry hint and sends coalesced invalidations with increasing ids`() =
        runTest {
            val text =
                stream(flowOf(tasks, tasks, summary, tasks), lastEventId = null).toText()
            assertEquals(
                "retry: 3000\n\n" +
                    "id: 1\nevent: invalidate\ndata: ${tasks.id}\ndata: ${summary.id}\n\n",
                text,
            )
        }

    @Test
    fun `reconnect starts with a resync of every declared target after the last seen id`() =
        runTest {
            val text = stream(flowOf(summary), lastEventId = "41").toText()
            assertEquals(
                "retry: 3000\n\n" +
                    "id: 42\nevent: resync\ndata: ${tasks.id}\ndata: ${summary.id}\n\n" +
                    "id: 43\nevent: invalidate\ndata: ${summary.id}\n\n",
                text,
            )
            assertTrue(stream(emptyFlow(), lastEventId = "not-a-number").toText().contains("id: 1\nevent: resync\n"))
        }

    @Test
    fun `idle streams send heartbeats and end at the maximum lifetime`() =
        runTest {
            val limits = LiveLimits(heartbeat = 15.seconds, maxLifetime = 50.seconds)
            val never = Channel<RegionTarget<*>>()
            val text = stream(never.consumeAsFlow(), lastEventId = null, limits = limits).toText()
            assertEquals("retry: 3000\n\n" + ": heartbeat\n\n".repeat(3), text)
        }

    @Test
    fun `a slow browser makes the server coalesce instead of queue`() =
        runTest {
            val source = Channel<RegionTarget<*>>(Channel.UNLIMITED)
            val events = stream(source.consumeAsFlow(), lastEventId = null)
            val received = async { events.take(3).map { it.decodeToString() }.toList() }
            runCurrent()
            source.send(tasks)
            runCurrent()
            repeat(100) { source.send(summary) }
            source.send(tasks)
            runCurrent()
            val chunks = received.await()
            assertEquals("id: 1\nevent: invalidate\ndata: ${tasks.id}\n\n", chunks[1])
            assertEquals("id: 2\nevent: invalidate\ndata: ${summary.id}\ndata: ${tasks.id}\n\n", chunks[2])
        }

    @Test
    fun `an undeclared target ends the stream`() =
        runTest {
            val failure = runCatching { stream(flowOf(other), lastEventId = null).collect() }.exceptionOrNull()
            assertInstanceOf(IllegalArgumentException::class.java, failure)
        }

    @Test
    fun `application refusal keeps its status and is observed as rejected`() =
        runTest {
            val events = mutableListOf<WogeObservationEvent>()
            val response =
                LiveAdmission().open(
                    LiveUseCase<Unit> { liveRefused(FailureCategory.FORBIDDEN, it.context.correlationId) },
                    request(),
                    null,
                    events::add,
                )
            assertEquals(
                403,
                assertInstanceOf(LiveResponse.Refused::class.java, response)
                    .failure.metadata.status.code,
            )
            assertEquals(WogeOutcome.REJECTED, events.filterIsInstance<WogeOperationFinished>().single().outcome)
        }

    @Test
    fun `application and session limits reject before streaming and release on completion`() =
        runTest {
            val admission = LiveAdmission(LiveLimits(maxSubscriptions = 2, maxSubscriptionsPerSession = 1))
            val events = mutableListOf<WogeObservationEvent>()

            suspend fun open(session: String) =
                admission.open(
                    LiveUseCase<Unit> {
                        liveSubscription(listOf(tasks), emptyFlow(), LiveSessionKey.of(session))
                    },
                    request(),
                    null,
                    events::add,
                )
            val first = assertInstanceOf(LiveResponse.Stream::class.java, open("a"))
            val sameSession = assertInstanceOf(LiveResponse.Refused::class.java, open("a"))
            assertEquals(429, sameSession.failure.metadata.status.code)
            assertInstanceOf(LiveResponse.Stream::class.java, open("b"))
            val full = assertInstanceOf(LiveResponse.Refused::class.java, open("c"))
            assertEquals(503, full.failure.metadata.status.code)
            assertEquals(
                listOf(ResourceLimit.LIVE_SESSION_SUBSCRIPTIONS, ResourceLimit.LIVE_SUBSCRIPTIONS),
                events.filterIsInstance<WogeOperationFinished>().mapNotNull { it.context.exceededLimit?.limit },
            )
            assertEquals(2, admission.openStreams)
            first.events.collect()
            assertEquals(1, admission.openStreams)
            assertInstanceOf(LiveResponse.Stream::class.java, open("a"))
        }

    @Test
    fun `subscriptions declare between one and the maximum number of targets`() {
        assertThrows(IllegalArgumentException::class.java) { liveSubscription(emptyList(), emptyFlow()) }
        val tooMany = (0..LiveLimits.MAX_TARGETS).map { target("region-$it") }
        assertThrows(IllegalArgumentException::class.java) { liveSubscription(tooMany, emptyFlow()) }
    }

    private suspend fun stream(
        invalidations: Flow<RegionTarget<*>>,
        lastEventId: String?,
        limits: LiveLimits = LiveLimits(),
    ): Flow<ByteArray> {
        val response =
            LiveAdmission(limits).open(
                LiveUseCase<Unit> { liveSubscription(listOf(tasks, summary), invalidations) },
                request(),
                lastEventId,
            )
        return assertInstanceOf(LiveResponse.Stream::class.java, response).events
    }

    private suspend fun Flow<ByteArray>.toText(): String = toList().joinToString("") { it.decodeToString() }

    private fun request(): PageRequest<Unit> =
        PageRequest(Unit, RequestContext(RequestMethod.GET, RequestTrace(RequestId.of("r"), CorrelationId.of("c"))))

    private fun target(name: String): RegionTarget<String> =
        object : PageRegion<String>(name) {
            override fun render(
                writer: HtmlWriter,
                input: String,
            ) = writer.text(input)
        }.target(page)

    private val RegionTarget<*>.id: String
        get() = target.region.value
}
