package dev.woge.development.browser

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.woge.development.DevelopmentSessionPhase
import dev.woge.development.DevelopmentUrl
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadApplied
import dev.woge.development.ReloadLevel
import dev.woge.development.ServerReady
import dev.woge.development.orchestrator.DevelopmentOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.IOException
import java.net.HttpURLConnection.HTTP_BAD_METHOD
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Optional privileged raw build details, separate from the redacted SSE protocol. */
public fun interface DevelopmentBuildDetails {
    public fun read(): String
}

/**
 * Stable loopback SSE endpoint owned by the development session, not by the restartable application.
 * Connections receive the latest complete snapshot, including the last ready build; reconnecting
 * clients need no unbounded event log. Slow tabs cannot block the coordinator or other tabs.
 */
@ExperimentalWogeDevelopmentApi
@Suppress("TooManyFunctions")
public class DevelopmentBrowserChannel(
    scope: CoroutineScope,
    private val orchestrator: DevelopmentOrchestrator,
    applicationOrigins: Set<String>,
    port: Int = 0,
    maxTabs: Int = 16,
    private val details: DevelopmentBuildDetails? = null,
) : AutoCloseable {
    private val origins =
        applicationOrigins
            .map { origin ->
                val url = URI(DevelopmentUrl.local(origin).value)
                require(
                    url.rawPath.isNullOrEmpty() || url.rawPath == "/",
                ) { "Application origins must not contain paths" }
                "${url.scheme}://${url.rawAuthority}"
            }.toSet()
    private val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
    private val session = UUID.randomUUID().toString()
    private val tabLimit =
        maxTabs.also {
            require(it in 1..MAX_TABS) { "Max tabs must be between 1 and $MAX_TABS" }
            require(origins.isNotEmpty()) { "At least one loopback application origin is required" }
            require(port in 0..MAX_PORT) { "Invalid development port" }
        }
    private val executor =
        Executors.newFixedThreadPool(tabLimit + 2) { runnable ->
            Thread(runnable, "woge-dev-browser").apply { isDaemon = true }
        }
    private val expiry =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "woge-dev-browser-expiry").apply { isDaemon = true }
        }
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0)
    private val slots = Semaphore(tabLimit)
    private val clients = CopyOnWriteArraySet<Client>()
    private val closed = AtomicBoolean(false)
    private val monitor = Any()
    private var snapshot =
        BrowserSnapshot(
            0,
            orchestrator.state.value,
            orchestrator.state.value.lastSuccessfulBuild.takeIf {
                orchestrator.state.value.phase in
                    setOf(DevelopmentSessionPhase.READY, DevelopmentSessionPhase.BUILD_FAILED)
            },
        )
    public val baseUrl: String = "http://127.0.0.1:${server.address.port}"
    public val eventsUrl: String get() = "$baseUrl/events?token=$token"
    public val detailsUrl: String? get() = if (details != null) "$baseUrl/details?token=$token" else null

    init {
        server.executor = executor
        server.createContext("/") { exchange -> safelyHandle(exchange) }
        server.start()
    }

    private val subscription =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                orchestrator.events.collect { record ->
                    synchronized(monitor) {
                        val event = record.event
                        val rendered =
                            when (event) {
                                is ServerReady -> event.buildId
                                is ReloadApplied -> event.buildId
                                else -> snapshot.renderedBuild
                            }
                        val document =
                            when {
                                event is ServerReady -> event.buildId
                                event is ReloadApplied && event.level.satisfies(ReloadLevel.DOCUMENT_REFRESH) ->
                                    event.buildId
                                else -> snapshot.documentBuild
                            }
                        snapshot = BrowserSnapshot(record.sequence, record.state, rendered, document)
                        clients.forEach { it.offer(snapshot) }
                    }
                }
            } finally {
                stopServer()
            }
        }

    override fun close() {
        subscription.cancel()
        stopServer()
    }

    override fun toString(): String = "DevelopmentBrowserChannel($baseUrl, credentials=<redacted>)"

    private fun stopServer() {
        if (!closed.compareAndSet(false, true)) return
        clients.forEach { it.exchange.close() }
        server.stop(0)
        executor.shutdownNow()
        expiry.shutdownNow()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun safelyHandle(exchange: HttpExchange) {
        try {
            handle(exchange)
        } catch (failure: Exception) {
            System.getLogger("woge.dev.browser").log(System.Logger.Level.ERROR, "Development endpoint failed", failure)
            if (exchange.responseCode < 0) {
                respond(exchange, HTTP_INTERNAL_ERROR, "Development endpoint failed; see the development log.")
            } else {
                exchange.close()
            }
        }
    }

    private fun handle(exchange: HttpExchange) {
        val origin = exchange.requestHeaders.getFirst("Origin")
        val host = exchange.requestHeaders.getFirst("Host")
        when {
            exchange.requestMethod != "GET" -> respond(exchange, HTTP_BAD_METHOD, "GET required")
            host !in setOf("127.0.0.1:${server.address.port}", "localhost:${server.address.port}") ->
                respond(exchange, HTTP_FORBIDDEN, "Loopback host required")
            origin != null && origin !in origins ->
                respond(
                    exchange,
                    HTTP_FORBIDDEN,
                    "Application origin not authorized",
                )
            else -> {
                exchange.responseHeaders.set("Cache-Control", "no-store")
                exchange.responseHeaders.set("Referrer-Policy", "no-referrer")
                exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
                exchange.responseHeaders.set("Vary", "Origin")
                if (origin != null) exchange.responseHeaders.set("Access-Control-Allow-Origin", origin)
                route(exchange)
            }
        }
    }

    private fun route(exchange: HttpExchange) {
        when (exchange.requestURI.path) {
            "/client.js" -> asset(exchange, "client.js", "text/javascript; charset=utf-8")
            "/refresh-state.js" -> asset(exchange, "refresh-state.js", "text/javascript; charset=utf-8")
            "/state-controls.js" -> asset(exchange, "state-controls.js", "text/javascript; charset=utf-8")
            "/stylesheets.js" -> asset(exchange, "stylesheets.js", "text/javascript; charset=utf-8")
            "/overlay.css" -> asset(exchange, "overlay.css", "text/css; charset=utf-8")
            "/events", "/details" -> {
                val credential = exchange.requestURI.rawQuery.orEmpty()
                if (!MessageDigest.isEqual(credential.toByteArray(), "token=$token".toByteArray())) {
                    respond(exchange, HTTP_FORBIDDEN, "Development session credential required")
                } else if (exchange.requestURI.path == "/events") {
                    stream(exchange)
                } else {
                    buildDetails(exchange)
                }
            }
            else -> respond(exchange, HTTP_NOT_FOUND, "Not found")
        }
    }

    private fun buildDetails(exchange: HttpExchange) {
        val source = details
        if (source == null) {
            respond(exchange, HTTP_NOT_FOUND, "Build details unavailable")
            return
        }
        val raw = source.read()
        val bounded =
            if (raw.length > MAX_DETAILS_LENGTH) {
                raw.take(MAX_DETAILS_LENGTH) + "\n[Build details truncated at $MAX_DETAILS_LENGTH characters]"
            } else {
                raw
            }
        respond(exchange, HTTP_OK, bounded)
    }

    @Suppress("SwallowedException")
    private fun stream(exchange: HttpExchange) {
        if (!slots.tryAcquire()) {
            respond(exchange, HTTP_UNAVAILABLE, "Development tab limit reached")
            return
        }
        val client = Client(exchange)
        val deadline = expiry.schedule({ exchange.close() }, CONNECTION_SECONDS, TimeUnit.SECONDS)
        try {
            exchange.responseHeaders.set("Content-Type", "text/event-stream; charset=utf-8")
            exchange.responseHeaders.set("X-Accel-Buffering", "no")
            exchange.sendResponseHeaders(HTTP_OK, 0)
            synchronized(monitor) {
                clients.add(client)
                client.offer(snapshot)
            }
            exchange.responseBody.bufferedWriter(Charsets.UTF_8).use { output ->
                writeSnapshots(client, output)
            }
        } catch (disconnected: IOException) {
            // Closing a tab or expiring a connection is normal; EventSource reconnects natively.
        } catch (stopped: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            deadline.cancel(false)
            clients.remove(client)
            exchange.close()
            slots.release()
        }
    }

    private fun writeSnapshots(
        client: Client,
        output: BufferedWriter,
    ) {
        while (!Thread.currentThread().isInterrupted) {
            val next = client.queue.poll(HEARTBEAT_SECONDS, TimeUnit.SECONDS)
            if (next == null) {
                output.write(": keepalive\n\n")
            } else {
                output.write("id: $session:${next.sequence}\nevent: snapshot\ndata: ${next.json(session)}\n\n")
            }
            output.flush()
        }
    }

    private fun asset(
        exchange: HttpExchange,
        name: String,
        type: String,
    ) {
        val content = checkNotNull(javaClass.getResource("/dev/woge/development/browser/$name")).readText()
        respond(exchange, HTTP_OK, content, type)
    }

    private fun respond(
        exchange: HttpExchange,
        code: Int,
        text: String,
        type: String = "text/plain; charset=utf-8",
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", type)
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private class Client(
        val exchange: HttpExchange,
    ) {
        val queue = LinkedBlockingQueue<BrowserSnapshot>(1)

        fun offer(snapshot: BrowserSnapshot) {
            queue.poll()
            queue.offer(snapshot)
        }
    }

    private companion object {
        const val MAX_PORT = 65_535
        const val MAX_TABS = 64
        const val HEARTBEAT_SECONDS = 1L
        const val CONNECTION_SECONDS = 30L
        const val MAX_DETAILS_LENGTH = 262_144
    }
}
