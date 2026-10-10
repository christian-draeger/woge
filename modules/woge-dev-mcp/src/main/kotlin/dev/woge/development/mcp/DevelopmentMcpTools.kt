package dev.woge.development.mcp

import dev.woge.development.AwaitBuildRequest
import dev.woge.development.AwaitBuildResult
import dev.woge.development.BuildCancelled
import dev.woge.development.BuildFailed
import dev.woge.development.BuildId
import dev.woge.development.BuildSucceeded
import dev.woge.development.DevelopmentApplicationManifest
import dev.woge.development.DevelopmentBuildTerminalEvent
import dev.woge.development.DevelopmentCommandResult
import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentReloadCommand
import dev.woge.development.DevelopmentRestartCommand
import dev.woge.development.DevelopmentSessionPhase
import dev.woge.development.DevelopmentSessionState
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import dev.woge.development.WogeDevelopmentCapabilities
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** A manifest whose generator already produced a JSON document, such as `.woge/manifest.json`. */
@ExperimentalWogeDevelopmentApi
public class JsonDevelopmentManifest(
    override val schemaVersion: Int,
    override val buildId: BuildId,
    public val document: JsonObject,
) : DevelopmentApplicationManifest

internal class DevelopmentMcpToolResult(
    val value: JsonObject,
    val isError: Boolean = false,
)

/** The seven development tools. Every result is a JSON object; no raw logs or request data. */
@ExperimentalWogeDevelopmentApi
@Suppress("TooManyFunctions")
internal class DevelopmentMcpTools(
    private val capabilities: WogeDevelopmentCapabilities,
    private val pollInterval: Duration = 100.milliseconds,
) {
    fun has(name: String): Boolean = name in NAMES

    fun definitions(): JsonArray = DEFINITIONS

    suspend fun call(
        name: String,
        arguments: JsonObject,
    ): DevelopmentMcpToolResult =
        try {
            when (name) {
                "status" -> DevelopmentMcpToolResult(status(capabilities.status()))
                "await_build" -> awaitBuild(arguments)
                "get_diagnostics" ->
                    DevelopmentMcpToolResult(
                        buildJsonObject { put("diagnostics", diagnostics(capabilities.diagnostics())) },
                    )
                "reload" -> reload(arguments)
                "restart" -> restart(arguments)
                "get_manifest" -> manifest()
                "get_dev_urls" -> {
                    val urls = capabilities.developmentUrls()
                    DevelopmentMcpToolResult(
                        buildJsonObject { putJsonArray("urls") { urls.forEach { add(it.value) } } },
                    )
                }
                else -> invalid("Unknown tool '$name'")
            }
        } catch (failure: IllegalArgumentException) {
            invalid(failure.message ?: "Invalid arguments")
        }

    private suspend fun awaitBuild(arguments: JsonObject): DevelopmentMcpToolResult {
        val timeout = (arguments.number("timeoutSeconds") ?: DEFAULT_TIMEOUT_SECONDS).seconds
        require(timeout.isPositive() && timeout <= MAX_TIMEOUT) { "timeoutSeconds must be between 0 and 300" }
        val waitForReady = arguments.boolean("waitForReady") ?: true
        val started = TimeSource.Monotonic.markNow()
        val result = capabilities.awaitBuild(AwaitBuildRequest(arguments.buildId("afterBuild"), timeout))
        if (result is AwaitBuildResult.Observed && result.outcome is BuildSucceeded && waitForReady) {
            awaitSettled(timeout - started.elapsedNow())
        }
        val state = capabilities.status()
        return DevelopmentMcpToolResult(
            buildJsonObject {
                when (result) {
                    is AwaitBuildResult.Observed -> {
                        put("result", outcomeName(result.outcome))
                        put("build", outcome(result.outcome))
                    }
                    is AwaitBuildResult.TimedOut -> put("result", "timedOut")
                    AwaitBuildResult.SessionStopped -> put("result", "sessionStopped")
                }
                put("status", status(state))
            },
        )
    }

    /** Waits until the application serves the new build, or until the session reports why it cannot. */
    private suspend fun awaitSettled(remaining: Duration) {
        val deadline = TimeSource.Monotonic.markNow() + remaining
        while (deadline.hasNotPassedNow() && capabilities.status().phase in UNSETTLED) delay(pollInterval)
    }

    private suspend fun reload(arguments: JsonObject): DevelopmentMcpToolResult {
        val level = arguments.level(ReloadLevel.DOCUMENT_REFRESH)
        require(!level.satisfies(ReloadLevel.SERVER_RESTART)) { "Use restart for SERVER_RESTART or COLD_RESTART" }
        val build = arguments.buildId("buildId") ?: capabilities.status().lastSuccessfulBuild
        return command(build) { capabilities.reload(DevelopmentReloadCommand(it, level)) }
    }

    private suspend fun restart(arguments: JsonObject): DevelopmentMcpToolResult {
        // A fast in-JVM restart only reacts to changed classes, which an explicit restart never has.
        val build = arguments.buildId("buildId") ?: capabilities.status().lastSuccessfulBuild
        return command(build) { capabilities.restart(DevelopmentRestartCommand(it, ReloadLevel.COLD_RESTART)) }
    }

    private suspend fun command(
        build: BuildId?,
        send: suspend (BuildId) -> DevelopmentCommandResult,
    ): DevelopmentMcpToolResult {
        if (build == null) return invalid("No successful build yet; call await_build first")
        return when (val result = send(build)) {
            is DevelopmentCommandResult.Accepted ->
                DevelopmentMcpToolResult(
                    buildJsonObject {
                        put("accepted", true)
                        put("buildId", result.buildId.value)
                        put("level", result.selectedLevel.name)
                    },
                )
            is DevelopmentCommandResult.Refused ->
                DevelopmentMcpToolResult(
                    buildJsonObject {
                        put("accepted", false)
                        put("reason", result.reason.name)
                    },
                    isError = true,
                )
        }
    }

    private suspend fun manifest(): DevelopmentMcpToolResult {
        val manifest =
            capabilities.applicationManifest()
                ?: return DevelopmentMcpToolResult(
                    buildJsonObject {
                        put("manifest", JsonNull)
                        put("reason", "No manifest for the last successful build yet. Call await_build first.")
                    },
                )
        return DevelopmentMcpToolResult(
            buildJsonObject {
                put("buildId", manifest.buildId.value)
                put(
                    "manifest",
                    (manifest as? JsonDevelopmentManifest)?.document
                        ?: buildJsonObject { put("schemaVersion", manifest.schemaVersion) },
                )
            },
        )
    }

    private fun status(state: DevelopmentSessionState): JsonObject =
        buildJsonObject {
            put("phase", state.phase.name)
            putBuild("latestRequestedBuild", state.latestRequestedBuild)
            putBuild("activeBuild", state.activeBuild)
            putBuild("lastSuccessfulBuild", state.lastSuccessfulBuild)
            put("latestBuild", state.latestBuildOutcome?.let(::outcome) ?: JsonNull)
            put("activeServerGeneration", state.activeServerGeneration?.value?.let(::JsonPrimitive) ?: JsonNull)
            put("pendingServerGeneration", state.pendingServerGeneration?.value?.let(::JsonPrimitive) ?: JsonNull)
            put("pendingReload", state.pendingReload?.name)
            put("applicationAvailable", state.hasLastValidApplication)
            putJsonArray("urls") { state.developmentUrls.forEach { add(it.value) } }
            put("diagnosticCount", state.diagnostics.size)
        }

    private fun outcome(event: DevelopmentBuildTerminalEvent): JsonObject =
        buildJsonObject {
            put("buildId", event.buildId.value)
            put("result", outcomeName(event))
            when (event) {
                is BuildSucceeded -> {
                    put("durationMillis", event.duration.inWholeMilliseconds)
                    put("requiredReload", event.requiredReload.name)
                }
                is BuildFailed -> {
                    put("durationMillis", event.duration.inWholeMilliseconds)
                    put("diagnostics", diagnostics(event.diagnostics))
                }
                is BuildCancelled -> put("reason", event.reason.name)
            }
        }

    private fun outcomeName(event: DevelopmentBuildTerminalEvent): String =
        when (event) {
            is BuildSucceeded -> "succeeded"
            is BuildFailed -> "failed"
            is BuildCancelled -> "cancelled"
        }

    private fun diagnostics(values: List<DevelopmentDiagnostic>): JsonArray =
        buildJsonArray {
            values.forEach { diagnostic ->
                add(
                    buildJsonObject {
                        put("code", diagnostic.code.value)
                        put("severity", diagnostic.severity.name)
                        put("message", diagnostic.summary.value)
                        diagnostic.location?.let {
                            put("file", it.path.value)
                            put("line", it.line)
                            put("column", it.column)
                        }
                    },
                )
            }
        }

    private fun invalid(message: String) =
        DevelopmentMcpToolResult(buildJsonObject { put("error", message) }, isError = true)

    private fun JsonObjectBuilder.putBuild(
        key: String,
        build: BuildId?,
    ) {
        put(key, build?.value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.buildId(key: String): BuildId? =
        (this[key] as? JsonPrimitive)?.let { value ->
            BuildId.of(requireNotNull(value.longOrNull) { "$key must be a positive whole number" })
        }

    private fun JsonObject.level(default: ReloadLevel): ReloadLevel =
        (this["level"] as? JsonPrimitive)?.contentOrNull?.let { name ->
            requireNotNull(ReloadLevel.entries.find { it.name == name }) { "Unknown level '$name'" }
        } ?: default

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 60.0
        val MAX_TIMEOUT = 300.seconds
        val UNSETTLED =
            setOf(
                DevelopmentSessionPhase.BUILDING,
                DevelopmentSessionPhase.RELOAD_PENDING,
                DevelopmentSessionPhase.SERVER_RESTARTING,
            )
        val NAMES =
            setOf("status", "await_build", "get_diagnostics", "reload", "restart", "get_manifest", "get_dev_urls")

        val DEFINITIONS =
            buildJsonArray {
                add(tool("status", "Current build and server state of the Woge development session."))
                add(
                    tool(
                        "await_build",
                        "Waits for the next build after `afterBuild` to finish and, if it succeeded, for the " +
                            "application to serve it. Returns the outcome with file and line diagnostics.",
                    ) {
                        putJsonObject("afterBuild") {
                            put("type", "integer")
                            put("minimum", 1)
                            put("description", "latestRequestedBuild from status, read before editing.")
                        }
                        putJsonObject("timeoutSeconds") {
                            put("type", "number")
                            put("description", "Default 60, at most 300.")
                        }
                        putJsonObject("waitForReady") {
                            put("type", "boolean")
                            put("description", "Default true: also wait until the new version serves requests.")
                        }
                    },
                )
                add(tool("get_diagnostics", "Compiler and startup errors of the latest build, with file and line."))
                add(
                    tool("reload", "Asks browsers to reload. Refused with STALE_BUILD for an outdated build.") {
                        buildIdProperty()
                        levelProperty("HOT_ASSET", "HOT_FRONTEND_MODULE", "DOCUMENT_REFRESH")
                    },
                )
                add(
                    tool(
                        "restart",
                        "Starts a fresh application process. Refused with STALE_BUILD for an outdated build.",
                    ) { buildIdProperty() },
                )
                add(
                    tool(
                        "get_manifest",
                        "The application manifest (.woge/manifest.json) of the last successful build: " +
                            "pages, actions, components and regions.",
                    ),
                )
                add(tool("get_dev_urls", "Local URLs of the running application. Open them with a browser tool."))
            }

        fun tool(
            name: String,
            description: String,
            properties: JsonObjectBuilder.() -> Unit = {},
        ): JsonObject =
            buildJsonObject {
                put("name", name)
                put("description", description)
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties", properties)
                    put("additionalProperties", false)
                }
            }

        fun JsonObjectBuilder.buildIdProperty() {
            putJsonObject("buildId") {
                put("type", "integer")
                put("minimum", 1)
                put("description", "Defaults to lastSuccessfulBuild.")
            }
        }

        fun JsonObjectBuilder.levelProperty(vararg levels: String) {
            putJsonObject("level") {
                put("type", "string")
                putJsonArray("enum") { levels.forEach { add(it) } }
            }
        }
    }
}
