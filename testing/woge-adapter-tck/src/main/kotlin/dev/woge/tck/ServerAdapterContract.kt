package dev.woge.tck

import dev.woge.host.RequestMethod
import dev.woge.host.ResponseStatus
import dev.woge.host.WogeObservationEvent
import dev.woge.host.WogeOperation
import dev.woge.host.WogeOperationFinished
import dev.woge.host.WogeOperationStarted
import dev.woge.host.WogeOutcome
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Executes the framework-neutral server-adapter contract over a real HTTP connection. */
public class ServerAdapterContract(
    private val factory: AdapterTckHarnessFactory,
) {
    /** Runs the core page/deferred suites followed by any additive capability suites. */
    public fun verify(extensions: Iterable<AdapterTckExtension> = emptyList()) {
        val application = AdapterTckApplication()
        val server = start(application)
        server.use {
            validateHarness(server)
            kotlinx.coroutines.runBlocking {
                val verification = AdapterTckVerification(factory.adapterName, server, application.fixtureState())
                verification.verifyCore()
                extensions.forEach { extension ->
                    contract("extension:${extension.name}") { extension.verify(server) }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun start(application: AdapterTckApplication): AdapterTckServer =
        try {
            factory.start(application)
        } catch (cause: Exception) {
            throw violation(AdapterTckFailureOwner.FIXTURE, "harness-start", "adapter harness did not start", cause)
        }

    private fun validateHarness(server: AdapterTckServer) {
        if (factory.adapterName.isBlank()) {
            throw violation(AdapterTckFailureOwner.FIXTURE, "harness-name", "adapter name must not be blank")
        }
        if (!server.origin.isAbsolute || server.origin.scheme !in setOf("http", "https")) {
            throw violation(AdapterTckFailureOwner.FIXTURE, "harness-origin", "server origin must be absolute HTTP")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun contract(
        name: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (failure: AdapterTckViolation) {
            throw failure
        } catch (cause: Exception) {
            throw violation(AdapterTckFailureOwner.ADAPTER, name, "adapter request failed", cause)
        }
    }

    private fun violation(
        owner: AdapterTckFailureOwner,
        contract: String,
        detail: String,
        cause: Throwable? = null,
    ): AdapterTckViolation = AdapterTckViolation(owner, factory.adapterName, contract, detail, cause)
}

@Suppress("TooManyFunctions")
private class AdapterTckVerification(
    private val adapterName: String,
    private val server: AdapterTckServer,
    private val fixture: AdapterTckFixtureState,
) {
    private val client: AdapterTckHttpClient = AdapterTckHttpClient(server.origin)

    suspend fun verifyCore() {
        verifyDocumentGetAndHead()
        verifyRedirectAndFailures()
        verifyDeferredCompletionOrder()
        verifyDeferredHeadersBeforeRegions()
        if (AdapterTckCapability.CLIENT_ABORT_CANCELLATION in server.capabilities) {
            verifyClientAbortCancellation()
        }
        verifyTypedRoute()
        verifySemanticObservations()
    }

    private suspend fun verifyDocumentGetAndHead() {
        runContract("page-get-stream") {
            val response =
                client.open(
                    RequestMethod.GET,
                    AdapterTckRoutes.page(AdapterTckPageScenario.DOCUMENT),
                    mapOf(
                        "Accept-Language" to "de-DE,de;q=0.8",
                        "Authorization" to "Bearer tck-secret",
                        "Cookie" to "tck-theme=dark",
                        "X-Woge-Tck-Request" to "visible",
                    ),
                )
            response.body().use { body ->
                expectDocumentMetadata("page-get-stream", response)
                val shell = body.readThrough("</main>").toString(StandardCharsets.UTF_8)
                expect(shell == "<main>TCK shell</main>", "page-get-stream", "first flush group was not the shell")
                expect(!fixture.documentTail.isCompleted, "page-get-stream", "document tail completed before release")
                fixture.documentTail.complete(Unit)
                expect(
                    body.readAllBytes().toString(StandardCharsets.UTF_8) == "<footer>TCK document tail</footer>",
                    "page-get-stream",
                    "second flush group was not the document tail",
                )
            }

            val context = fixture.context(RequestMethod.GET)
            expect(context != null, "request-context", "GET request context was not supplied")
            expect(
                context?.language?.value == "de-de",
                "request-context",
                "accepted language was not normalized",
            )
            expect(
                context?.header("x-woge-tck-request") == "visible",
                "request-context",
                "safe header was not copied",
            )
            expect(context?.header("authorization") == null, "request-context", "authorization header leaked")
            expect(context?.header("cookie") == null, "request-context", "raw cookie header leaked")
            expect(context?.cookie("tck-theme") == "dark", "request-context", "parsed cookie was not supplied")
        }

        runContract("page-head") {
            val response = client.bytes(RequestMethod.HEAD, AdapterTckRoutes.page(AdapterTckPageScenario.DOCUMENT))
            expectDocumentMetadata("page-head", response)
            expect(response.body().isEmpty(), "page-head", "HEAD response exposed document bytes")
            expect(fixture.context(RequestMethod.HEAD) != null, "page-head", "HEAD method was not mapped")
        }
    }

    private suspend fun verifyRedirectAndFailures() {
        runContract("page-redirect") {
            val response = client.text(RequestMethod.GET, AdapterTckRoutes.page(AdapterTckPageScenario.REDIRECT))
            expect(response.statusCode() == ResponseStatus.SEE_OTHER.code, "page-redirect", "expected HTTP 303")
            expect(response.header("location") == "/woge-tck/redirect-target", "page-redirect", "location changed")
            expect(response.body().isEmpty(), "page-redirect", "redirect response was not bodyless")
        }

        runContract("page-controlled-failure") {
            val response =
                client.text(RequestMethod.GET, AdapterTckRoutes.page(AdapterTckPageScenario.CONTROLLED_FAILURE))
            expect(
                response.statusCode() == ResponseStatus.NOT_FOUND.code,
                "page-controlled-failure",
                "expected HTTP 404",
            )
            expect(response.body().isEmpty(), "page-controlled-failure", "controlled failure was not bodyless")
            expect(response.header("content-type") == null, "page-controlled-failure", "bodyless failure has a type")
        }

        runContract("page-pre-stream-failure") {
            val response =
                client.text(
                    RequestMethod.GET,
                    AdapterTckRoutes.page(AdapterTckPageScenario.PRE_STREAM_FAILURE),
                )
            expect(
                response.statusCode() == ResponseStatus.INTERNAL_SERVER_ERROR.code,
                "page-pre-stream-failure",
                "expected safe HTTP 500",
            )
            expect(
                !response.body().contains(PRE_STREAM_PRIVATE_DETAIL),
                "page-pre-stream-failure",
                "private exception detail reached the client",
            )
        }
    }

    private suspend fun verifyDeferredCompletionOrder() {
        runContract("deferred-completion-order") {
            val response =
                client.open(
                    RequestMethod.GET,
                    AdapterTckRoutes.deferred(AdapterTckDeferredScenario.COMPLETION_ORDER),
                )
            response.body().use { body ->
                expect(
                    response.statusCode() == ResponseStatus.OK.code,
                    "deferred-completion-order",
                    "expected HTTP 200",
                )
                expect(
                    response.header("content-type")?.startsWith("application/vnd.woge.patch-stream") == true,
                    "deferred-completion-order",
                    "patch media type changed",
                )
                expect(
                    response.header("cache-control") == "no-store",
                    "deferred-completion-order",
                    "unsafe cache policy",
                )

                val prefix = body.readThrough("Fast region")
                expect(
                    !prefix.toString(StandardCharsets.UTF_8).contains("Slow region"),
                    "deferred-completion-order",
                    "slow declaration blocked fast result",
                )
                expect(
                    !fixture.slowRegion.isCompleted,
                    "deferred-completion-order",
                    "slow fixture completed before release",
                )
                fixture.slowRegion.complete(patch("Slow region"))
                val stream = prefix + body.readAllBytes()
                val events = PatchStreamV1.decoder().let { decoder -> decoder.feed(stream).also { decoder.finish() } }
                expect(
                    events.filterIsInstance<PatchStreamEvent.PatchFrame>().map { it.patch.target.region.value } ==
                        listOf("fast", "slow"),
                    "deferred-completion-order",
                    "patches did not preserve completion order",
                )
                expect(
                    events.lastOrNull() == PatchStreamEvent.Complete(EXPECTED_PATCH_COUNT),
                    "deferred-completion-order",
                    "stream incomplete",
                )
            }
        }
    }

    private suspend fun verifyDeferredHeadersBeforeRegions() {
        runContract("deferred-headers-before-regions") {
            // HttpClient.send returns once headers arrive; a host that waits for a region would time out here.
            val response =
                client.open(
                    RequestMethod.GET,
                    AdapterTckRoutes.deferred(AdapterTckDeferredScenario.HEADERS_BEFORE_REGIONS),
                )
            response.body().use { body ->
                expect(
                    response.statusCode() == ResponseStatus.OK.code,
                    "deferred-headers-before-regions",
                    "expected HTTP 200",
                )
                expect(
                    response.header("content-type")?.startsWith("application/vnd.woge.patch-stream") == true,
                    "deferred-headers-before-regions",
                    "patch media type changed",
                )
                val preamble = body.readNBytes(PREAMBLE_PROBE_BYTES)
                expect(
                    !fixture.gatedRegions.isCompleted,
                    "deferred-headers-before-regions",
                    "headers or preamble waited for region work",
                )
                fixture.gatedRegions.complete(Unit)
                val events =
                    PatchStreamV1.decoder().let { decoder ->
                        decoder.feed(preamble + body.readAllBytes()).also { decoder.finish() }
                    }
                expect(
                    events.lastOrNull() == PatchStreamEvent.Complete(EXPECTED_PATCH_COUNT),
                    "deferred-headers-before-regions",
                    "stream incomplete",
                )
            }
        }
    }

    private suspend fun verifyClientAbortCancellation() {
        runContract("deferred-client-abort") {
            val response =
                client.open(
                    RequestMethod.GET,
                    AdapterTckRoutes.deferred(AdapterTckDeferredScenario.CLIENT_ABORT),
                )
            response.body().use { body -> body.readThrough("Ready region") }
            withTimeout(CLIENT_ABORT_TIMEOUT) { fixture.cancelledRegion.await() }
        }
    }

    private suspend fun verifyTypedRoute() {
        runContract("typed-route") {
            val url = AdapterTckRoute.url(AdapterTckRouteInput(item = 7, note = "a & b", count = 3)).value
            val response = client.text(RequestMethod.GET, url)
            expect(response.statusCode() == ResponseStatus.OK.code, "typed-route", "expected HTTP 200 for $url")
            expect(
                response.body() == "<p>item=7 note=a &amp; b count=3</p>",
                "typed-route",
                "route values were not decoded",
            )
        }
        runContract("typed-route-invalid-path") {
            val response = client.text(RequestMethod.GET, "/woge-tck/routes/seven")
            expect(response.statusCode() == ResponseStatus.NOT_FOUND.code, "typed-route-invalid-path", "expected 404")
            expect(response.body().isEmpty(), "typed-route-invalid-path", "failure was not bodyless")
        }
        runContract("typed-route-invalid-query") {
            val response = client.text(RequestMethod.GET, "/woge-tck/routes/7?count=many")
            expect(
                response.statusCode() == ResponseStatus.BAD_REQUEST.code,
                "typed-route-invalid-query",
                "expected 400",
            )
            expect(response.body().isEmpty(), "typed-route-invalid-query", "failure was not bodyless")
        }
    }

    private suspend fun verifySemanticObservations() {
        runContract("semantic-observations") {
            val events = settledObservations()
            expect(events.isNotEmpty(), "semantic-observations", "adapter emitted no semantic events")
            expectPairedLifecycle(events)
            expectOutcome(events, WogeOperation.PAGE_REQUEST, WogeOutcome.REJECTED)
            expectOutcome(events, WogeOperation.PAGE_REQUEST, WogeOutcome.FAILED)
            expectOutcome(events, WogeOperation.SHELL_RENDER, WogeOutcome.SUCCEEDED)
            expectOutcome(events, WogeOperation.DEFERRED_REGION, WogeOutcome.SUCCEEDED)
            expectOutcome(events, WogeOperation.PATCH_ENCODE, WogeOutcome.SUCCEEDED)
            expect(
                events.all { it.context.requestTrace?.correlationId != null },
                "semantic-observations",
                "server event is missing request correlation",
            )
            expect(
                !events.toString().contains(PRE_STREAM_PRIVATE_DETAIL),
                "semantic-observations",
                "private exception detail entered structured observations",
            )
        }
    }

    // A reactive host may finish an operation just after the client has read the response.
    private suspend fun settledObservations(): List<WogeObservationEvent> {
        var events = fixture.observations()
        val deadline = System.nanoTime() + OBSERVATION_SETTLE_TIMEOUT.inWholeNanoseconds
        while (openOperations(events) > 0 && System.nanoTime() < deadline) {
            delay(OBSERVATION_SETTLE_POLL)
            events = fixture.observations()
        }
        return events
    }

    private fun openOperations(events: List<WogeObservationEvent>): Int =
        events.count { it is WogeOperationStarted } - events.count { it is WogeOperationFinished }

    private fun expectPairedLifecycle(events: List<WogeObservationEvent>) {
        val open = mutableMapOf<Long, WogeOperationStarted>()
        events.forEach { event ->
            when (event) {
                is WogeOperationStarted -> {
                    expect(
                        open.put(event.observationId.value, event) == null,
                        "semantic-observations",
                        "duplicate observation ID ${event.observationId.value}",
                    )
                }
                is WogeOperationFinished -> {
                    val started = open.remove(event.observationId.value)
                    expect(
                        started?.operation == event.operation && started.context == event.context,
                        "semantic-observations",
                        "${event.operation.semanticName} finished without a matching start",
                    )
                }
            }
        }
        expect(open.isEmpty(), "semantic-observations", "semantic operation did not emit a terminal event")
    }

    private fun expectOutcome(
        events: List<WogeObservationEvent>,
        operation: WogeOperation,
        outcome: WogeOutcome,
    ) {
        expect(
            events.filterIsInstance<WogeOperationFinished>().any {
                it.operation == operation && it.outcome == outcome
            },
            "semantic-observations",
            "missing ${operation.semanticName}/${outcome.semanticName} outcome",
        )
    }

    private fun expectDocumentMetadata(
        contract: String,
        response: HttpResponse<*>,
    ) {
        expect(response.statusCode() == TCK_DOCUMENT_STATUS_CODE, contract, "expected HTTP 202")
        expect(response.header(TCK_RESPONSE_HEADER) == "mapped", contract, "response header changed")
        val contentType = response.header("content-type")?.lowercase().orEmpty()
        expect(contentType.startsWith("text/html"), contract, "HTML media type changed")
        expect(contentType.contains("charset=utf-8"), contract, "UTF-8 charset missing")
        expect(
            response.header("set-cookie")?.contains("woge-tck=safe") == true,
            contract,
            "response cookie missing",
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runContract(
        contract: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (failure: AdapterTckViolation) {
            throw failure
        } catch (cause: Exception) {
            throw violation(contract, "adapter request failed", cause)
        }
    }

    private fun expect(
        condition: Boolean,
        contract: String,
        detail: String,
    ) {
        if (!condition) throw violation(contract, detail)
    }

    private fun violation(
        contract: String,
        detail: String,
        cause: Throwable? = null,
    ): AdapterTckViolation = AdapterTckViolation(AdapterTckFailureOwner.ADAPTER, adapterName, contract, detail, cause)
}

private class AdapterTckHttpClient(
    origin: URI,
) {
    private val base: String = origin.toString().trimEnd('/')
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build()

    fun open(
        method: RequestMethod,
        path: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<InputStream> = send(method, path, headers, HttpResponse.BodyHandlers.ofInputStream())

    fun text(
        method: RequestMethod,
        path: String,
    ): HttpResponse<String> = send(method, path, emptyMap(), HttpResponse.BodyHandlers.ofString())

    fun bytes(
        method: RequestMethod,
        path: String,
    ): HttpResponse<ByteArray> = send(method, path, emptyMap(), HttpResponse.BodyHandlers.ofByteArray())

    private fun <Body> send(
        method: RequestMethod,
        path: String,
        headers: Map<String, String>,
        handler: HttpResponse.BodyHandler<Body>,
    ): HttpResponse<Body> {
        val request =
            HttpRequest
                .newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .method(method.value, HttpRequest.BodyPublishers.noBody())
                .apply { headers.forEach(::header) }
                .build()
        return client.send(request, handler)
    }
}

private fun HttpResponse<*>.header(name: String): String? = headers().firstValue(name).orElse(null)

private fun InputStream.readThrough(marker: String): ByteArray {
    val markerBytes = marker.toByteArray(StandardCharsets.UTF_8)
    val output = ByteArrayOutputStream()
    while (!output.endsWith(markerBytes)) {
        val next = read()
        check(next >= 0) { "Response ended before the bounded TCK marker" }
        output.write(next)
        check(output.size() <= MAX_STREAM_PREFIX_BYTES) { "TCK marker exceeded the bounded response prefix" }
    }
    return output.toByteArray()
}

private fun ByteArrayOutputStream.endsWith(suffix: ByteArray): Boolean {
    val bytes = toByteArray()
    return bytes.size >= suffix.size && bytes.copyOfRange(bytes.size - suffix.size, bytes.size).contentEquals(suffix)
}

private fun patch(text: String): dev.woge.protocol.PatchHtml =
    dev.woge.protocol.patchHtml { element("p") { text(text) } }

private const val MAX_STREAM_PREFIX_BYTES: Int = 64 * 1024
private const val EXPECTED_PATCH_COUNT: Int = 2
private const val PREAMBLE_PROBE_BYTES: Int = 1
private const val CONNECT_TIMEOUT_SECONDS: Long = 5
private const val REQUEST_TIMEOUT_SECONDS: Long = 10
private val CLIENT_ABORT_TIMEOUT: kotlin.time.Duration = 5.seconds
private val OBSERVATION_SETTLE_TIMEOUT: kotlin.time.Duration = 5.seconds
private val OBSERVATION_SETTLE_POLL: kotlin.time.Duration = 20.milliseconds
