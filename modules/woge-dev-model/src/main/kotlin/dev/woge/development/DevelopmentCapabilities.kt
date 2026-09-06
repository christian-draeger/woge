package dev.woge.development

import kotlin.time.Duration

/** Cursor and bounded wait requested by a development-tool client. */
@ExperimentalWogeDevelopmentApi
public data class AwaitBuildRequest(
    public val afterBuild: BuildId?,
    public val timeout: Duration,
) {
    init {
        require(timeout.isFinite() && timeout.isPositive()) { "Await-build timeout must be finite and positive" }
    }
}

@ExperimentalWogeDevelopmentApi
public sealed interface AwaitBuildResult {
    public data class Observed(
        public val outcome: DevelopmentBuildTerminalEvent,
    ) : AwaitBuildResult

    public data class TimedOut(
        public val latestRequestedBuild: BuildId?,
    ) : AwaitBuildResult

    public data object SessionStopped : AwaitBuildResult
}

/**
 * Minimal contract implemented by the application manifest owned by its generator.
 *
 * The concrete schema is intentionally not part of the lifecycle model.
 */
@ExperimentalWogeDevelopmentApi
public interface DevelopmentApplicationManifest {
    public val schemaVersion: Int
    public val buildId: BuildId
}

@ExperimentalWogeDevelopmentApi
public data class DevelopmentReloadCommand(
    public val buildId: BuildId,
    public val level: ReloadLevel,
) {
    init {
        require(!level.satisfies(ReloadLevel.SERVER_RESTART)) {
            "Reload commands support hot updates and document refresh; use restart for server levels"
        }
    }
}

@ExperimentalWogeDevelopmentApi
public data class DevelopmentRestartCommand(
    public val buildId: BuildId,
    public val level: ReloadLevel,
) {
    init {
        require(level.satisfies(ReloadLevel.SERVER_RESTART)) {
            "Restart commands require SERVER_RESTART or COLD_RESTART"
        }
    }
}

@ExperimentalWogeDevelopmentApi
public sealed interface DevelopmentCommandResult {
    public data class Accepted(
        public val buildId: BuildId,
        public val selectedLevel: ReloadLevel,
    ) : DevelopmentCommandResult

    public data class Refused(
        public val reason: DevelopmentCommandRefusal,
    ) : DevelopmentCommandResult
}

@ExperimentalWogeDevelopmentApi
public enum class DevelopmentCommandRefusal {
    NOT_AUTHORIZED,
    STALE_BUILD,
    UNSUPPORTED,
    SESSION_STOPPED,
}

/**
 * Structured object-capability for Woge development tools.
 *
 * Implementations are privileged: transport adapters must authenticate and authorize callers before
 * exposing this object or invoking its mutating methods. CLI, Gradle, browser, MCP and future IDE
 * integrations adapt this same contract instead of parsing terminal output.
 */
@ExperimentalWogeDevelopmentApi
public interface WogeDevelopmentCapabilities {
    public suspend fun status(): DevelopmentSessionState

    public suspend fun awaitBuild(request: AwaitBuildRequest): AwaitBuildResult

    public suspend fun diagnostics(): List<DevelopmentDiagnostic>

    public suspend fun reload(command: DevelopmentReloadCommand): DevelopmentCommandResult

    public suspend fun restart(command: DevelopmentRestartCommand): DevelopmentCommandResult

    public suspend fun applicationManifest(): DevelopmentApplicationManifest?

    public suspend fun developmentUrls(): List<DevelopmentUrl>
}
