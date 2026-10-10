package dev.woge.development.mcp

import dev.woge.development.AwaitBuildRequest
import dev.woge.development.AwaitBuildResult
import dev.woge.development.BuildFailed
import dev.woge.development.BuildId
import dev.woge.development.BuildStarted
import dev.woge.development.BuildSucceeded
import dev.woge.development.DevelopmentApplicationManifest
import dev.woge.development.DevelopmentCommandRefusal
import dev.woge.development.DevelopmentCommandResult
import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentDiagnosticCode
import dev.woge.development.DevelopmentDiagnosticSeverity
import dev.woge.development.DevelopmentDiagnosticSummary
import dev.woge.development.DevelopmentEvent
import dev.woge.development.DevelopmentLifecycle
import dev.woge.development.DevelopmentReloadCommand
import dev.woge.development.DevelopmentRestartCommand
import dev.woge.development.DevelopmentSessionState
import dev.woge.development.DevelopmentSourceLocation
import dev.woge.development.DevelopmentSourcePath
import dev.woge.development.DevelopmentUrl
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import dev.woge.development.ServerGeneration
import dev.woge.development.ServerReady
import dev.woge.development.ServerRestarting
import dev.woge.development.WogeDevelopmentCapabilities
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.milliseconds

/** In-memory capabilities driven by the real lifecycle reducer. */
@OptIn(ExperimentalWogeDevelopmentApi::class)
internal class FakeCapabilities : WogeDevelopmentCapabilities {
    @Volatile var state: DevelopmentSessionState = DevelopmentSessionState.initial()
    var manifest: DevelopmentApplicationManifest? = null
    val commands = mutableListOf<Pair<BuildId, ReloadLevel>>()

    fun apply(vararg events: DevelopmentEvent) {
        events.forEach { state = DevelopmentLifecycle.reduce(state, it).state }
    }

    fun buildAndServe(
        build: Long,
        generation: Long,
    ) {
        val id = BuildId.of(build)
        apply(
            BuildStarted(id, emptySet()),
            BuildSucceeded(id, 10.milliseconds, ReloadLevel.SERVER_RESTART),
            ServerRestarting(id, ServerGeneration.of(generation), ReloadLevel.SERVER_RESTART),
            ServerReady(id, ServerGeneration.of(generation), listOf(DevelopmentUrl.local("http://127.0.0.1:8080/"))),
        )
    }

    fun fail(build: Long) {
        val id = BuildId.of(build)
        apply(BuildStarted(id, emptySet()), BuildFailed(id, 5.milliseconds, listOf(compileError)))
    }

    override suspend fun status(): DevelopmentSessionState = state

    override suspend fun awaitBuild(request: AwaitBuildRequest): AwaitBuildResult {
        val outcome = state.latestBuildOutcome
        return if (outcome != null && (request.afterBuild == null || outcome.buildId > request.afterBuild!!)) {
            AwaitBuildResult.Observed(outcome)
        } else {
            AwaitBuildResult.TimedOut(state.latestRequestedBuild)
        }
    }

    override suspend fun diagnostics(): List<DevelopmentDiagnostic> = state.diagnostics

    override suspend fun reload(command: DevelopmentReloadCommand): DevelopmentCommandResult =
        command(command.buildId, command.level)

    override suspend fun restart(command: DevelopmentRestartCommand): DevelopmentCommandResult =
        command(command.buildId, command.level)

    override suspend fun applicationManifest(): DevelopmentApplicationManifest? = manifest

    override suspend fun developmentUrls(): List<DevelopmentUrl> = state.developmentUrls

    private fun command(
        build: BuildId,
        level: ReloadLevel,
    ): DevelopmentCommandResult {
        commands += build to level
        return if (build == state.lastSuccessfulBuild) {
            DevelopmentCommandResult.Accepted(build, level)
        } else {
            DevelopmentCommandResult.Refused(DevelopmentCommandRefusal.STALE_BUILD)
        }
    }

    companion object {
        val compileError =
            DevelopmentDiagnostic(
                DevelopmentDiagnosticCode.of("KOTLIN-COMPILE"),
                DevelopmentDiagnosticSeverity.ERROR,
                DevelopmentDiagnosticSummary.of("Unresolved reference 'tittle'."),
                DevelopmentSourceLocation(DevelopmentSourcePath.of("src/main/kotlin/HomePage.kt"), 12, 9),
            )

        fun manifest(build: Long): JsonDevelopmentManifest =
            JsonDevelopmentManifest(1, BuildId.of(build), JsonObject(emptyMap()))
    }
}
