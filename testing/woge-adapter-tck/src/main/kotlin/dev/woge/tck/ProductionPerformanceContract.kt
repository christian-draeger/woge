package dev.woge.tck

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

internal class ProductionPerformanceContract(
    private val adapter: String,
    private val server: AdapterTckServer,
    private val fixture: PerformanceRenderFixture,
) {
    fun verify() {
        val measurementStarted = System.nanoTime()
        val root = Path.of(System.getProperty("woge.budgets.root"))
        val policy = readJson(root.resolve("testing/performance/server-policy.json"))
        val request =
            HttpRequest
                .newBuilder(server.origin.resolve("/woge-tck/routes/1?note=performance"))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build()
        HttpClient.newHttpClient().use { client ->
            repeat(WARMUP_REQUESTS) { render(client, request) }
            fixture.drain()
            val started = System.nanoTime()
            val responseBytes = renderBatch(client, request)
            val seconds = (System.nanoTime() - started) / NANOS_PER_SECOND
            val allocations = fixture.drain().sorted()
            check(allocations.size == MEASURED_REQUESTS) { "Every request must render the same fixture exactly once" }
            val metrics =
                JsonObject(
                    mapOf(
                        "renderAllocatedBytes" to JsonPrimitive(allocations[allocations.size / 2]),
                        "responseBytes" to JsonPrimitive(responseBytes),
                        "requestsPerSecond" to JsonPrimitive(MEASURED_REQUESTS / seconds),
                        "measurementMillis" to
                            JsonPrimitive((System.nanoTime() - measurementStarted) / NANOS_PER_MILLISECOND),
                    ),
                )
            record(root, policy, metrics)
        }
    }

    private fun renderBatch(
        client: HttpClient,
        request: HttpRequest,
    ): Int {
        var bytes = 0
        repeat(MEASURED_REQUESTS) { bytes = render(client, request) }
        return bytes
    }

    private fun render(
        client: HttpClient,
        request: HttpRequest,
    ): Int {
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == SUCCESS_STATUS) {
            "$adapter performance fixture failed: ${response.statusCode()}"
        }
        check(response.body().size > MINIMUM_FIXTURE_BYTES) { "$adapter did not render the dashboard fixture" }
        return response.body().size
    }

    private fun record(
        root: Path,
        policy: JsonObject,
        metrics: JsonObject,
    ) {
        val baselinePath = root.resolve("testing/performance/$adapter-baseline.json")
        val report = root.resolve("adapters/woge-$adapter/build/reports/performance.json")
        Files.createDirectories(report.parent)
        Files.writeString(report, "$metrics\n")
        val allocated = metrics.getValue("renderAllocatedBytes").jsonPrimitive.long
        val ceiling = policy.getValue("renderAllocatedBytesCeiling").jsonPrimitive.long
        check(allocated in 1..ceiling) { "$adapter rendering allocation $allocated exceeds portable ceiling $ceiling" }
        if (System.getProperty("woge.budgets.update") == "true") {
            Files.writeString(baselinePath, "$metrics\n")
        }
        val baseline = readJson(baselinePath)
        val measured = baseline.getValue("renderAllocatedBytes").jsonPrimitive.long
        checkRenderAllocation(allocated, measured, policy)
        check(metrics.getValue("responseBytes") == baseline.getValue("responseBytes")) {
            "Performance fixture output changed; explicitly review its baseline"
        }
        println("[woge-performance][$adapter] $metrics")
    }
}

internal fun checkRenderAllocation(
    allocated: Long,
    baseline: Long,
    policy: JsonObject,
) {
    val ceiling = policy.getValue("renderAllocatedBytesCeiling").jsonPrimitive.long
    val tolerance = policy.getValue("allocationTolerance").jsonPrimitive.double
    check(allocated in 1..ceiling) { "Rendering allocation $allocated exceeds portable ceiling $ceiling" }
    check(allocated <= baseline * (1 + tolerance)) {
        "Rendering allocation $allocated exceeds baseline $baseline + ${tolerance * PERCENT}%"
    }
}

private fun readJson(path: Path): JsonObject = Json.parseToJsonElement(Files.readString(path)).jsonObject

private const val WARMUP_REQUESTS = 30
private const val MEASURED_REQUESTS = 50
private const val MINIMUM_FIXTURE_BYTES = 8_000
private const val NANOS_PER_SECOND = 1_000_000_000.0

private const val REQUEST_TIMEOUT_SECONDS = 10L
private const val SUCCESS_STATUS = 200
private const val PERCENT = 100

private const val NANOS_PER_MILLISECOND = 1_000_000.0
