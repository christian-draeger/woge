package dev.woge.tck

import dev.woge.host.PageResult
import dev.woge.host.ResponseHeaders
import dev.woge.host.ResponseMetadata
import dev.woge.host.httpHeader
import dev.woge.host.streamingHtmlPage
import dev.woge.protocol.htmlFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.net.Socket
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/** Test-only diagnostic: four small timestamped HTML frames, never bound by production handlers. */
internal fun streamDiagnosticPage(): PageResult.Document =
    streamingHtmlPage(
        flow {
            repeat(STREAM_CHUNKS) { number ->
                emit(htmlFrame { element("p") { text("chunk=$number serverMillis=${System.currentTimeMillis()}") } })
                if (number < LAST_STREAM_CHUNK) delay(STREAM_SPACING_MILLIS)
            }
        },
        metadata =
            ResponseMetadata(
                headers =
                    ResponseHeaders.of(
                        httpHeader("Cache-Control", "no-store, no-transform"),
                        httpHeader("X-Accel-Buffering", "no"),
                    ),
            ),
    )

internal suspend fun verifyStreamingDeployment(
    server: AdapterTckServer,
    fixture: AdapterTckFixtureState,
    adapterName: String,
) {
    verifyActiveDisconnect(server, fixture)
    val url = server.origin.resolve(AdapterTckRoutes.page(AdapterTckPageScenario.STREAM_DIAGNOSTIC))
    verifyDirectTiming(server, adapterName)
    System.getenv("WOGE_STREAM_PROXY_SCRIPT")?.let { script ->
        val profile = System.getenv("WOGE_STREAM_PROXY_PROFILE") ?: "nginx"
        check(profile in setOf("nginx", "caddy"))
        val process =
            ProcessBuilder(
                "python3",
                Path.of(script).toAbsolutePath().toString(),
                url.toString(),
                "--$profile",
                "--label",
                adapterName,
            ).inheritIO()
                .start()
        try {
            check(process.waitFor(PROXY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "$profile streaming probe timed out" }
            check(process.exitValue() == 0) { "$profile streaming probe failed" }
        } finally {
            releaseProbe(process)
        }
    }
}

private fun releaseProbe(process: Process) {
    if (process.isAlive) {
        process.destroy()
        if (!process.waitFor(PROXY_CLEANUP_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
    }
}

private fun verifyDirectTiming(
    server: AdapterTckServer,
    adapterName: String,
) {
    val url = server.origin.resolve(AdapterTckRoutes.page(AdapterTckPageScenario.STREAM_DIAGNOSTIC))
    val httpClient =
        HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(STREAM_TIMEOUT_SECONDS))
            .build()
    httpClient.use { client ->
        for (encoding in listOf("identity", "gzip")) {
            val started = System.nanoTime()
            val response =
                client.send(
                    HttpRequest
                        .newBuilder(url)
                        .timeout(Duration.ofSeconds(STREAM_TIMEOUT_SECONDS))
                        .header("Accept-Encoding", encoding)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofInputStream(),
                )
            response.body().use { body ->
                check(response.statusCode() == HTTP_OK)
                check(response.headers().firstValue("X-Accel-Buffering").orElse("") == "no")
                check(
                    response
                        .headers()
                        .firstValue("Cache-Control")
                        .orElse("")
                        .contains("no-transform"),
                )
                val firstByte = body.read()
                val firstMillis = (System.nanoTime() - started) / NANOS_PER_MILLISECOND
                val rest = body.readAllBytes().toString(Charsets.UTF_8)
                val endMillis = (System.nanoTime() - started) / NANOS_PER_MILLISECOND
                check(firstByte >= 0 && rest.contains("chunk=3"))
                check(
                    endMillis - firstMillis >= MIN_STREAM_SPACING_MILLIS,
                ) { "Buffered stream: first=$firstMillis end=$endMillis" }
                val headers = response.headers()
                println(
                    "WOGE-STREAM host=$adapterName protocol=${response.version()} accept=$encoding " +
                        "encoding=${headers.firstValue("Content-Encoding").orElse("identity")} " +
                        "transfer=${headers.firstValue("Transfer-Encoding").orElse("none")} " +
                        "firstMs=$firstMillis endMs=$endMillis",
                )
            }
        }
    }
}

private suspend fun verifyActiveDisconnect(
    server: AdapterTckServer,
    fixture: AdapterTckFixtureState,
) {
    Socket(server.origin.host, server.origin.port).use { socket ->
        socket.soTimeout = SOCKET_TIMEOUT_MILLIS
        socket.setSoLinger(true, 0)
        val path = AdapterTckRoutes.deferred(AdapterTckDeferredScenario.ACTIVE_CLIENT_ABORT)
        socket.getOutputStream().write(
            "GET $path HTTP/1.1\r\nHost: ${server.origin.host}\r\nConnection: close\r\n\r\n".toByteArray(),
        )
        // Read the committed patch preamble, then send TCP RST while a request-owned child is suspended.
        val input = socket.getInputStream()
        val prefix = StringBuilder()
        while (!prefix.contains("\r\n\r\n")) prefix.append(input.read().also { check(it >= 0) }.toChar())
        check(prefix.startsWith("HTTP/1.1 200"))
        check(prefix.toString().lowercase().contains("x-accel-buffering: no"))
        check(prefix.toString().lowercase().contains("no-transform"))
        check(input.read() >= 0)
        withTimeout(5.seconds) { fixture.disconnectStarted.await() }
    }
    // Servlet notices RST on the next pulse write; Reactor/Netty may notice immediately.
    withTimeout(5.seconds) { fixture.disconnectReleased.await() }
}

private const val STREAM_CHUNKS = 4
private const val LAST_STREAM_CHUNK = STREAM_CHUNKS - 1
private const val STREAM_SPACING_MILLIS = 500L
private const val STREAM_TIMEOUT_SECONDS = 5L
private const val SOCKET_TIMEOUT_MILLIS = 5_000
private const val PROXY_TIMEOUT_SECONDS = 45L
private const val PROXY_CLEANUP_SECONDS = 12L
private const val MIN_STREAM_SPACING_MILLIS = 800
private const val NANOS_PER_MILLISECOND = 1_000_000
private const val HTTP_OK = 200
