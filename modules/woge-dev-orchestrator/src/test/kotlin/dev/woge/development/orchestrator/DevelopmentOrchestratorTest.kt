package dev.woge.development.orchestrator

import dev.woge.development.AwaitBuildRequest
import dev.woge.development.AwaitBuildResult
import dev.woge.development.BuildCancellationReason
import dev.woge.development.BuildCancelled
import dev.woge.development.BuildFailed
import dev.woge.development.BuildId
import dev.woge.development.BuildStarted
import dev.woge.development.BuildSucceeded
import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.DevelopmentCommandRefusal
import dev.woge.development.DevelopmentCommandResult
import dev.woge.development.DevelopmentReloadCommand
import dev.woge.development.DevelopmentRestartCommand
import dev.woge.development.DevelopmentSessionPhase
import dev.woge.development.DevelopmentSessionStopped
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadApplied
import dev.woge.development.ReloadLevel
import dev.woge.development.ServerExited
import dev.woge.development.ServerGeneration
import dev.woge.development.ServerReady
import dev.woge.development.ServerRestartFailed
import dev.woge.development.ServerRestarting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalWogeDevelopmentApi::class, ExperimentalCoroutinesApi::class)
class DevelopmentOrchestratorTest {
    @Test
    fun `an unexpected child exit clears readiness and the next build uses a cold restart`() =
        runTest {
            val harness = harness()
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()
            harness.host.exits.emit(ServerExited(ServerGeneration.FIRST, listOf(Harness.diagnostic())))
            harness.settle()
            assertEquals(DevelopmentSessionPhase.SERVER_FAILED, harness.orchestrator.state.value.phase)
            assertTrue(harness.orchestrator.developmentUrls().isEmpty())
            harness.orchestrator.reportChange(Harness.cssChange())
            harness.settle()
            assertEquals(
                ReloadLevel.COLD_RESTART,
                harness.host.requests
                    .last()
                    .level,
            )
            harness.host.exits.emit(ServerExited(ServerGeneration.FIRST, listOf(Harness.diagnostic())))
            harness.settle()
            assertEquals(DevelopmentSessionPhase.READY, harness.orchestrator.state.value.phase)
            harness.orchestrator.stop()
        }

    private fun TestScope.harness(
        quietPeriod: kotlin.time.Duration = kotlin.time.Duration.ZERO,
        frontend: suspend (BuildId, ReloadLevel) -> Boolean = { _, level -> level == ReloadLevel.DOCUMENT_REFRESH },
    ): Harness = Harness(CoroutineScope(coroutineContext + Job()), this, quietPeriod, frontend)

    @Test
    fun `the first change builds then starts the host and advances build and generation`() =
        runTest {
            val harness = harness()

            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            val state = harness.orchestrator.state.value
            assertEquals(DevelopmentSessionPhase.READY, state.phase)
            assertEquals(BuildId.FIRST, state.lastSuccessfulBuild)
            assertEquals(ServerGeneration.FIRST, state.activeServerGeneration)
            assertEquals(listOf(Harness.localUrl()), harness.orchestrator.developmentUrls())
            assertEquals(
                listOf(
                    BuildStarted::class,
                    BuildSucceeded::class,
                    ServerRestarting::class,
                    ServerReady::class,
                ),
                harness.events.map { it::class },
            )
            assertEquals(
                ReloadLevel.SERVER_RESTART,
                harness.host.requests
                    .single()
                    .level,
            )
        }

    @Test
    fun `rapid edits supersede the running build and the newest build carries every change`() =
        runTest {
            val harness = harness()
            val release = Harness.gate()
            harness.builds.script = { request ->
                if (request.buildId == BuildId.FIRST) awaitCancellation()
                release.await()
                DevelopmentBuildResult.Succeeded()
            }

            harness.orchestrator.reportChange(Harness.kotlinChange("src/main/A.kt"))
            runCurrent()
            harness.orchestrator.reportChange(Harness.kotlinChange("src/main/B.kt"))
            runCurrent()
            harness.orchestrator.reportChange(Harness.cssChange())
            runCurrent()
            release.complete(Unit)
            harness.settle()

            val latest = harness.builds.requests.last()
            assertEquals(3, harness.builds.requests.size)
            assertEquals(
                setOf(
                    Harness.kotlinChange("src/main/A.kt"),
                    Harness.kotlinChange("src/main/B.kt"),
                    Harness.cssChange(),
                ),
                latest.changes,
            )
            assertEquals(listOf(BuildId.FIRST, BuildId.of(2)), harness.builds.cancelled)
            val state = harness.orchestrator.state.value
            assertEquals(BuildId.of(3), state.lastSuccessfulBuild)
            assertEquals(1, harness.host.requests.size)
        }

    @Test
    fun `a late success from a cancelled build that ignores cancellation is never current`() =
        runTest {
            val harness = harness()
            val staleRelease = Harness.gate()
            harness.builds.script = { request ->
                if (request.buildId == BuildId.FIRST) {
                    withContext(NonCancellable) { staleRelease.await() }
                }
                DevelopmentBuildResult.Succeeded()
            }

            harness.orchestrator.reportChange(Harness.kotlinChange())
            runCurrent()
            harness.orchestrator.reportChange(Harness.kotlinChange("src/main/B.kt"))
            runCurrent()
            staleRelease.complete(Unit)
            harness.settle()

            assertEquals(BuildId.of(2), harness.orchestrator.state.value.lastSuccessfulBuild)
            assertEquals(1, harness.host.requests.size)
            assertEquals(
                BuildId.of(2),
                harness.host.requests
                    .single()
                    .buildId,
            )
        }

    @Test
    fun `a failed build keeps the last valid application and exposes structured diagnostics`() =
        runTest {
            val harness = harness()
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            harness.builds.script = { DevelopmentBuildResult.Failed(listOf(Harness.diagnostic())) }
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            val state = harness.orchestrator.state.value
            assertEquals(DevelopmentSessionPhase.BUILD_FAILED, state.phase)
            assertEquals(BuildId.FIRST, state.lastSuccessfulBuild)
            assertEquals(ServerGeneration.FIRST, state.activeServerGeneration)
            assertTrue(state.hasLastValidApplication)
            assertEquals(listOf(Harness.diagnostic()), harness.orchestrator.diagnostics())
            assertEquals(1, harness.host.requests.size)
        }

    @Test
    fun `a fix after a failed build restarts for the failed kotlin edit even when only css changed`() =
        runTest {
            val harness = harness()
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()
            harness.builds.script = { DevelopmentBuildResult.Failed(listOf(Harness.diagnostic())) }
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            harness.builds.script = { DevelopmentBuildResult.Succeeded(ReloadLevel.HOT_ASSET) }
            harness.orchestrator.reportChange(Harness.cssChange())
            harness.settle()

            assertEquals(2, harness.host.requests.size)
            assertEquals(ServerGeneration.of(2), harness.orchestrator.state.value.activeServerGeneration)
            assertEquals(DevelopmentSessionPhase.READY, harness.orchestrator.state.value.phase)
        }

    @Test
    fun `css changes use the hot level and fall back to a document refresh when it is unsupported`() =
        runTest {
            val harness = harness()
            harness.builds.script = { DevelopmentBuildResult.Succeeded(ReloadLevel.HOT_ASSET) }
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            harness.orchestrator.reportChange(Harness.cssChange())
            harness.settle()

            assertEquals(
                listOf(
                    BuildId.of(2) to ReloadLevel.HOT_ASSET,
                    BuildId.of(2) to ReloadLevel.HOT_FRONTEND_MODULE,
                    BuildId.of(2) to ReloadLevel.DOCUMENT_REFRESH,
                ),
                harness.frontendCalls,
            )
            val applied = harness.events.filterIsInstance<ReloadApplied>().single()
            assertEquals(ReloadLevel.DOCUMENT_REFRESH, applied.level)
            assertEquals(1, harness.host.requests.size)
        }

    @Test
    fun `a supported hot level is applied without a server restart`() =
        runTest {
            val harness = harness(frontend = { _, level -> level == ReloadLevel.HOT_ASSET })
            harness.builds.script = { DevelopmentBuildResult.Succeeded(ReloadLevel.HOT_ASSET) }
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            harness.orchestrator.reportChange(Harness.cssChange())
            harness.settle()

            assertEquals(
                ReloadLevel.HOT_ASSET,
                harness.events
                    .filterIsInstance<ReloadApplied>()
                    .single()
                    .level,
            )
            assertEquals(1, harness.host.requests.size)
        }

    @Test
    fun `a failed or unsupported server restart escalates to a cold restart`() =
        runTest {
            val harness = harness()
            harness.host.script = { request ->
                when {
                    request.generation == ServerGeneration.FIRST ->
                        DevelopmentHostRestartResult.Ready(
                            listOf(Harness.localUrl()),
                        )
                    request.level == ReloadLevel.SERVER_RESTART ->
                        DevelopmentHostRestartResult.Failed(
                            listOf(Harness.diagnostic()),
                            previousApplicationRetained = true,
                        )
                    else -> DevelopmentHostRestartResult.Ready(listOf(Harness.localUrl()))
                }
            }
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            assertEquals(
                listOf(ReloadLevel.SERVER_RESTART, ReloadLevel.SERVER_RESTART, ReloadLevel.COLD_RESTART),
                harness.host.requests.map { it.level },
            )
            assertEquals(
                listOf(1L, 2L, 3L),
                harness.host.requests.map { it.generation.value },
            )
            assertEquals(ServerGeneration.of(3), harness.orchestrator.state.value.activeServerGeneration)
        }

    @Test
    fun `a cold restart failure publishes diagnostics and the next success restarts cold`() =
        runTest {
            val harness = harness()
            harness.host.script = { DevelopmentHostRestartResult.Failed(listOf(Harness.diagnostic()), false) }
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            var state = harness.orchestrator.state.value
            assertEquals(DevelopmentSessionPhase.SERVER_FAILED, state.phase)
            assertFalse(state.hasLastValidApplication)
            assertEquals(listOf(Harness.diagnostic()), state.diagnostics)
            assertInstanceOf(ServerRestartFailed::class.java, harness.events.last())

            harness.host.script = { DevelopmentHostRestartResult.Ready(listOf(Harness.localUrl())) }
            harness.builds.script = { DevelopmentBuildResult.Succeeded(ReloadLevel.HOT_ASSET) }
            harness.orchestrator.reportChange(Harness.cssChange())
            harness.settle()

            state = harness.orchestrator.state.value
            assertEquals(DevelopmentSessionPhase.READY, state.phase)
            assertEquals(
                ReloadLevel.COLD_RESTART,
                harness.host.requests
                    .last()
                    .level,
            )
        }

    @Test
    fun `edits during a restart wait for it and then start exactly one newest build`() =
        runTest {
            val harness = harness()
            val restartGate = CompletableDeferred<Unit>()
            harness.host.script = {
                restartGate.await()
                DevelopmentHostRestartResult.Ready(listOf(Harness.localUrl()))
            }
            harness.orchestrator.reportChange(Harness.kotlinChange("src/main/A.kt"))
            runCurrent()
            repeat(20) { index -> harness.orchestrator.reportChange(Harness.kotlinChange("src/main/Storm$index.kt")) }
            runCurrent()
            assertEquals(1, harness.builds.requests.size)
            assertEquals(DevelopmentSessionPhase.SERVER_RESTARTING, harness.orchestrator.state.value.phase)

            restartGate.complete(Unit)
            harness.settle()

            assertEquals(2, harness.builds.requests.size)
            assertEquals(
                20,
                harness.builds.requests
                    .last()
                    .changes.size,
            )
            assertTrue(harness.builds.cancelled.isEmpty())
            val generations = harness.host.requests.map { it.generation }
            assertEquals(generations.sorted(), generations)
            assertEquals(generations.distinct(), generations)
            assertEquals(BuildId.of(2), harness.orchestrator.state.value.lastSuccessfulBuild)
        }

    @Test
    fun `quiet period collects rapid edits into one build`() =
        runTest {
            val harness = harness(quietPeriod = 100.milliseconds)

            repeat(5) { index ->
                harness.orchestrator.reportChange(Harness.kotlinChange("src/main/File$index.kt"))
                testScheduler.advanceTimeBy(10.milliseconds)
                runCurrent()
            }
            assertTrue(harness.builds.requests.isEmpty())
            harness.settle()

            assertEquals(1, harness.builds.requests.size)
            assertEquals(
                5,
                harness.builds.requests
                    .single()
                    .changes.size,
            )
        }

    @Test
    fun `await build returns the next outcome or times out`() =
        runTest {
            val harness = harness()
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            val observed = harness.orchestrator.awaitBuild(AwaitBuildRequest(null, 1.seconds))
            assertEquals(
                BuildId.FIRST,
                assertInstanceOf(AwaitBuildResult.Observed::class.java, observed).outcome.buildId,
            )

            val timedOut = harness.orchestrator.awaitBuild(AwaitBuildRequest(BuildId.FIRST, 50.milliseconds))
            assertEquals(
                BuildId.FIRST,
                assertInstanceOf(AwaitBuildResult.TimedOut::class.java, timedOut).latestRequestedBuild,
            )

            val waiting = launch { harness.orchestrator.awaitBuild(AwaitBuildRequest(BuildId.FIRST, 5.seconds)) }
            harness.orchestrator.reportChange(Harness.cssChange())
            harness.settle()
            waiting.join()
            assertEquals(BuildId.of(2), harness.orchestrator.state.value.lastSuccessfulBuild)
        }

    @Test
    fun `reload and restart commands reject stale builds and run the requested level`() =
        runTest {
            val harness = harness(frontend = { _, level -> level == ReloadLevel.HOT_ASSET })
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            val stale = harness.orchestrator.reload(DevelopmentReloadCommand(BuildId.of(9), ReloadLevel.HOT_ASSET))
            assertEquals(DevelopmentCommandResult.Refused(DevelopmentCommandRefusal.STALE_BUILD), stale)

            val reload = harness.orchestrator.reload(DevelopmentReloadCommand(BuildId.FIRST, ReloadLevel.HOT_ASSET))
            assertEquals(DevelopmentCommandResult.Accepted(BuildId.FIRST, ReloadLevel.HOT_ASSET), reload)
            harness.settle()
            assertEquals(
                ReloadLevel.HOT_ASSET,
                harness.events
                    .filterIsInstance<ReloadApplied>()
                    .last()
                    .level,
            )

            val restart =
                harness.orchestrator.restart(
                    DevelopmentRestartCommand(BuildId.FIRST, ReloadLevel.COLD_RESTART),
                )
            assertEquals(DevelopmentCommandResult.Accepted(BuildId.FIRST, ReloadLevel.COLD_RESTART), restart)
            harness.settle()
            assertEquals(
                ReloadLevel.COLD_RESTART,
                harness.host.requests
                    .last()
                    .level,
            )
            assertEquals(ServerGeneration.of(2), harness.orchestrator.state.value.activeServerGeneration)
        }

    @Test
    fun `a command during a restart is queued behind it and an edit wins over the queued command`() =
        runTest {
            val harness = harness()
            val restartGate = CompletableDeferred<Unit>()
            harness.host.script = {
                restartGate.await()
                DevelopmentHostRestartResult.Ready(listOf(Harness.localUrl()))
            }
            harness.orchestrator.reportChange(Harness.kotlinChange())
            runCurrent()
            val accepted =
                launch {
                    harness.orchestrator.restart(
                        DevelopmentRestartCommand(BuildId.FIRST, ReloadLevel.COLD_RESTART),
                    )
                }
            runCurrent()
            restartGate.complete(Unit)
            harness.settle()
            accepted.join()

            assertEquals(
                listOf(ReloadLevel.SERVER_RESTART, ReloadLevel.COLD_RESTART),
                harness.host.requests.map { it.level },
            )
        }

    @Test
    fun `adapter exceptions become redacted diagnostics and reach only the internal listener`() =
        runTest {
            val harness = harness()
            harness.builds.script = { error("secret /Users/someone/project token=abc") }

            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            val state = harness.orchestrator.state.value
            assertEquals(DevelopmentSessionPhase.BUILD_FAILED, state.phase)
            assertEquals(
                "BUILD-ADAPTER-FAILURE",
                state.diagnostics
                    .single()
                    .code.value,
            )
            assertFalse(state.diagnostics.toString().contains("secret"))
            assertEquals(1, harness.internalErrors.size)
        }

    @Test
    fun `stop cancels the running build stops the host once and refuses later commands`() =
        runTest {
            val harness = harness()
            harness.builds.script = { awaitCancellation() }
            harness.orchestrator.reportChange(Harness.kotlinChange())
            runCurrent()

            harness.orchestrator.stop()
            harness.orchestrator.stop()

            val state = harness.orchestrator.state.value
            assertEquals(DevelopmentSessionPhase.STOPPED, state.phase)
            assertEquals(1, harness.host.shutdowns)
            assertEquals(listOf(BuildId.FIRST), harness.builds.cancelled)
            assertEquals(BuildCancellationReason.SESSION_STOPPING, (state.latestBuildOutcome as BuildCancelled).reason)
            assertEquals(DevelopmentSessionStopped, harness.events.last())
            assertTrue(state.developmentUrls.isEmpty())
            assertEquals(
                DevelopmentCommandResult.Refused(DevelopmentCommandRefusal.SESSION_STOPPED),
                harness.orchestrator.restart(DevelopmentRestartCommand(BuildId.FIRST, ReloadLevel.COLD_RESTART)),
            )
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()
            assertEquals(1, harness.builds.requests.size)
            assertEquals(
                AwaitBuildResult.Observed(state.latestBuildOutcome as BuildCancelled),
                harness.orchestrator.awaitBuild(AwaitBuildRequest(null, 1.seconds)),
            )
        }

    @Test
    fun `stop during a restart cancels it and shuts the child down`() =
        runTest {
            val harness = harness()
            harness.host.script = { awaitCancellation() }
            harness.orchestrator.reportChange(Harness.kotlinChange())
            runCurrent()

            harness.orchestrator.stop()

            assertEquals(DevelopmentSessionPhase.STOPPED, harness.orchestrator.state.value.phase)
            assertEquals(1, harness.host.shutdowns)
        }

    @Test
    fun `cancelling the owning scope still stops the application child`() =
        runTest {
            val owner = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
            val harness = Harness(owner, this)
            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            owner.cancel()
            harness.settle()

            assertEquals(1, harness.host.shutdowns)
            assertEquals(DevelopmentSessionPhase.STOPPED, harness.orchestrator.state.value.phase)
        }

    @Test
    fun `build configuration changes require a cold restart and unknown changes a server restart`() =
        runTest {
            val harness = harness()
            harness.orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.UNKNOWN))
            harness.settle()
            harness.orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.BUILD_CONFIGURATION))
            harness.settle()

            assertEquals(
                listOf(ReloadLevel.SERVER_RESTART, ReloadLevel.COLD_RESTART),
                harness.host.requests.map { it.level },
            )
        }

    @Test
    fun `the manifest is only exposed for the last successful build`() =
        runTest {
            val harness = harness()
            assertNull(harness.orchestrator.applicationManifest())
        }

    @Test
    fun `failed builds publish a failed event without touching the host`() =
        runTest {
            val harness = harness()
            harness.builds.script = { DevelopmentBuildResult.Failed(listOf(Harness.diagnostic())) }

            harness.orchestrator.reportChange(Harness.kotlinChange())
            harness.settle()

            assertTrue(harness.host.requests.isEmpty())
            assertEquals(listOf(BuildStarted::class, BuildFailed::class), harness.events.map { it::class })
        }
}
