package dev.woge.development

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalWogeDevelopmentApi::class)
class DevelopmentLifecycleTest {
    @Test
    fun `rapid saves coalesce changes and stale success cannot replace the newest build`() {
        val buildOne = BuildId.FIRST
        val buildTwo = BuildId.after(buildOne)
        val kotlinChange = DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE, source("src/main/Page.kt"))
        val cssChange = DevelopmentChange(DevelopmentChangeKind.CSS, source("src/main/site.css"))

        var state = apply(DevelopmentSessionState.initial(), BuildStarted(buildOne, setOf(kotlinChange)))
        state = apply(state, BuildStarted(buildTwo, setOf(cssChange)))
        assertEquals(setOf(kotlinChange, cssChange), state.activeChanges)

        val stale =
            DevelopmentLifecycle.reduce(
                state,
                BuildSucceeded(buildOne, 20.milliseconds, ReloadLevel.SERVER_RESTART),
            )
        assertEquals(DevelopmentEventDisposition.IGNORED_STALE, stale.disposition)
        assertEquals(DevelopmentTransitionReason.STALE_BUILD, stale.reason)
        assertSame(state, stale.state)

        val outOfOrder =
            DevelopmentLifecycle.reduce(
                state,
                BuildSucceeded(BuildId.after(buildTwo), 20.milliseconds, ReloadLevel.SERVER_RESTART),
            )
        assertEquals(DevelopmentEventDisposition.REJECTED, outOfOrder.disposition)
        assertEquals(DevelopmentTransitionReason.BUILD_NOT_REQUESTED, outOfOrder.reason)
        assertSame(state, outOfOrder.state)

        state = apply(state, BuildSucceeded(buildTwo, 30.milliseconds, ReloadLevel.HOT_ASSET))
        assertEquals(buildTwo, state.lastSuccessfulBuild)
        assertEquals(ReloadLevel.HOT_ASSET, state.pendingReload)
        assertEquals(DevelopmentSessionPhase.RELOAD_PENDING, state.phase)
    }

    @Test
    fun `a failed replacement build keeps the last ready application and structured diagnostics`() {
        val buildOne = BuildId.FIRST
        val buildTwo = BuildId.after(buildOne)
        var state = readyState(buildOne, ServerGeneration.FIRST)

        state = apply(state, BuildStarted(buildTwo, setOf(DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE))))
        val diagnostic = compileDiagnostic()
        state = apply(state, BuildFailed(buildTwo, 50.milliseconds, listOf(diagnostic)))

        assertEquals(DevelopmentSessionPhase.BUILD_FAILED, state.phase)
        assertEquals(buildOne, state.lastSuccessfulBuild)
        assertEquals(ServerGeneration.FIRST, state.activeServerGeneration)
        assertEquals(listOf(diagnostic), state.diagnostics)
        assertTrue(state.hasLastValidApplication)
        assertEquals(listOf(localUrl()), state.developmentUrls)

        val staleReady =
            DevelopmentLifecycle.reduce(
                state,
                ServerReady(buildOne, ServerGeneration.after(ServerGeneration.FIRST), listOf(localUrl())),
            )
        assertEquals(DevelopmentEventDisposition.IGNORED_STALE, staleReady.disposition)
        assertSame(state, staleReady.state)
    }

    @Test
    fun `cancellation retains the previous application and late completion is rejected`() {
        val buildOne = BuildId.FIRST
        val buildTwo = BuildId.after(buildOne)
        var state = readyState(buildOne, ServerGeneration.FIRST)
        state = apply(state, BuildStarted(buildTwo, emptySet()))
        state = apply(state, BuildCancelled(buildTwo, BuildCancellationReason.REQUESTED))

        assertEquals(DevelopmentSessionPhase.READY, state.phase)
        assertTrue(state.hasLastValidApplication)
        assertEquals(buildTwo, state.latestRequestedBuild)

        val lateSuccess =
            DevelopmentLifecycle.reduce(
                state,
                BuildSucceeded(buildTwo, 10.milliseconds, ReloadLevel.SERVER_RESTART),
            )
        assertEquals(DevelopmentEventDisposition.REJECTED, lateSuccess.disposition)
        assertEquals(DevelopmentTransitionReason.BUILD_NOT_ACTIVE, lateSuccess.reason)
        assertSame(state, lateSuccess.state)
    }

    @Test
    fun `restart storms keep only the newest requested generation`() {
        val build = BuildId.FIRST
        var state = successfulBuildState(build, ReloadLevel.SERVER_RESTART)
        val generationOne = ServerGeneration.FIRST
        val generationTwo = ServerGeneration.after(generationOne)
        val generationThree = ServerGeneration.after(generationTwo)

        state = apply(state, ServerRestarting(build, generationOne, ReloadLevel.SERVER_RESTART))
        state = apply(state, ServerRestarting(build, generationTwo, ReloadLevel.SERVER_RESTART))
        assertEquals(generationTwo, state.pendingServerGeneration)

        val stale = DevelopmentLifecycle.reduce(state, ServerReady(build, generationOne, listOf(localUrl())))
        assertEquals(DevelopmentEventDisposition.IGNORED_STALE, stale.disposition)
        assertSame(state, stale.state)

        val unrequested = DevelopmentLifecycle.reduce(state, ServerReady(build, generationThree, listOf(localUrl())))
        assertEquals(DevelopmentEventDisposition.REJECTED, unrequested.disposition)
        assertEquals(DevelopmentTransitionReason.SERVER_GENERATION_NOT_REQUESTED, unrequested.reason)
        assertSame(state, unrequested.state)

        state = apply(state, ServerReady(build, generationTwo, listOf(localUrl())))
        assertEquals(DevelopmentSessionPhase.READY, state.phase)
        assertEquals(generationTwo, state.activeServerGeneration)
        assertNull(state.pendingServerGeneration)
    }

    @Test
    fun `semantic frontend events escalate but never weaken a pending reload`() {
        val build = BuildId.FIRST
        var state = readyState(build, ServerGeneration.FIRST)

        state = apply(state, CssChanged(build, setOf(source("src/main/site.css"))))
        assertEquals(ReloadLevel.HOT_ASSET, state.pendingReload)
        state = apply(state, FrontendChanged(build, setOf(source("src/main/app.js"))))
        assertEquals(ReloadLevel.HOT_FRONTEND_MODULE, state.pendingReload)
        state = apply(state, ReloadRequired(build, ReloadLevel.DOCUMENT_REFRESH))
        assertEquals(ReloadLevel.DOCUMENT_REFRESH, state.pendingReload)

        val tooWeak = DevelopmentLifecycle.reduce(state, ReloadApplied(build, ReloadLevel.HOT_FRONTEND_MODULE))
        assertEquals(DevelopmentEventDisposition.REJECTED, tooWeak.disposition)
        assertEquals(DevelopmentTransitionReason.RELOAD_LEVEL_TOO_WEAK, tooWeak.reason)
        assertSame(state, tooWeak.state)

        state = apply(state, ReloadApplied(build, ReloadLevel.DOCUMENT_REFRESH))
        assertEquals(DevelopmentSessionPhase.READY, state.phase)
        assertNull(state.pendingReload)
    }

    @Test
    fun `asset events do not supersede a restart already in progress`() {
        val build = BuildId.FIRST
        val generation = ServerGeneration.FIRST
        var state = successfulBuildState(build, ReloadLevel.SERVER_RESTART)
        state = apply(state, ServerRestarting(build, generation, ReloadLevel.SERVER_RESTART))
        state = apply(state, CssChanged(build, setOf(source("src/main/site.css"))))

        assertEquals(DevelopmentSessionPhase.SERVER_RESTARTING, state.phase)
        assertEquals(generation, state.pendingServerGeneration)
        assertEquals(ReloadLevel.SERVER_RESTART, state.pendingReload)
    }

    @Test
    fun `stopped sessions release live endpoints and reject later events`() {
        val build = BuildId.FIRST
        var state = readyState(build, ServerGeneration.FIRST)
        state = apply(state, DevelopmentSessionStopped)

        assertEquals(DevelopmentSessionPhase.STOPPED, state.phase)
        assertFalse(state.hasLastValidApplication)
        assertTrue(state.developmentUrls.isEmpty())

        val later = DevelopmentLifecycle.reduce(state, BuildStarted(BuildId.after(build), emptySet()))
        assertEquals(DevelopmentEventDisposition.REJECTED, later.disposition)
        assertEquals(DevelopmentTransitionReason.SESSION_STOPPED, later.reason)
        assertSame(state, later.state)
    }

    private fun successfulBuildState(
        buildId: BuildId,
        reloadLevel: ReloadLevel,
    ): DevelopmentSessionState {
        var state = apply(DevelopmentSessionState.initial(), BuildStarted(buildId, emptySet()))
        state = apply(state, BuildSucceeded(buildId, 25.milliseconds, reloadLevel))
        return state
    }

    private fun readyState(
        buildId: BuildId,
        generation: ServerGeneration,
    ): DevelopmentSessionState {
        var state = successfulBuildState(buildId, ReloadLevel.COLD_RESTART)
        state = apply(state, ServerRestarting(buildId, generation, ReloadLevel.COLD_RESTART))
        state = apply(state, ServerReady(buildId, generation, listOf(localUrl())))
        return state
    }

    private fun apply(
        state: DevelopmentSessionState,
        event: DevelopmentEvent,
    ): DevelopmentSessionState {
        val transition = DevelopmentLifecycle.reduce(state, event)
        assertTrue(
            transition.wasApplied,
            "Expected $event to apply, got ${transition.disposition}: ${transition.reason}",
        )
        return transition.state
    }

    private fun compileDiagnostic(): DevelopmentDiagnostic =
        DevelopmentDiagnostic(
            DevelopmentDiagnosticCode.of("WOGE-KOTLIN-COMPILE"),
            DevelopmentDiagnosticSeverity.ERROR,
            DevelopmentDiagnosticSummary.of("Type mismatch"),
            DevelopmentSourceLocation(source("src/main/Page.kt"), 4, 12),
        )

    private fun source(value: String): DevelopmentSourcePath = DevelopmentSourcePath.of(value)

    private fun localUrl(): DevelopmentUrl = DevelopmentUrl.local("http://localhost:8080/")
}
