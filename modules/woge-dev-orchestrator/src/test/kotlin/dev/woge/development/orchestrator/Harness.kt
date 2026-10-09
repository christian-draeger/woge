package dev.woge.development.orchestrator

import dev.woge.development.BuildId
import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentDiagnosticCode
import dev.woge.development.DevelopmentDiagnosticSeverity
import dev.woge.development.DevelopmentDiagnosticSummary
import dev.woge.development.DevelopmentEvent
import dev.woge.development.DevelopmentSourceLocation
import dev.woge.development.DevelopmentSourcePath
import dev.woge.development.DevelopmentUrl
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.testTimeSource
import kotlin.time.Duration

/** Scriptable adapters that record every call so tests can assert order and cancellation. */
@OptIn(ExperimentalWogeDevelopmentApi::class)
internal class Harness(
    scope: CoroutineScope,
    private val testScope: TestScope,
    quietPeriod: Duration = Duration.ZERO,
    frontendHandler: suspend (BuildId, ReloadLevel) -> Boolean = { _, level -> level == ReloadLevel.DOCUMENT_REFRESH },
) {
    val builds = ScriptedBuild()
    val host = ScriptedHost()
    val frontendCalls = mutableListOf<Pair<BuildId, ReloadLevel>>()
    val internalErrors = mutableListOf<Throwable>()
    val orchestrator: DevelopmentOrchestrator =
        DevelopmentOrchestrator.start(
            scope,
            DevelopmentAdapters(
                build = builds,
                host = host,
                frontend =
                    DevelopmentFrontendAdapter { build, level ->
                        frontendCalls += build to level
                        frontendHandler(build, level)
                    },
            ),
            DevelopmentOrchestratorOptions(
                quietPeriod = quietPeriod,
                timeSource = testScope.testTimeSource,
                internalErrorListener = { internalErrors += it },
            ),
        )
    val events: List<DevelopmentEvent> get() = orchestrator.events.replayCache.map { it.event }

    suspend fun settle() {
        testScope.advanceUntilIdle()
    }

    class ScriptedBuild : DevelopmentBuildAdapter {
        val requests = mutableListOf<DevelopmentBuildRequest>()
        val cancelled = mutableListOf<BuildId>()
        var script: suspend (DevelopmentBuildRequest) -> DevelopmentBuildResult = { DevelopmentBuildResult.Succeeded() }

        override suspend fun build(request: DevelopmentBuildRequest): DevelopmentBuildResult {
            requests += request
            try {
                return script(request)
            } catch (cancellation: CancellationException) {
                cancelled += request.buildId
                throw cancellation
            }
        }
    }

    class ScriptedHost : DevelopmentHostAdapter {
        val requests = mutableListOf<DevelopmentHostRestartRequest>()
        var shutdowns = 0
        var script: suspend (DevelopmentHostRestartRequest) -> DevelopmentHostRestartResult = {
            DevelopmentHostRestartResult.Ready(listOf(localUrl()))
        }

        override suspend fun restart(request: DevelopmentHostRestartRequest): DevelopmentHostRestartResult {
            requests += request
            return script(request)
        }

        override suspend fun shutdown() {
            shutdowns++
        }
    }

    companion object {
        fun localUrl(): DevelopmentUrl = DevelopmentUrl.local("http://localhost:8080/")

        fun kotlinChange(path: String = "src/main/Page.kt"): DevelopmentChange =
            DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE, DevelopmentSourcePath.of(path))

        fun cssChange(path: String = "src/main/site.css"): DevelopmentChange =
            DevelopmentChange(DevelopmentChangeKind.CSS, DevelopmentSourcePath.of(path))

        fun diagnostic(summary: String = "Type mismatch"): DevelopmentDiagnostic =
            DevelopmentDiagnostic(
                DevelopmentDiagnosticCode.of("WOGE-KOTLIN-COMPILE"),
                DevelopmentDiagnosticSeverity.ERROR,
                DevelopmentDiagnosticSummary.of(summary),
                DevelopmentSourceLocation(DevelopmentSourcePath.of("src/main/Page.kt"), 4, 12),
            )

        fun gate(): CompletableDeferred<Unit> = CompletableDeferred()
    }
}
