package dev.woge.development

@ExperimentalWogeDevelopmentApi
public enum class DevelopmentSessionPhase {
    IDLE,
    BUILDING,
    BUILD_FAILED,
    RELOAD_PENDING,
    SERVER_RESTARTING,
    READY,
    STOPPED,
}

/** Immutable status snapshot reduced from ordered [DevelopmentEvent] values. */
@ExperimentalWogeDevelopmentApi
@Suppress("LongParameterList")
public class DevelopmentSessionState internal constructor(
    public val phase: DevelopmentSessionPhase,
    public val latestRequestedBuild: BuildId?,
    public val activeBuild: BuildId?,
    public val activeChanges: Set<DevelopmentChange>,
    public val latestBuildOutcome: DevelopmentBuildTerminalEvent?,
    public val lastSuccessfulBuild: BuildId?,
    public val activeServerGeneration: ServerGeneration?,
    public val pendingServerGeneration: ServerGeneration?,
    public val diagnostics: List<DevelopmentDiagnostic>,
    public val pendingReload: ReloadLevel?,
    public val developmentUrls: List<DevelopmentUrl>,
) {
    /** Whether an earlier valid application remains available, including while a later build fails. */
    public val hasLastValidApplication: Boolean
        get() = activeServerGeneration != null && developmentUrls.isNotEmpty()

    override fun toString(): String =
        "DevelopmentSessionState(" +
            "phase=$phase, " +
            "latestRequestedBuild=$latestRequestedBuild, " +
            "activeBuild=$activeBuild, " +
            "lastSuccessfulBuild=$lastSuccessfulBuild, " +
            "activeServerGeneration=$activeServerGeneration, " +
            "pendingServerGeneration=$pendingServerGeneration, " +
            "diagnosticCount=${diagnostics.size}, " +
            "pendingReload=$pendingReload, " +
            "developmentUrlCount=${developmentUrls.size})"

    public companion object {
        public fun initial(): DevelopmentSessionState =
            DevelopmentSessionState(
                phase = DevelopmentSessionPhase.IDLE,
                latestRequestedBuild = null,
                activeBuild = null,
                activeChanges = emptySet(),
                latestBuildOutcome = null,
                lastSuccessfulBuild = null,
                activeServerGeneration = null,
                pendingServerGeneration = null,
                diagnostics = emptyList(),
                pendingReload = null,
                developmentUrls = emptyList(),
            )
    }
}

@ExperimentalWogeDevelopmentApi
public enum class DevelopmentEventDisposition {
    APPLIED,
    IGNORED_STALE,
    REJECTED,
}

@ExperimentalWogeDevelopmentApi
public enum class DevelopmentTransitionReason {
    SESSION_STOPPED,
    STALE_BUILD,
    BUILD_NOT_REQUESTED,
    BUILD_NOT_ACTIVE,
    BUILD_NOT_SUCCESSFUL,
    STALE_SERVER_GENERATION,
    SERVER_GENERATION_NOT_REQUESTED,
    SERVER_NOT_READY,
    NO_RELOAD_PENDING,
    RELOAD_LEVEL_TOO_WEAK,
}

/** Result of reducing one event. Rejected or stale events always retain the original state instance. */
@ExperimentalWogeDevelopmentApi
public class DevelopmentTransition internal constructor(
    public val state: DevelopmentSessionState,
    public val disposition: DevelopmentEventDisposition,
    public val reason: DevelopmentTransitionReason?,
) {
    public val wasApplied: Boolean
        get() = disposition == DevelopmentEventDisposition.APPLIED
}
