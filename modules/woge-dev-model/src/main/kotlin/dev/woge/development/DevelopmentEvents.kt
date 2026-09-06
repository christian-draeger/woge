package dev.woge.development

import kotlin.time.Duration

@ExperimentalWogeDevelopmentApi
public enum class DevelopmentChangeKind {
    KOTLIN_SOURCE,
    GENERATED_SOURCE,
    CSS,
    FRONTEND_MODULE,
    BUILD_CONFIGURATION,
    UNKNOWN,
}

/** One semantic input change. Content and absolute workstation paths are intentionally absent. */
@ExperimentalWogeDevelopmentApi
public data class DevelopmentChange(
    public val kind: DevelopmentChangeKind,
    public val path: DevelopmentSourcePath? = null,
)

@ExperimentalWogeDevelopmentApi
public sealed interface DevelopmentEvent

@ExperimentalWogeDevelopmentApi
public sealed interface DevelopmentBuildEvent : DevelopmentEvent {
    public val buildId: BuildId
}

@ExperimentalWogeDevelopmentApi
public sealed interface DevelopmentBuildTerminalEvent : DevelopmentBuildEvent

@ExperimentalWogeDevelopmentApi
public data class BuildStarted(
    override val buildId: BuildId,
    public val changes: Set<DevelopmentChange>,
) : DevelopmentBuildEvent

@ExperimentalWogeDevelopmentApi
public data class BuildSucceeded(
    override val buildId: BuildId,
    public val duration: Duration,
    public val requiredReload: ReloadLevel,
) : DevelopmentBuildTerminalEvent {
    init {
        requireValidDuration(duration)
    }
}

@ExperimentalWogeDevelopmentApi
public data class BuildFailed(
    override val buildId: BuildId,
    public val duration: Duration,
    public val diagnostics: List<DevelopmentDiagnostic>,
) : DevelopmentBuildTerminalEvent {
    init {
        requireValidDuration(duration)
        require(diagnostics.isNotEmpty()) { "A failed build must contain at least one structured diagnostic" }
    }
}

@ExperimentalWogeDevelopmentApi
public enum class BuildCancellationReason {
    SUPERSEDED,
    REQUESTED,
    SESSION_STOPPING,
}

@ExperimentalWogeDevelopmentApi
public data class BuildCancelled(
    override val buildId: BuildId,
    public val reason: BuildCancellationReason,
) : DevelopmentBuildTerminalEvent

/** A server restart has started or a newer restart request superseded the previous request. */
@ExperimentalWogeDevelopmentApi
public data class ServerRestarting(
    public val buildId: BuildId,
    public val expectedGeneration: ServerGeneration,
    public val level: ReloadLevel,
) : DevelopmentEvent {
    init {
        require(level.satisfies(ReloadLevel.SERVER_RESTART)) {
            "A server restart must use SERVER_RESTART or COLD_RESTART"
        }
    }
}

@ExperimentalWogeDevelopmentApi
public data class ServerReady(
    public val buildId: BuildId,
    public val generation: ServerGeneration,
    public val urls: List<DevelopmentUrl>,
) : DevelopmentEvent {
    init {
        require(urls.isNotEmpty()) { "A ready development server must expose at least one local URL" }
    }
}

@ExperimentalWogeDevelopmentApi
public data class CssChanged(
    public val buildId: BuildId,
    public val resources: Set<DevelopmentSourcePath>,
) : DevelopmentEvent {
    init {
        require(resources.isNotEmpty()) { "A CSS change must identify at least one resource" }
    }
}

@ExperimentalWogeDevelopmentApi
public data class FrontendChanged(
    public val buildId: BuildId,
    public val modules: Set<DevelopmentSourcePath>,
) : DevelopmentEvent {
    init {
        require(modules.isNotEmpty()) { "A frontend change must identify at least one module" }
    }
}

@ExperimentalWogeDevelopmentApi
public data class ReloadRequired(
    public val buildId: BuildId,
    public val minimumLevel: ReloadLevel,
) : DevelopmentEvent

@ExperimentalWogeDevelopmentApi
public data class ReloadApplied(
    public val buildId: BuildId,
    public val level: ReloadLevel,
) : DevelopmentEvent {
    init {
        require(!level.satisfies(ReloadLevel.SERVER_RESTART)) {
            "Server restart completion must be reported with ServerReady"
        }
    }
}

@ExperimentalWogeDevelopmentApi
public data object DevelopmentSessionStopped : DevelopmentEvent

private fun requireValidDuration(duration: Duration) {
    require(duration.isFinite() && !duration.isNegative()) { "Build duration must be finite and non-negative" }
}
