package dev.woge.development.mcp

import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.WogeDevelopmentCapabilities
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * The JSON-RPC side of the Woge development MCP endpoint, independent of the HTTP transport.
 *
 * Supports `initialize`, `ping`, `tools/list` and `tools/call`. Notifications get no response.
 */
@ExperimentalWogeDevelopmentApi
public class DevelopmentMcpProtocol(
    capabilities: WogeDevelopmentCapabilities,
    private val serverVersion: String = "experimental",
) {
    private val tools = DevelopmentMcpTools(capabilities)

    /** Handles one JSON-RPC message. Returns `null` for notifications, which have no response. */
    public suspend fun handle(message: JsonElement): JsonObject? {
        val request = message as? JsonObject ?: return error(JsonNull, INVALID_REQUEST, "Expected one JSON-RPC object")
        val id = request["id"]
        val method = (request["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when {
            method == null -> if (id == null) null else error(id, INVALID_REQUEST, "Missing method")
            id == null -> null
            else -> respond(id, method, request["params"] as? JsonObject ?: JsonObject(emptyMap()))
        }
    }

    private suspend fun respond(
        id: JsonElement,
        method: String,
        params: JsonObject,
    ): JsonObject =
        when (method) {
            "initialize" -> result(id, initialize(params))
            "ping" -> result(id, JsonObject(emptyMap()))
            "tools/list" -> result(id, buildJsonObject { put("tools", tools.definitions()) })
            "tools/call" -> call(id, params)
            else -> error(id, METHOD_NOT_FOUND, "Unknown method '$method'")
        }

    private fun initialize(params: JsonObject): JsonObject {
        val requested = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
        return buildJsonObject {
            put("protocolVersion", requested?.takeIf { it in SUPPORTED_VERSIONS } ?: SUPPORTED_VERSIONS.first())
            put("capabilities", buildJsonObject { put("tools", buildJsonObject { put("listChanged", false) }) })
            put(
                "serverInfo",
                buildJsonObject {
                    put("name", "woge-dev")
                    put("version", serverVersion)
                },
            )
            put("instructions", INSTRUCTIONS)
        }
    }

    private suspend fun call(
        id: JsonElement,
        params: JsonObject,
    ): JsonObject {
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        return when {
            name == null -> error(id, INVALID_PARAMS, "Missing tool name")
            !tools.has(name) -> error(id, INVALID_PARAMS, "Unknown tool '$name'")
            else -> result(id, tools.call(name, arguments).toMcp())
        }
    }

    private fun DevelopmentMcpToolResult.toMcp(): JsonObject =
        buildJsonObject {
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", value.toString())
                        },
                    )
                },
            )
            put("structuredContent", value)
            put("isError", isError)
        }

    private companion object {
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602

        /** Newest first. Older clients get the version they asked for when it is supported. */
        val SUPPORTED_VERSIONS = listOf("2025-06-18", "2025-03-26")

        const val INSTRUCTIONS =
            "Woge development session. Before editing, call status and remember latestRequestedBuild. " +
                "After editing, call await_build with afterBuild set to that value. A failed build " +
                "returns diagnostics with file and line. Use your browser tool for clicks, typing and " +
                "screenshots; open the URLs from get_dev_urls."

        fun result(
            id: JsonElement,
            value: JsonObject,
        ): JsonObject =
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("result", value)
            }

        fun error(
            id: JsonElement,
            code: Int,
            message: String,
        ): JsonObject =
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put(
                    "error",
                    buildJsonObject {
                        put("code", code)
                        put("message", message)
                    },
                )
            }
    }
}
