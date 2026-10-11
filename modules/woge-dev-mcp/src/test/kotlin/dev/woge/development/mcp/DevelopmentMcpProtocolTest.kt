package dev.woge.development.mcp

import dev.woge.development.BuildId
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalWogeDevelopmentApi::class)
class DevelopmentMcpProtocolTest {
    private val capabilities = FakeCapabilities()
    private val protocol = DevelopmentMcpProtocol(capabilities)

    @Test
    fun `initialize negotiates a supported protocol version and offers only tools`() =
        runTest {
            val result = request("initialize", """{"protocolVersion":"2025-03-26","capabilities":{}}""")

            assertEquals("2025-03-26", result.string("protocolVersion"))
            assertEquals(setOf("tools"), result["capabilities"]!!.jsonObject.keys)
            assertEquals("woge-dev", result["serverInfo"]!!.jsonObject.string("name"))
            assertEquals(
                "2025-06-18",
                request("initialize", """{"protocolVersion":"1999-01-01"}""").string("protocolVersion"),
            )
        }

    @Test
    fun `notifications get no response and unknown methods a JSON-RPC error`() =
        runTest {
            val notification = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
            assertNull(protocol.handle(Json.parseToJsonElement(notification)))
            val unknown = """{"jsonrpc":"2.0","id":7,"method":"resources/list"}"""
            val response = protocol.handle(Json.parseToJsonElement(unknown))!!
            assertEquals(
                -32601,
                response["error"]!!
                    .jsonObject["code"]!!
                    .jsonPrimitive.content
                    .toInt(),
            )
            assertEquals("7", response["id"].toString())
        }

    @Test
    fun `lists exactly the seven development tools with object schemas`() =
        runTest {
            val tools = request("tools/list").getValue("tools").jsonArray.map { it.jsonObject }

            assertEquals(
                listOf("status", "await_build", "get_diagnostics", "reload", "restart", "get_manifest", "get_dev_urls"),
                tools.map { it.string("name") },
            )
            tools.forEach { assertEquals("object", it["inputSchema"]!!.jsonObject.string("type")) }
        }

    @Test
    fun `await_build reports a compiler error with file and line, then the fixed build`() =
        runTest {
            capabilities.buildAndServe(1, 1)
            capabilities.fail(2)

            val failed = call("await_build", buildJsonObject { put("afterBuild", 1) })
            assertEquals("failed", failed.string("result"))
            val diagnostic =
                failed["build"]!!
                    .jsonObject["diagnostics"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("src/main/kotlin/HomePage.kt", diagnostic.string("file"))
            assertEquals(12, diagnostic.int("line"))
            assertEquals("KOTLIN-COMPILE", diagnostic.string("code"))
            assertTrue(
                failed["status"]!!
                    .jsonObject["applicationAvailable"]!!
                    .jsonPrimitive.content
                    .toBoolean(),
            )
            assertEquals(1, call("get_diagnostics").getValue("diagnostics").jsonArray.size)

            capabilities.buildAndServe(3, 2)
            val fixed = call("await_build", buildJsonObject { put("afterBuild", 2) })
            assertEquals("succeeded", fixed.string("result"))
            assertEquals("READY", fixed["status"]!!.jsonObject.string("phase"))
            assertEquals(2, fixed["status"]!!.jsonObject.int("activeServerGeneration"))
            assertEquals(0, call("get_diagnostics").getValue("diagnostics").jsonArray.size)
        }

    @Test
    fun `await_build times out without a newer build`() =
        runTest {
            capabilities.buildAndServe(1, 1)

            val result = call("await_build", buildJsonObject { put("afterBuild", 1) })

            assertEquals("timedOut", result.string("result"))
        }

    @Test
    fun `restart defaults to the last successful build and refuses a stale one`() =
        runTest {
            capabilities.buildAndServe(1, 1)
            capabilities.buildAndServe(2, 2)

            val accepted = callResult("restart")
            assertEquals("false", accepted["isError"].toString())
            assertEquals(BuildId.of(2) to ReloadLevel.COLD_RESTART, capabilities.commands.last())

            val stale = callResult("reload", buildJsonObject { put("buildId", 1) })
            assertEquals("true", stale["isError"].toString())
            assertEquals("STALE_BUILD", stale["structuredContent"]!!.jsonObject.string("reason"))
        }

    @Test
    fun `invalid tool arguments become tool errors the agent can correct`() =
        runTest {
            val wrongLevel = callResult("reload", buildJsonObject { put("level", "SERVER_RESTART") })
            assertEquals("true", wrongLevel["isError"].toString())

            val noBuild = callResult("reload")
            assertEquals("true", noBuild["isError"].toString())
            assertTrue(noBuild["structuredContent"]!!.jsonObject.string("error").contains("await_build"))
        }

    @Test
    fun `manifest and urls come from the capabilities only`() =
        runTest {
            assertEquals("null", call("get_manifest")["manifest"].toString())
            capabilities.buildAndServe(1, 1)
            capabilities.manifest = FakeCapabilities.manifest(1)

            assertEquals(1, call("get_manifest").int("buildId"))
            val urls = call("get_dev_urls").getValue("urls").jsonArray
            assertEquals(listOf("http://127.0.0.1:8080/"), urls.map { it.jsonPrimitive.content })
        }

    private suspend fun request(
        method: String,
        params: String = "{}",
    ): JsonObject {
        val message = """{"jsonrpc":"2.0","id":1,"method":"$method","params":$params}"""
        return protocol.handle(Json.parseToJsonElement(message))!!.getValue("result").jsonObject
    }

    private suspend fun callResult(
        name: String,
        arguments: JsonObject = JsonObject(emptyMap()),
    ): JsonObject =
        request(
            "tools/call",
            buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            }.toString(),
        )

    private suspend fun call(
        name: String,
        arguments: JsonObject = JsonObject(emptyMap()),
    ): JsonObject {
        val result = callResult(name, arguments)
        val structured = result.getValue("structuredContent").jsonObject
        assertEquals(structured, Json.parseToJsonElement(result["content"]!!.jsonArray[0].jsonObject.string("text")))
        return structured
    }

    private fun JsonObject.string(key: String): String = (getValue(key) as JsonPrimitive).content

    private fun JsonObject.int(key: String): Int = string(key).toInt()
}
