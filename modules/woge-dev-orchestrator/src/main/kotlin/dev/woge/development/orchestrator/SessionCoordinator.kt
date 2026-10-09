package dev.woge.development.orchestrator

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
import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentDiagnosticCode
import dev.woge.development.DevelopmentDiagnosticSeverity
import dev.woge.development.DevelopmentDiagnosticSummary
import dev.woge.development.DevelopmentEvent
import dev.woge.development.DevelopmentLifecycle
import dev.woge.development.DevelopmentSessionPhase
import dev.woge.development.DevelopmentSessionState
import dev.woge.development.DevelopmentSessionStopped
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadApplied
import dev.woge.development.ReloadLevel
import dev.woge.development.ReloadRequired
import dev.woge.development.ServerExited
import dev.woge.development.ServerGeneration
import dev.woge.development.ServerReady
import dev.woge.development.ServerRestartFailed
import dev.woge.development.ServerRestarting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

/**
 * Single-threaded coordinator: every lifecycle decision runs in one coroutine that reads one inbox.
 * Adapter calls run in child jobs and report back through that inbox, so no state is shared.
 */
@OptIn(ExperimentalWogeDevelopmentApi::class)
@Suppress("TooManyFunctions")
internal class SessionCoordinator(
    parent: CoroutineScope,
    private val adapters: DevelopmentAdapters,
    private val options: DevelopmentOrchestratorOptions,
) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val mutableState = MutableStateFlow(DevelopmentSessionState.initial())
    private val mutableEvents =
        MutableSharedFlow<DevelopmentEventRecord>(
            replay = options.eventReplay,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val inbox = Channel<Input>(Channel.UNLIMITED)
    private val changeBox = ChangeBox()
    private val wakePending = AtomicBoolean(false)

    private var work: Work? = null
    private var windowJob: Job? = null
    private var tokenCounter = 0L
    private var sequence = 0L
    private var nextBuild: BuildId = BuildId.FIRST
    private var lastGeneration: ServerGeneration? = null
    private var lastRestartFailed = false
    private var pendingHostExit: ServerExited? = null
    private var followUp: ReloadLevel? = null
    private var stopRequested = false
    private var carried: Set<DevelopmentChange> = emptySet()
    private var pending: Set<DevelopmentChange> = emptySet()
    private val stopWaiters = mutableListOf<CompletableDeferred<Unit>>()

    val state: StateFlow<DevelopmentSessionState> = mutableState.asStateFlow()
    val events: SharedFlow<DevelopmentEventRecord> = mutableEvents.asSharedFlow()
    private val loop: Job = scope.launch { run() }
    private val hostMonitor: Job =
        scope.launch {
            adapters.host.exits.collect { inbox.send(Input.HostExited(it)) }
        }

    fun reportChanges(changes: Collection<DevelopmentChange>) {
        if (changes.isEmpty()) return
        changeBox.add(changes)
        if (wakePending.compareAndSet(false, true)) {
            inbox.trySend(Input.ChangesAvailable)
        }
    }

    suspend fun submit(
        buildId: BuildId,
        level: ReloadLevel,
    ): DevelopmentCommandResult {
        val reply = CompletableDeferred<DevelopmentCommandResult>()
        val refused = DevelopmentCommandResult.Refused(DevelopmentCommandRefusal.SESSION_STOPPED)
        val completion = loop.invokeOnCompletion { reply.complete(refused) }
        try {
            if (!inbox.trySend(Input.Command(buildId, level, reply)).isSuccess) return refused
            return reply.await()
        } finally {
            completion.dispose()
        }
    }

    suspend fun stop() {
        val done = CompletableDeferred<Unit>()
        val completion = loop.invokeOnCompletion { done.complete(Unit) }
        try {
            if (inbox.trySend(Input.Stop(done)).isSuccess) done.await()
        } finally {
            completion.dispose()
        }
    }

    private suspend fun run() {
        try {
            for (input in inbox) {
                handle(input)
                if (stopRequested) break
            }
        } finally {
            withContext(NonCancellable) { shutDown() }
        }
    }

    private suspend fun handle(input: Input) {
        when (input) {
            Input.ChangesAvailable -> onChanges()
            Input.QuietElapsed -> {
                windowJob = null
                startPending()
            }
            is Input.BuildFinished -> onBuildFinished(input)
            is Input.FrontendFinished -> onFrontendFinished(input)
            is Input.HostFinished -> onHostFinished(input)
            is Input.HostExited -> {
                if (mutableState.value.activeServerGeneration == input.event.generation) {
                    lastRestartFailed = true
                    emit(input.event)
                } else if (mutableState.value.pendingServerGeneration == input.event.generation) {
                    pendingHostExit = input.event
                }
            }
            is Input.Command -> onCommand(input)
            is Input.Stop -> {
                stopRequested = true
                stopWaiters += input.done
            }
        }
    }

    private suspend fun onChanges() {
        wakePending.set(false)
        pending = mergeBounded(pending, changeBox.drain())
        if (pending.isEmpty()) return
        if (options.quietPeriod.isPositive()) {
            if (windowJob == null) {
                windowJob =
                    scope.launch {
                        delay(options.quietPeriod)
                        inbox.trySend(Input.QuietElapsed)
                    }
            }
        } else {
            startPending()
        }
    }

    /** A newer edit supersedes a running build; a running reload finishes first and `settle` resumes. */
    private suspend fun startPending() {
        if (pending.isEmpty() || stopRequested) return
        when (val running = work) {
            is Work.Frontend, is Work.Restarting -> return
            is Work.Building -> {
                work = null
                running.job.cancelAndJoin()
            }
            null -> Unit
        }
        startBuild()
    }

    private fun startBuild() {
        val id = nextBuild
        nextBuild = BuildId.after(id)
        val changes = mergeBounded(carried, pending)
        carried = emptySet()
        pending = emptySet()
        followUp = null
        if (!emit(BuildStarted(id, changes))) return
        val request = DevelopmentBuildRequest(id, mutableState.value.activeChanges)
        val started = options.timeSource.markNow()
        val job =
            scope.launch {
                val result =
                    guarded(
                        { DevelopmentBuildResult.Failed(listOf(internalDiagnostic("BUILD-ADAPTER-FAILURE"))) },
                    ) { adapters.build.build(request) }
                inbox.trySend(Input.BuildFinished(id, result, started.elapsedNow()))
            }
        work = Work.Building(id, job)
    }

    private suspend fun onBuildFinished(input: Input.BuildFinished) {
        val running = work as? Work.Building
        if (running == null || running.buildId != input.buildId) return
        work = null
        when (val result = input.result) {
            is DevelopmentBuildResult.Succeeded -> {
                val level = ReloadLevel.safest(result.requiredReload, reloadFloor())
                if (emit(BuildSucceeded(input.buildId, input.duration, level))) {
                    advance(input.buildId, level)
                } else {
                    settle()
                }
            }
            is DevelopmentBuildResult.Failed -> {
                carried = mutableState.value.activeChanges
                emit(BuildFailed(input.buildId, input.duration, result.diagnostics))
                settle()
            }
        }
    }

    /** Changes the host never received, or a host that is not at the last built state, need more than a hot update. */
    private fun reloadFloor(): ReloadLevel {
        val current = mutableState.value
        val byChange = ReloadPolicy.minimumFor(current.activeChanges)
        val byHost =
            when {
                lastRestartFailed -> ReloadLevel.COLD_RESTART
                current.activeServerGeneration == null -> ReloadLevel.SERVER_RESTART
                else -> ReloadLevel.HOT_ASSET
            }
        return ReloadLevel.safest(byChange, byHost)
    }

    private suspend fun advance(
        buildId: BuildId,
        level: ReloadLevel,
    ) {
        if (level.satisfies(ReloadLevel.SERVER_RESTART)) {
            startRestart(buildId, level, previousRetained = true)
        } else {
            startFrontend(buildId, level)
        }
    }

    private suspend fun startFrontend(
        buildId: BuildId,
        level: ReloadLevel,
    ) {
        val token = ++tokenCounter
        val job =
            scope.launch {
                val applied = guarded({ false }) { adapters.frontend.apply(buildId, level) }
                inbox.trySend(Input.FrontendFinished(token, applied))
            }
        work = Work.Frontend(token, buildId, level, job)
    }

    private suspend fun onFrontendFinished(input: Input.FrontendFinished) {
        val running = work as? Work.Frontend
        if (running == null || running.token != input.token) return
        work = null
        if (input.applied) {
            if (emit(ReloadApplied(running.buildId, running.level))) carried = emptySet()
            settle()
        } else {
            advance(running.buildId, running.level.fallback() ?: ReloadLevel.COLD_RESTART)
        }
    }

    private suspend fun startRestart(
        buildId: BuildId,
        level: ReloadLevel,
        previousRetained: Boolean,
    ) {
        val generation = lastGeneration?.let(ServerGeneration::after) ?: ServerGeneration.FIRST
        lastGeneration = generation
        pendingHostExit = null
        if (!emit(ServerRestarting(buildId, generation, level))) {
            settle()
            return
        }
        val token = ++tokenCounter
        val request = DevelopmentHostRestartRequest(buildId, generation, level)
        val job =
            scope.launch {
                val result =
                    guarded(
                        {
                            DevelopmentHostRestartResult.Failed(
                                listOf(internalDiagnostic("HOST-ADAPTER-FAILURE")),
                                previousApplicationRetained = false,
                            )
                        },
                    ) { adapters.host.restart(request) }
                inbox.trySend(Input.HostFinished(token, result))
            }
        work = Work.Restarting(token, request, previousRetained, job)
    }

    private suspend fun onHostFinished(input: Input.HostFinished) {
        val running = work as? Work.Restarting
        if (running == null || running.token != input.token) return
        work = null
        val request = running.request
        val earlyExit = pendingHostExit?.takeIf { it.generation == request.generation }
        pendingHostExit = null
        when (val result = input.result) {
            is DevelopmentHostRestartResult.Ready -> {
                if (earlyExit != null) {
                    escalateOrFail(running, false, earlyExit.diagnostics)
                } else {
                    lastRestartFailed = false
                    carried = emptySet()
                    emit(ServerReady(request.buildId, request.generation, result.urls))
                    settle()
                }
            }
            DevelopmentHostRestartResult.Unsupported ->
                escalateOrFail(
                    running,
                    running.previousRetained,
                    listOf(internalDiagnostic("HOST-RESTART-UNSUPPORTED")),
                )
            is DevelopmentHostRestartResult.Failed ->
                escalateOrFail(
                    running,
                    running.previousRetained && result.previousApplicationRetained,
                    result.diagnostics,
                )
        }
    }

    private suspend fun escalateOrFail(
        running: Work.Restarting,
        previousRetained: Boolean,
        diagnostics: List<DevelopmentDiagnostic>,
    ) {
        val request = running.request
        if (request.level != ReloadLevel.COLD_RESTART) {
            startRestart(request.buildId, ReloadLevel.COLD_RESTART, previousRetained)
            return
        }
        lastRestartFailed = true
        emit(ServerRestartFailed(request.buildId, request.generation, diagnostics, previousRetained))
        settle()
    }

    private suspend fun onCommand(input: Input.Command) {
        val refusal = refusalFor(input)
        if (refusal != null) {
            input.reply.complete(DevelopmentCommandResult.Refused(refusal))
            return
        }
        input.reply.complete(DevelopmentCommandResult.Accepted(input.buildId, input.level))
        if (work != null) {
            followUp = followUp?.let { ReloadLevel.safest(it, input.level) } ?: input.level
        } else if (emit(ReloadRequired(input.buildId, input.level))) {
            advance(input.buildId, input.level)
        }
    }

    private fun refusalFor(input: Input.Command): DevelopmentCommandRefusal? {
        val current = mutableState.value
        return when {
            stopRequested || current.phase == DevelopmentSessionPhase.STOPPED ->
                DevelopmentCommandRefusal.SESSION_STOPPED
            current.lastSuccessfulBuild != input.buildId || current.latestRequestedBuild != input.buildId ->
                DevelopmentCommandRefusal.STALE_BUILD
            !input.level.satisfies(ReloadLevel.SERVER_RESTART) && current.activeServerGeneration == null ->
                DevelopmentCommandRefusal.UNSUPPORTED
            else -> null
        }
    }

    /** Runs after every finished step: a newer edit wins over a queued command. */
    private suspend fun settle() {
        if (stopRequested) return
        if (pending.isNotEmpty()) {
            followUp = null
            if (windowJob == null) startPending()
            return
        }
        val level = followUp
        val buildId = mutableState.value.lastSuccessfulBuild
        followUp = null
        if (level != null && buildId != null && emit(ReloadRequired(buildId, level))) advance(buildId, level)
    }

    private suspend fun shutDown() {
        inbox.close()
        windowJob?.cancel()
        hostMonitor.cancel()
        val running = work
        work = null
        running?.job?.cancelAndJoin()
        if (running is Work.Building) emit(BuildCancelled(running.buildId, BuildCancellationReason.SESSION_STOPPING))
        withTimeoutOrNull(options.shutdownTimeout) {
            @Suppress("TooGenericExceptionCaught")
            try {
                adapters.host.shutdown()
            } catch (expected: Exception) {
                options.internalErrorListener(expected)
            }
        }
        emit(DevelopmentSessionStopped)
        for (input in inbox) {
            when (input) {
                is Input.Command ->
                    input.reply.complete(DevelopmentCommandResult.Refused(DevelopmentCommandRefusal.SESSION_STOPPED))
                is Input.Stop -> stopWaiters += input.done
                else -> Unit
            }
        }
        stopWaiters.forEach { it.complete(Unit) }
        scope.cancel()
    }

    private fun emit(event: DevelopmentEvent): Boolean {
        val transition = DevelopmentLifecycle.reduce(mutableState.value, event)
        if (!transition.wasApplied) {
            options.internalErrorListener(
                IllegalStateException("Development event ${event::class.simpleName} was ${transition.reason}"),
            )
            return false
        }
        mutableState.value = transition.state
        mutableEvents.tryEmit(DevelopmentEventRecord(++sequence, event, transition.state))
        return true
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> guarded(
        onFailure: () -> T,
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unexpected: Exception) {
            options.internalErrorListener(unexpected)
            onFailure()
        }

    private fun internalDiagnostic(code: String): DevelopmentDiagnostic =
        DevelopmentDiagnostic(
            DevelopmentDiagnosticCode.of(code),
            DevelopmentDiagnosticSeverity.ERROR,
            DevelopmentDiagnosticSummary.of("A development adapter failed unexpectedly. See the Woge development log."),
        )

    private sealed interface Work {
        val job: Job

        class Building(
            val buildId: BuildId,
            override val job: Job,
        ) : Work

        class Frontend(
            val token: Long,
            val buildId: BuildId,
            val level: ReloadLevel,
            override val job: Job,
        ) : Work

        class Restarting(
            val token: Long,
            val request: DevelopmentHostRestartRequest,
            val previousRetained: Boolean,
            override val job: Job,
        ) : Work
    }

    private sealed interface Input {
        data class HostExited(
            val event: ServerExited,
        ) : Input

        data object ChangesAvailable : Input

        data object QuietElapsed : Input

        class BuildFinished(
            val buildId: BuildId,
            val result: DevelopmentBuildResult,
            val duration: Duration,
        ) : Input

        class FrontendFinished(
            val token: Long,
            val applied: Boolean,
        ) : Input

        class HostFinished(
            val token: Long,
            val result: DevelopmentHostRestartResult,
        ) : Input

        class Command(
            val buildId: BuildId,
            val level: ReloadLevel,
            val reply: CompletableDeferred<DevelopmentCommandResult>,
        ) : Input

        class Stop(
            val done: CompletableDeferred<Unit>,
        ) : Input
    }

    /** Thread-safe collector for change reports from file watchers. */
    private class ChangeBox {
        private val lock = Any()
        private var changes: Set<DevelopmentChange> = emptySet()

        fun add(added: Collection<DevelopmentChange>) {
            synchronized(lock) { changes = mergeBounded(changes, added) }
        }

        fun drain(): Set<DevelopmentChange> =
            synchronized(lock) {
                changes.also { changes = emptySet() }
            }
    }
}

private const val MAX_TRACKED_CHANGES: Int = 1_024

/** Beyond the bound the exact paths no longer matter; one unknown change forces the safe level. */
@OptIn(ExperimentalWogeDevelopmentApi::class)
private fun mergeBounded(
    first: Set<DevelopmentChange>,
    second: Collection<DevelopmentChange>,
): Set<DevelopmentChange> {
    val merged = LinkedHashSet(first).apply { addAll(second) }
    return if (merged.size > MAX_TRACKED_CHANGES) {
        setOf(DevelopmentChange(DevelopmentChangeKind.UNKNOWN))
    } else {
        merged
    }
}
