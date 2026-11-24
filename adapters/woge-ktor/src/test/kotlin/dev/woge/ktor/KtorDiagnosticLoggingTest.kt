package dev.woge.ktor

import dev.woge.host.PageUseCase
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.spi.SLF4JServiceProvider
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.ServiceLoader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class KtorDiagnosticLoggingTest {
    @Test
    fun `the consumer provider receives one warning for an unset identity secret`() {
        val providers = ServiceLoader.load(SLF4JServiceProvider::class.java).map { it.javaClass.name }
        assertEquals(listOf("org.slf4j.jul.JULServiceProvider"), providers)
        capture(WogeKtorHandlers::class.java.name) { records ->
            val configured = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
            WogeKtorHandlers(MapApplicationConfig("woge.render-identity.secret" to configured))
            assertEquals(0, records.size)
            WogeKtorHandlers(MapApplicationConfig())
            val warning = records.single()
            assertEquals(Level.WARNING, warning.level)
            assertEquals(
                "woge.render-identity.secret is unset; using a process-local secret. " +
                    "Configure a shared secret before running multiple instances or restarting enhanced pages.",
                warning.message,
            )
        }
    }

    @Test
    fun `the consumer provider receives only the correlation ID for a response failure`() {
        val correlationId = AtomicReference<String>()
        val page =
            WogeKtorHandlers().page(
                PageUseCase<Unit> { request ->
                    correlationId.set(request.context.correlationId.value)
                    yield()
                    error("private application failure")
                },
                KtorPageInput { },
            )
        capture("dev.woge.ktor.KtorResponses") { records ->
            val server =
                embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                    routing { get("/") { page.handle(call) } }
                }.start(wait = false)
            try {
                val connectors = runBlocking { server.engine.resolvedConnectors() }
                val port = connectors.single().port
                val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/")).build()
                val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
                assertEquals(500, response.statusCode())
                assertEquals("", response.body())
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow())
                val error = records.single()
                assertEquals(Level.SEVERE, error.level)
                assertEquals(
                    "Woge request failed before response streaming (correlationId=${correlationId.get()})",
                    error.message,
                )
                assertNull(error.thrown)
            } finally {
                server.stop(1_000, 1_000)
            }
        }
    }

    private fun capture(
        name: String,
        block: (List<LogRecord>) -> Unit,
    ) {
        val logger = Logger.getLogger(name)
        val previousLevel = logger.level
        val records = CopyOnWriteArrayList<LogRecord>()
        val handler =
            object : Handler() {
                override fun publish(record: LogRecord) {
                    records.add(record)
                }

                override fun close() = Unit

                override fun flush() = Unit
            }
        logger.level = Level.ALL
        logger.addHandler(handler)
        try {
            block(records)
        } finally {
            logger.removeHandler(handler)
            logger.level = previousLevel
        }
    }
}
