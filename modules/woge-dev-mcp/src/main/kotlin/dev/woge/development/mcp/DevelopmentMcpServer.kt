package dev.woge.development.mcp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.WogeDevelopmentCapabilities
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import java.util.concurrent.Executors

/**
 * Experimental MCP endpoint for coding agents (ADR 0075), using the Streamable HTTP transport with
 * plain JSON responses.
 *
 * It listens on `127.0.0.1` only, requires the bearer [token], rejects foreign `Host` and `Origin`
 * headers (DNS rebinding, browser pages) and exposes only [WogeDevelopmentCapabilities]: build state,
 * diagnostics, the application manifest and local URLs. It never sees requests, sessions or user data
 * of the application. The protocol has no stability guarantee.
 */
@ExperimentalWogeDevelopmentApi
public class DevelopmentMcpServer(
    capabilities: WogeDevelopmentCapabilities,
    port: Int = 0,
    serverVersion: String = "experimental",
) : AutoCloseable {
    /** The secret every request must send as `Authorization: Bearer <token>`. */
    public val token: String = HexFormat.of().formatHex(ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes))

    private val protocol = DevelopmentMcpProtocol(capabilities, serverVersion)
    private val executor =
        Executors.newFixedThreadPool(THREADS) { runnable ->
            Thread(runnable, "woge-dev-mcp").apply { isDaemon = true }
        }
    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0).apply {
            executor = this@DevelopmentMcpServer.executor
            createContext("/") { exchange -> safely(exchange) }
            start()
        }

    public val url: String = "http://127.0.0.1:${server.address.port}$PATH"

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    override fun toString(): String = "DevelopmentMcpServer($url, token=<redacted>)"

    @Suppress("TooGenericExceptionCaught")
    private fun safely(exchange: HttpExchange) {
        try {
            handle(exchange)
        } catch (failure: Exception) {
            System.getLogger("woge.dev.mcp").log(System.Logger.Level.ERROR, "MCP request failed", failure)
            if (exchange.responseCode < 0) text(exchange, INTERNAL_ERROR, "MCP request failed; see the wogeDev log.")
        } finally {
            exchange.close()
        }
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun handle(exchange: HttpExchange) {
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
        val port = server.address.port
        val host = exchange.requestHeaders.getFirst("Host")
        val origin = exchange.requestHeaders.getFirst("Origin")
        val rejection =
            when {
                host !in setOf("127.0.0.1:$port", "localhost:$port") -> FORBIDDEN to "Loopback host required"
                origin != null && !isLoopback(origin) -> FORBIDDEN to "Browser origins are not allowed"
                !authorized(exchange.requestHeaders.getFirst("Authorization")) ->
                    UNAUTHORIZED to "Bearer token required; wogeDev --mcp writes it to build/woge-dev/mcp.json"
                exchange.requestURI.path != PATH -> NOT_FOUND to "Not found"
                exchange.requestMethod != "POST" -> METHOD_NOT_ALLOWED to "POST required"
                exchange.requestHeaders
                    .getFirst("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase() != "application/json" ->
                    UNSUPPORTED_MEDIA_TYPE to "Content-Type application/json required"
                else -> null
            }
        if (rejection != null) {
            if (rejection.first == METHOD_NOT_ALLOWED) exchange.responseHeaders.set("Allow", "POST")
            return text(exchange, rejection.first, rejection.second)
        }
        val body = exchange.requestBody.readNBytes(MAX_BODY_BYTES + 1)
        if (body.size > MAX_BODY_BYTES) return text(exchange, PAYLOAD_TOO_LARGE, "Request too large")
        val message =
            try {
                Json.parseToJsonElement(body.decodeToString())
            } catch (_: SerializationException) {
                return json(exchange, OK, rpcError(PARSE_ERROR, "Invalid JSON"))
            }
        if (message is JsonArray) {
            return json(
                exchange,
                OK,
                rpcError(INVALID_REQUEST, "Batch requests are not supported"),
            )
        }
        val response = runBlocking { protocol.handle(message) }
        if (response == null) {
            exchange.sendResponseHeaders(ACCEPTED, NO_BODY)
        } else {
            json(exchange, OK, response)
        }
    }

    private fun authorized(header: String?): Boolean {
        val presented = header?.removePrefix("Bearer ")?.takeIf { header.startsWith("Bearer ") } ?: return false
        return MessageDigest.isEqual(presented.toByteArray(), token.toByteArray())
    }

    private fun isLoopback(origin: String): Boolean =
        runCatching { URI(origin).host?.lowercase() }.getOrNull() in setOf("127.0.0.1", "localhost", "[::1]")

    private fun json(
        exchange: HttpExchange,
        status: Int,
        value: JsonElement,
    ) {
        val bytes = value.toString().toByteArray()
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun text(
        exchange: HttpExchange,
        status: Int,
        message: String,
    ) {
        val bytes = message.toByteArray()
        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun rpcError(
        code: Int,
        message: String,
    ) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", JsonNull)
        put(
            "error",
            buildJsonObject {
                put("code", code)
                put("message", message)
            },
        )
    }

    private companion object {
        const val PATH = "/mcp"
        const val THREADS = 4
        const val TOKEN_BYTES = 32
        const val MAX_BODY_BYTES = 1 shl 20
        const val NO_BODY = -1L
        const val OK = 200
        const val ACCEPTED = 202
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val METHOD_NOT_ALLOWED = 405
        const val PAYLOAD_TOO_LARGE = 413
        const val UNSUPPORTED_MEDIA_TYPE = 415
        const val INTERNAL_ERROR = 500
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
    }
}
