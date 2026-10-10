package dev.woge.development.mcp

import dev.woge.development.ExperimentalWogeDevelopmentApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@OptIn(ExperimentalWogeDevelopmentApi::class)
class DevelopmentMcpServerTest {
    private val server = DevelopmentMcpServer(FakeCapabilities())
    private val client = HttpClient.newHttpClient()

    @AfterEach
    fun close() = server.close()

    @Test
    fun `answers authorized JSON-RPC posts on loopback`() {
        val (status, body) = post(INITIALIZE)

        assertEquals(200, status)
        assertTrue(body.contains("\"protocolVersion\":\"2025-06-18\""))
        assertTrue(server.url.startsWith("http://127.0.0.1:"))
        assertFalse(server.toString().contains(server.token))
    }

    @Test
    fun `notifications are accepted without a body`() {
        assertEquals(202, post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").first)
    }

    @Test
    fun `rejects missing or wrong tokens, browser origins and foreign hosts`() {
        assertEquals(401, post(INITIALIZE, token = null).first)
        assertEquals(401, post(INITIALIZE, token = "wrong").first)
        assertEquals(403, post(INITIALIZE, headers = mapOf("Origin" to "https://evil.example")).first)
        assertEquals(200, post(INITIALIZE, headers = mapOf("Origin" to "http://localhost:3000")).first)
    }

    @Test
    fun `requires POST with JSON and refuses batches`() {
        assertEquals(405, request("GET", null).first)
        assertEquals(415, post(INITIALIZE, contentType = "text/plain").first)
        val (status, body) = post("[$INITIALIZE]")
        assertEquals(200, status)
        assertTrue(body.contains("-32600"))
        assertTrue(post("{not json").second.contains("-32700"))
    }

    private fun post(
        body: String,
        token: String? = server.token,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
    ) = request("POST", body, token, headers + ("Content-Type" to contentType))

    private fun request(
        method: String,
        body: String?,
        token: String? = server.token,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val builder = HttpRequest.newBuilder(URI(server.url))
        token?.let { builder.header("Authorization", "Bearer $it") }
        headers.forEach(builder::header)
        builder.method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return response.statusCode() to response.body()
    }

    private companion object {
        const val INITIALIZE = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""
    }
}
