package dev.woge.development

/** Pure state transition function shared by all Woge development-tool adapters. */
@ExperimentalWogeDevelopmentApi
@Suppress("TooManyFunctions")
public object DevelopmentLifecycle {
    public fun reduce(
        state: DevelopmentSessionState,
        event: DevelopmentEvent,
    ): DevelopmentTransition {
        if (state.phase == DevelopmentSessionPhase.STOPPED) {
            return rejected(state, DevelopmentTransitionReason.SESSION_STOPPED)
        }
        return when (event) {
            is BuildStarted -> reduceBuildStarted(state, event)
            is BuildSucceeded -> reduceBuildSucceeded(state, event)
            is BuildFailed -> reduceBuildFailed(state, event)
            is BuildCancelled -> reduceBuildCancelled(state, event)
            is ServerRestarting -> reduceServerRestarting(state, event)
            is ServerReady -> reduceServerReady(state, event)
            is CssChanged -> reduceCssChanged(state, event)
            is FrontendChanged -> reduceFrontendChanged(state, event)
            is ReloadRequired -> reduceReloadRequired(state, event)
            is ReloadApplied -> reduceReloadApplied(state, event)
            DevelopmentSessionStopped -> reduceStopped(state)
        }
    }

    private fun reduceBuildStarted(
        state: DevelopmentSessionState,
        event: BuildStarted,
    ): DevelopmentTransition {
        val latest = state.latestRequestedBuild
        if (latest != null && event.buildId <= latest) {
            return stale(state, DevelopmentTransitionReason.STALE_BUILD)
        }
        val coalescedChanges =
            if (state.activeBuild == null) {
                event.changes.toSet()
            } else {
                state.activeChanges + event.changes
            }
        return applied(
            DevelopmentSessionState(
                phase = DevelopmentSessionPhase.BUILDING,
                latestRequestedBuild = event.buildId,
                activeBuild = event.buildId,
                activeChanges = coalescedChanges,
                latestBuildOutcome = null,
                lastSuccessfulBuild = state.lastSuccessfulBuild,
                activeServerGeneration = state.activeServerGeneration,
                pendingServerGeneration = null,
                diagnostics = emptyList(),
                pendingReload = null,
                developmentUrls = state.developmentUrls,
            ),
        )
    }

    private fun reduceBuildSucceeded(
        state: DevelopmentSessionState,
        event: BuildSucceeded,
    ): DevelopmentTransition =
        withActiveBuild(state, event.buildId) {
            applied(
                DevelopmentSessionState(
                    phase = DevelopmentSessionPhase.RELOAD_PENDING,
                    latestRequestedBuild = state.latestRequestedBuild,
                    activeBuild = null,
                    activeChanges = emptySet(),
                    latestBuildOutcome = event,
                    lastSuccessfulBuild = event.buildId,
                    activeServerGeneration = state.activeServerGeneration,
                    pendingServerGeneration = null,
                    diagnostics = emptyList(),
                    pendingReload = event.requiredReload,
                    developmentUrls = state.developmentUrls,
                ),
            )
        }

    private fun reduceBuildFailed(
        state: DevelopmentSessionState,
        event: BuildFailed,
    ): DevelopmentTransition =
        withActiveBuild(state, event.buildId) {
            val stableEvent = event.copy(diagnostics = event.diagnostics.toList())
            applied(
                DevelopmentSessionState(
                    phase = DevelopmentSessionPhase.BUILD_FAILED,
                    latestRequestedBuild = state.latestRequestedBuild,
                    activeBuild = null,
                    activeChanges = emptySet(),
                    latestBuildOutcome = stableEvent,
                    lastSuccessfulBuild = state.lastSuccessfulBuild,
                    activeServerGeneration = state.activeServerGeneration,
                    pendingServerGeneration = null,
                    diagnostics = stableEvent.diagnostics,
                    pendingReload = null,
                    developmentUrls = state.developmentUrls,
                ),
            )
        }

    private fun reduceBuildCancelled(
        state: DevelopmentSessionState,
        event: BuildCancelled,
    ): DevelopmentTransition =
        withActiveBuild(state, event.buildId) {
            applied(
                DevelopmentSessionState(
                    phase = restingPhase(state),
                    latestRequestedBuild = state.latestRequestedBuild,
                    activeBuild = null,
                    activeChanges = emptySet(),
                    latestBuildOutcome = event,
                    lastSuccessfulBuild = state.lastSuccessfulBuild,
                    activeServerGeneration = state.activeServerGeneration,
                    pendingServerGeneration = state.pendingServerGeneration,
                    diagnostics = emptyList(),
                    pendingReload = state.pendingReload,
                    developmentUrls = state.developmentUrls,
                ),
            )
        }

    private fun reduceServerRestarting(
        state: DevelopmentSessionState,
        event: ServerRestarting,
    ): DevelopmentTransition =
        withCurrentSuccessfulBuild(state, event.buildId) {
            val activeGeneration = state.activeServerGeneration
            val pendingGeneration = state.pendingServerGeneration
            if (isStaleGeneration(event.expectedGeneration, activeGeneration, pendingGeneration)) {
                stale(state, DevelopmentTransitionReason.STALE_SERVER_GENERATION)
            } else {
                applied(
                    DevelopmentSessionState(
                        phase = DevelopmentSessionPhase.SERVER_RESTARTING,
                        latestRequestedBuild = state.latestRequestedBuild,
                        activeBuild = null,
                        activeChanges = emptySet(),
                        latestBuildOutcome = state.latestBuildOutcome,
                        lastSuccessfulBuild = state.lastSuccessfulBuild,
                        activeServerGeneration = state.activeServerGeneration,
                        pendingServerGeneration = event.expectedGeneration,
                        diagnostics = state.diagnostics,
                        pendingReload = safest(state.pendingReload, event.level),
                        developmentUrls = state.developmentUrls,
                    ),
                )
            }
        }

    private fun reduceServerReady(
        state: DevelopmentSessionState,
        event: ServerReady,
    ): DevelopmentTransition =
        withCurrentSuccessfulBuild(state, event.buildId) {
            val activeGeneration = state.activeServerGeneration
            if (activeGeneration != null && event.generation <= activeGeneration) {
                return@withCurrentSuccessfulBuild stale(
                    state,
                    DevelopmentTransitionReason.STALE_SERVER_GENERATION,
                )
            }
            val pendingGeneration =
                state.pendingServerGeneration
                    ?: return@withCurrentSuccessfulBuild rejected(
                        state,
                        DevelopmentTransitionReason.SERVER_GENERATION_NOT_REQUESTED,
                    )
            when {
                event.generation < pendingGeneration ->
                    stale(state, DevelopmentTransitionReason.STALE_SERVER_GENERATION)

                event.generation > pendingGeneration ->
                    rejected(state, DevelopmentTransitionReason.SERVER_GENERATION_NOT_REQUESTED)

                else ->
                    applied(
                        DevelopmentSessionState(
                            phase = DevelopmentSessionPhase.READY,
                            latestRequestedBuild = state.latestRequestedBuild,
                            activeBuild = null,
                            activeChanges = emptySet(),
                            latestBuildOutcome = state.latestBuildOutcome,
                            lastSuccessfulBuild = state.lastSuccessfulBuild,
                            activeServerGeneration = event.generation,
                            pendingServerGeneration = null,
                            diagnostics = emptyList(),
                            pendingReload = null,
                            developmentUrls = event.urls.distinct(),
                        ),
                    )
            }
        }

    private fun reduceCssChanged(
        state: DevelopmentSessionState,
        event: CssChanged,
    ): DevelopmentTransition =
        withCurrentSuccessfulBuild(state, event.buildId) {
            requireReload(state, ReloadLevel.HOT_ASSET)
        }

    private fun reduceFrontendChanged(
        state: DevelopmentSessionState,
        event: FrontendChanged,
    ): DevelopmentTransition =
        withCurrentSuccessfulBuild(state, event.buildId) {
            requireReload(state, ReloadLevel.HOT_FRONTEND_MODULE)
        }

    private fun reduceReloadRequired(
        state: DevelopmentSessionState,
        event: ReloadRequired,
    ): DevelopmentTransition =
        withCurrentSuccessfulBuild(state, event.buildId) {
            requireReload(state, event.minimumLevel)
        }

    private fun reduceReloadApplied(
        state: DevelopmentSessionState,
        event: ReloadApplied,
    ): DevelopmentTransition =
        withCurrentSuccessfulBuild(state, event.buildId) {
            if (state.activeServerGeneration == null) {
                return@withCurrentSuccessfulBuild rejected(state, DevelopmentTransitionReason.SERVER_NOT_READY)
            }
            val pendingReload =
                state.pendingReload
                    ?: return@withCurrentSuccessfulBuild rejected(state, DevelopmentTransitionReason.NO_RELOAD_PENDING)
            if (!event.level.satisfies(pendingReload)) {
                return@withCurrentSuccessfulBuild rejected(
                    state,
                    DevelopmentTransitionReason.RELOAD_LEVEL_TOO_WEAK,
                )
            }
            applied(
                DevelopmentSessionState(
                    phase = DevelopmentSessionPhase.READY,
                    latestRequestedBuild = state.latestRequestedBuild,
                    activeBuild = null,
                    activeChanges = emptySet(),
                    latestBuildOutcome = state.latestBuildOutcome,
                    lastSuccessfulBuild = state.lastSuccessfulBuild,
                    activeServerGeneration = state.activeServerGeneration,
                    pendingServerGeneration = null,
                    diagnostics = emptyList(),
                    pendingReload = null,
                    developmentUrls = state.developmentUrls,
                ),
            )
        }

    private fun reduceStopped(state: DevelopmentSessionState): DevelopmentTransition =
        applied(
            DevelopmentSessionState(
                phase = DevelopmentSessionPhase.STOPPED,
                latestRequestedBuild = state.latestRequestedBuild,
                activeBuild = null,
                activeChanges = emptySet(),
                latestBuildOutcome = state.latestBuildOutcome,
                lastSuccessfulBuild = state.lastSuccessfulBuild,
                activeServerGeneration = null,
                pendingServerGeneration = null,
                diagnostics = emptyList(),
                pendingReload = null,
                developmentUrls = emptyList(),
            ),
        )

    private fun requireReload(
        state: DevelopmentSessionState,
        level: ReloadLevel,
    ): DevelopmentTransition =
        applied(
            DevelopmentSessionState(
                phase =
                    if (state.pendingServerGeneration == null) {
                        DevelopmentSessionPhase.RELOAD_PENDING
                    } else {
                        DevelopmentSessionPhase.SERVER_RESTARTING
                    },
                latestRequestedBuild = state.latestRequestedBuild,
                activeBuild = null,
                activeChanges = emptySet(),
                latestBuildOutcome = state.latestBuildOutcome,
                lastSuccessfulBuild = state.lastSuccessfulBuild,
                activeServerGeneration = state.activeServerGeneration,
                pendingServerGeneration = state.pendingServerGeneration,
                diagnostics = state.diagnostics,
                pendingReload = safest(state.pendingReload, level),
                developmentUrls = state.developmentUrls,
            ),
        )

    private fun withActiveBuild(
        state: DevelopmentSessionState,
        buildId: BuildId,
        block: () -> DevelopmentTransition,
    ): DevelopmentTransition {
        val latest =
            state.latestRequestedBuild
                ?: return rejected(state, DevelopmentTransitionReason.BUILD_NOT_REQUESTED)
        return when {
            buildId < latest -> stale(state, DevelopmentTransitionReason.STALE_BUILD)
            buildId > latest -> rejected(state, DevelopmentTransitionReason.BUILD_NOT_REQUESTED)
            state.activeBuild != buildId -> rejected(state, DevelopmentTransitionReason.BUILD_NOT_ACTIVE)
            else -> block()
        }
    }

    private fun withCurrentSuccessfulBuild(
        state: DevelopmentSessionState,
        buildId: BuildId,
        block: () -> DevelopmentTransition,
    ): DevelopmentTransition {
        val latest =
            state.latestRequestedBuild
                ?: return rejected(state, DevelopmentTransitionReason.BUILD_NOT_REQUESTED)
        return when {
            buildId < latest -> stale(state, DevelopmentTransitionReason.STALE_BUILD)
            buildId > latest -> rejected(state, DevelopmentTransitionReason.BUILD_NOT_REQUESTED)
            state.lastSuccessfulBuild != buildId ->
                rejected(state, DevelopmentTransitionReason.BUILD_NOT_SUCCESSFUL)

            else -> block()
        }
    }

    private fun isStaleGeneration(
        expected: ServerGeneration,
        active: ServerGeneration?,
        pending: ServerGeneration?,
    ): Boolean = listOfNotNull(active, pending).any { expected <= it }

    private fun restingPhase(state: DevelopmentSessionState): DevelopmentSessionPhase =
        when {
            state.pendingServerGeneration != null -> DevelopmentSessionPhase.SERVER_RESTARTING
            state.pendingReload != null -> DevelopmentSessionPhase.RELOAD_PENDING
            state.activeServerGeneration != null -> DevelopmentSessionPhase.READY
            else -> DevelopmentSessionPhase.IDLE
        }

    private fun safest(
        current: ReloadLevel?,
        requested: ReloadLevel,
    ): ReloadLevel = current?.let { ReloadLevel.safest(it, requested) } ?: requested

    private fun applied(state: DevelopmentSessionState): DevelopmentTransition =
        DevelopmentTransition(state, DevelopmentEventDisposition.APPLIED, null)

    private fun stale(
        state: DevelopmentSessionState,
        reason: DevelopmentTransitionReason,
    ): DevelopmentTransition = DevelopmentTransition(state, DevelopmentEventDisposition.IGNORED_STALE, reason)

    private fun rejected(
        state: DevelopmentSessionState,
        reason: DevelopmentTransitionReason,
    ): DevelopmentTransition = DevelopmentTransition(state, DevelopmentEventDisposition.REJECTED, reason)
}
