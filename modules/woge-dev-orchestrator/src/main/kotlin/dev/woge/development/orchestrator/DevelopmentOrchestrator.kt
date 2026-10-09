package dev.woge.development.orchestrator

import dev.woge.development.AwaitBuildRequest
import dev.woge.development.AwaitBuildResult
import dev.woge.development.BuildId
import dev.woge.development.DevelopmentApplicationManifest
import dev.woge.development.DevelopmentBuildTerminalEvent
import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentCommandResult
import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentReloadCommand
import dev.woge.development.DevelopmentRestartCommand
import dev.woge.development.DevelopmentSessionPhase
import dev.woge.development.DevelopmentSessionState
import dev.woge.development.DevelopmentUrl
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.WogeDevelopmentCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Woge-owned development session.
 *
 * It turns change reports and adapter results into one ordered lifecycle (ADR 0038, ADR 0041):
 * builds never touch the running application, only a successful build can restart it, and events for
 * an older build or server generation are ignored. The class is internal tooling and must not be
 * exposed to application code or reachable from a network without authorization.
 */
@ExperimentalWogeDevelopmentApi
@Suppress("TooManyFunctions")
public class DevelopmentOrchestrator private constructor(
    private val coordinator: SessionCoordinator,
    private val adapters: DevelopmentAdapters,
) : WogeDevelopmentCapabilities {
    /** Current lifecycle snapshot. */
    public val state: StateFlow<DevelopmentSessionState> get() = coordinator.state

    /** Applied lifecycle events in order. Browser and tool channels subscribe here. */
    public val events: SharedFlow<DevelopmentEventRecord> get() = coordinator.events

    /** Reports edited inputs. Never blocks; rapid reports are coalesced into the newest build. */
    public fun reportChange(vararg changes: DevelopmentChange) {
        coordinator.reportChanges(changes.asList())
    }

    public fun reportChanges(changes: Collection<DevelopmentChange>) {
        coordinator.reportChanges(changes)
    }

    /** Cancels running work, stops the application child and ends the session. Safe to call repeatedly. */
    public suspend fun stop() {
        coordinator.stop()
    }

    override suspend fun status(): DevelopmentSessionState = state.value

    override suspend fun awaitBuild(request: AwaitBuildRequest): AwaitBuildResult {
        val observed =
            withTimeoutOrNull(request.timeout) {
                state.first {
                    outcomeAfter(it, request.afterBuild) != null ||
                        it.phase == DevelopmentSessionPhase.STOPPED
                }
            }
        val outcome = observed?.let { outcomeAfter(it, request.afterBuild) }
        return when {
            outcome != null -> AwaitBuildResult.Observed(outcome)
            observed != null -> AwaitBuildResult.SessionStopped
            else -> AwaitBuildResult.TimedOut(state.value.latestRequestedBuild)
        }
    }

    override suspend fun diagnostics(): List<DevelopmentDiagnostic> = state.value.diagnostics

    override suspend fun reload(command: DevelopmentReloadCommand): DevelopmentCommandResult =
        coordinator.submit(command.buildId, command.level)

    override suspend fun restart(command: DevelopmentRestartCommand): DevelopmentCommandResult =
        coordinator.submit(command.buildId, command.level)

    /** Returns the manifest only while it describes the last successful build. */
    override suspend fun applicationManifest(): DevelopmentApplicationManifest? {
        val build = state.value.lastSuccessfulBuild ?: return null
        return adapters.manifest.manifest(build)?.takeIf { it.buildId == build }
    }

    override suspend fun developmentUrls(): List<DevelopmentUrl> = state.value.developmentUrls

    private fun outcomeAfter(
        state: DevelopmentSessionState,
        cursor: BuildId?,
    ): DevelopmentBuildTerminalEvent? = state.latestBuildOutcome?.takeIf { cursor == null || it.buildId > cursor }

    public companion object {
        /**
         * Starts a session whose work runs in a child of [scope]. Cancelling the scope stops the
         * session and the application child. No build runs until the first change is reported.
         */
        public fun start(
            scope: CoroutineScope,
            adapters: DevelopmentAdapters,
            options: DevelopmentOrchestratorOptions = DevelopmentOrchestratorOptions(),
        ): DevelopmentOrchestrator = DevelopmentOrchestrator(SessionCoordinator(scope, adapters, options), adapters)
    }
}
