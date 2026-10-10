package dev.woge.tck

import dev.woge.host.FailureCategory
import dev.woge.host.LiveLimits
import dev.woge.host.LiveSessionKey
import dev.woge.host.LiveUseCase
import dev.woge.host.PageIdentity
import dev.woge.host.PageRegion
import dev.woge.host.PageRoute
import dev.woge.host.RegionTarget
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.RequestMethod
import dev.woge.host.RouteParameters
import dev.woge.host.liveRefused
import dev.woge.host.liveSubscription
import dev.woge.html.ApplicationUrl
import dev.woge.html.HtmlWriter
import dev.woge.protocol.PageEpoch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Typed input of the live TCK route: a session key and an optional authorization denial. */
public data class AdapterTckLiveInput(
    val session: String,
    val deny: Boolean = false,
)

/** The live route every adapter binds with `handlers(liveLimits = application.liveLimits).live(...)`. */
public object AdapterTckLiveRoute : PageRoute<AdapterTckLiveInput>("/woge-tck/live/{session}") {
    override fun url(input: AdapterTckLiveInput): ApplicationUrl =
        buildUrl(mapOf("session" to input.session), listOf("deny" to if (input.deny) "true" else null))

    override fun decode(parameters: RouteParameters): AdapterTckLiveInput =
        AdapterTckLiveInput(
            session = pathValue(parameters, "session") { it.takeIf(String::isNotBlank) },
            deny = queryValue(parameters, "deny") { it.toBooleanStrictOrNull() } ?: false,
        )
}

/** Small limits so the contract can reach every admission outcome quickly. */
internal val TCK_LIVE_LIMITS: LiveLimits =
    LiveLimits(
        maxSubscriptions = 3,
        maxSubscriptionsPerSession = 2,
        heartbeat = 200.milliseconds,
        maxLifetime = 30.seconds,
        retry = 1500.milliseconds,
    )

/** Live fixture: two declared regions, an invalidation source and a count of active subscriptions. */
internal class AdapterTckLiveFixture {
    private val page = PageIdentity(PageEpoch.of("tck-live-epoch"), RenderIdentitySecret.random())
    val tasks: RegionTarget<String> = textRegion("liveTasks").target(page)
    val summary: RegionTarget<String> = textRegion("liveSummary").target(page)
    val subscribers: AtomicInteger = AtomicInteger()
    val invalidations: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 64)

    val useCase: LiveUseCase<AdapterTckLiveInput> =
        LiveUseCase { request ->
            if (request.input.deny) {
                liveRefused(FailureCategory.FORBIDDEN, request.context.correlationId)
            } else {
                liveSubscription(
                    targets = listOf(tasks, summary),
                    invalidations =
                        invalidations
                            .onStart { subscribers.incrementAndGet() }
                            .onCompletion { subscribers.decrementAndGet() }
                            .mapNotNull { name -> listOf(tasks, summary).firstOrNull { it.id == name } },
                    session = LiveSessionKey.of(request.input.session),
                )
            }
        }

    private fun textRegion(name: String): PageRegion<String> =
        object : PageRegion<String>(name) {
            override fun render(
                writer: HtmlWriter,
                input: String,
            ) = writer.text(input)
        }
}

internal val RegionTarget<*>.id: String
    get() = target.region.value

/** Verifies authorization, SSE framing, coalescing, resync, admission limits and disconnect cleanup. */
@Suppress("LongMethod")
internal suspend fun AdapterTckHttpClient.verifyLive(
    fixture: AdapterTckLiveFixture,
    expect: (Boolean, String, String) -> Unit,
) {
    val contract = "live-sse"
    val denied = open(RequestMethod.GET, AdapterTckLiveRoute.url(AdapterTckLiveInput("denied", deny = true)).value)
    denied.body().close()
    expect(denied.statusCode() == FORBIDDEN, contract, "refused subscription must keep the application status")

    val first = openLive("a")
    expect(first.statusCode() == OK, contract, "expected HTTP 200 for an admitted stream")
    expect(
        first.header("content-type")?.lowercase()?.startsWith("text/event-stream") == true,
        contract,
        "stream must use text/event-stream",
    )
    expect(first.header("cache-control") == "no-store", contract, "stream must not be cached")
    expect(first.header("x-accel-buffering") == "no", contract, "stream must disable proxy buffering")
    val stream = first.body()
    expect(stream.readEvent() == "retry: 1500", contract, "stream must start with the retry hint")
    awaitSubscribers(fixture, 1)
    fixture.invalidations.emit(fixture.tasks.id)
    fixture.invalidations.emit(fixture.tasks.id)
    fixture.invalidations.emit(fixture.summary.id)
    val seen = mutableSetOf<String>()
    var lastId = 0L
    while (seen.size < 2) {
        val event = stream.readEvent()
        if (event.startsWith(":")) continue
        val lines = event.lines()
        val id = lines.first().removePrefix("id: ").toLongOrNull() ?: -1
        expect(id > lastId, contract, "event ids must increase")
        lastId = id
        expect(lines.getOrNull(1) == "event: invalidate", contract, "expected an invalidate event")
        seen += lines.drop(2).map { it.removePrefix("data: ") }
    }
    expect(seen == setOf(fixture.tasks.id, fixture.summary.id), contract, "invalidated targets changed")
    expect(stream.readEvent() == ": heartbeat", contract, "idle stream must send a heartbeat")

    val resumed = openLive("b", lastEventId = "41")
    val resumedStream = resumed.body()
    resumedStream.readEvent()
    expect(
        resumedStream.readEvent() == "id: 42\nevent: resync\ndata: ${fixture.tasks.id}\ndata: ${fixture.summary.id}",
        contract,
        "reconnect must resync every declared target",
    )

    val sameSession = openLive("a")
    val sessionLimited = openLive("a")
    sessionLimited.body().close()
    expect(sessionLimited.statusCode() == TOO_MANY_REQUESTS, contract, "per-session limit must return 429")
    val applicationLimited = openLive("c")
    applicationLimited.body().close()
    expect(applicationLimited.statusCode() == UNAVAILABLE, contract, "application limit must return 503")

    listOf(stream, resumedStream, sameSession.body()).forEach(InputStream::close)
    awaitSubscribers(fixture, 0)
    var reopened = openLive("c")
    repeat(ADMISSION_RELEASE_ATTEMPTS) {
        if (reopened.statusCode() == OK) return@repeat
        reopened.body().close()
        delay(ADMISSION_RELEASE_POLL)
        reopened = openLive("c")
    }
    expect(reopened.statusCode() == OK, contract, "closed streams must release their admission slots")
    reopened.body().close()
    awaitSubscribers(fixture, 0)
}

private fun AdapterTckHttpClient.openLive(
    session: String,
    lastEventId: String? = null,
): HttpResponse<InputStream> =
    open(
        RequestMethod.GET,
        AdapterTckLiveRoute.url(AdapterTckLiveInput(session)).value,
        listOfNotNull(lastEventId?.let { "Last-Event-ID" to it }).toMap(),
    )

/** Disconnects are noticed at the next heartbeat write, so cleanup takes a few heartbeats at most. */
private suspend fun awaitSubscribers(
    fixture: AdapterTckLiveFixture,
    count: Int,
) {
    val deadline = System.nanoTime() + LIVE_WAIT.inWholeNanoseconds
    while (fixture.subscribers.get() != count) {
        check(
            System.nanoTime() < deadline,
        ) { "Live subscriptions did not reach $count, saw ${fixture.subscribers.get()}" }
        delay(ADMISSION_RELEASE_POLL)
    }
}

/** Reads one SSE block without the blank separator line. A stuck stream is closed and fails the contract. */
private suspend fun InputStream.readEvent(): String =
    coroutineScope {
        val watchdog =
            launch {
                delay(LIVE_WAIT)
                close()
            }
        try {
            withContext(Dispatchers.IO) { readBlock() }
        } finally {
            watchdog.cancel()
        }
    }

private fun InputStream.readBlock(): String {
    val output = ByteArrayOutputStream()
    var previous = -1
    while (true) {
        val next = read()
        check(next >= 0) { "Live stream ended before a complete event" }
        if (next == '\n'.code && previous == '\n'.code) break
        output.write(next)
        check(output.size() <= MAX_EVENT_BYTES) { "Live event exceeded the bounded TCK size" }
        previous = next
    }
    return output.toString(Charsets.UTF_8).trimEnd('\n')
}

private const val OK: Int = 200
private const val FORBIDDEN: Int = 403
private const val TOO_MANY_REQUESTS: Int = 429
private const val UNAVAILABLE: Int = 503
private const val MAX_EVENT_BYTES: Int = 16 * 1024
private const val ADMISSION_RELEASE_ATTEMPTS: Int = 50
private val ADMISSION_RELEASE_POLL = 50.milliseconds
private val LIVE_WAIT = 5.seconds
