package dev.woge.development.process

import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentDiagnosticCode
import dev.woge.development.DevelopmentDiagnosticSeverity
import dev.woge.development.DevelopmentDiagnosticSummary
import dev.woge.development.DevelopmentUrl
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import dev.woge.development.ServerExited
import dev.woge.development.ServerGeneration
import dev.woge.development.orchestrator.DevelopmentHostAdapter
import dev.woge.development.orchestrator.DevelopmentHostRestartRequest
import dev.woge.development.orchestrator.DevelopmentHostRestartResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs the application as a child process owned by Woge.
 *
 * - `SERVER_RESTART` with a live child and [ChildReadiness.ReadyMarker] fast restart touches the
 *   trigger file. Spring's restart tool then swaps its class loader inside the same JVM. A ready
 *   listener echoes the new request token.
 * - `SERVER_RESTART` without a live child starts a fresh child.
 * - `SERVER_RESTART` without fast restart answers [DevelopmentHostRestartResult.Unsupported], so the
 *   orchestrator escalates to a complete restart.
 * - `COLD_RESTART` stops the child, checks that the port is free and starts a new child.
 *
 * Child output is only matched against the ready marker. It never ends up in diagnostics.
 */
@ExperimentalWogeDevelopmentApi
@Suppress("TooManyFunctions")
public class ChildProcessDevelopmentHost(
    private val config: ChildProcessHostConfig,
    private val launcher: ChildLauncher = ProcessChildLauncher,
    private val portProbe: PortProbe = PortProbe.local,
    private val listenProbe: ListenProbe = ListenProbe.loopback,
    private val probeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DevelopmentHostAdapter {
    private val marker = config.readiness as? ChildReadiness.ReadyMarker

    private sealed interface Signal {
        data class Ready(
            val token: String,
        ) : Signal

        data class Exited(
            val code: Int,
        ) : Signal
    }

    private class Running(
        val child: ManagedChild,
        val signals: Channel<Signal>,
        val intentionalStop: AtomicBoolean,
        val generation: AtomicReference<ServerGeneration?>,
    )

    private val lock = Mutex()
    private var running: Running? = null
    private var consecutiveFailures = 0
    private val childExits = Channel<ServerExited>(Channel.UNLIMITED)
    override val exits: Flow<ServerExited> = childExits.receiveAsFlow()

    override suspend fun restart(request: DevelopmentHostRestartRequest): DevelopmentHostRestartResult =
        lock.withLock {
            val current = running?.takeIf { it.child.isAlive }
            try {
                when {
                    request.level == ReloadLevel.COLD_RESTART -> startFresh(request.generation)
                    current == null -> startFresh(request.generation)
                    marker == null || !marker.fastRestart -> DevelopmentHostRestartResult.Unsupported
                    else -> triggerRestart(current, marker, request.generation)
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { stopCurrent() }
                throw cancelled
            }
        }

    override suspend fun shutdown() {
        withContext(NonCancellable) { lock.withLock { stopCurrent() } }
    }

    private suspend fun stopCurrent() {
        val old = running ?: return
        old.intentionalStop.set(true)
        running = null
        old.child.stop(config.stopGrace)
    }

    @Suppress("ReturnCount")
    private suspend fun startFresh(generation: ServerGeneration): DevelopmentHostRestartResult {
        stopCurrent()
        if (!portProbe.isFree(config.port)) {
            return failed(PORT_IN_USE, "The application port ${config.port} is already in use", retained = false)
        }
        val token = UUID.randomUUID().toString()
        if (marker != null && !writeTrigger(marker, token)) {
            return failed(TRIGGER_FAILED, "The restart trigger file could not be written", retained = false)
        }
        val signals = Channel<Signal>(Channel.CONFLATED)
        val intentionalStop = AtomicBoolean(false)
        val activeGeneration = AtomicReference<ServerGeneration?>(null)
        val child =
            try {
                launcher.launch(
                    config.launch,
                    { line ->
                        if (marker != null && line.startsWith(READY_PREFIX)) {
                            signals.trySend(Signal.Ready(line.removePrefix(READY_PREFIX).trim()))
                        }
                    },
                    {
                        signals.trySend(Signal.Exited(it))
                        activeGeneration.get()?.let { readyGeneration ->
                            if (!intentionalStop.get()) {
                                childExits.trySend(
                                    ServerExited(
                                        readyGeneration,
                                        listOf(
                                            diagnostic(
                                                EXITED,
                                                "The application child exited. Save a change to rebuild and restart.",
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }
                    },
                )
            } catch (expected: IOException) {
                null
            }
        if (child != null) running = Running(child, signals, intentionalStop, activeGeneration)
        return if (child == null) {
            failed(START_FAILED, "The application child could not be started", retained = false)
        } else {
            awaitReady(config.startupTimeout, token, generation)
        }
    }

    private suspend fun triggerRestart(
        current: Running,
        marker: ChildReadiness.ReadyMarker,
        generation: ServerGeneration,
    ): DevelopmentHostRestartResult {
        while (current.signals.tryReceive().isSuccess) {
            // Drop output from the previous generation so only a new ready line counts.
        }
        val token = UUID.randomUUID().toString()
        if (!writeTrigger(marker, token)) {
            return failed(
                TRIGGER_FAILED,
                "The restart trigger file could not be written",
                retained = current.child.isAlive,
            )
        }
        return awaitReady(config.restartTimeout, token, generation)
    }

    private fun writeTrigger(
        marker: ChildReadiness.ReadyMarker,
        token: String,
    ): Boolean =
        try {
            marker.triggerFile.parent?.let { Files.createDirectories(it) }
            Files.writeString(marker.triggerFile, "$token\n")
            true
        } catch (expected: IOException) {
            false
        }

    private suspend fun awaitReady(
        timeout: Duration,
        token: String,
        generation: ServerGeneration,
    ): DevelopmentHostRestartResult {
        val current = checkNotNull(running)
        val outcome =
            withTimeoutOrNull(timeout) {
                if (marker == null) listening(current.signals) else nextOutcome(current.signals, token)
            }
        if (outcome == Outcome.READY) {
            current.generation.set(generation)
        }
        if (outcome == Outcome.READY && current.child.isAlive) {
            consecutiveFailures = 0
            return DevelopmentHostRestartResult.Ready(listOf(DevelopmentUrl.local("http://localhost:${config.port}/")))
        }
        val exited = outcome == Outcome.EXITED || !current.child.isAlive
        stopCurrent()
        return if (exited) {
            failed(EXITED, "The application child exited before it was ready", retained = false)
        } else {
            val summary =
                if (marker == null) {
                    "The application did not accept connections on port ${config.port} in time. " +
                        "Start the server on the port from the PORT environment variable."
                } else {
                    "The application child was not ready in time"
                }
            failed(START_TIMEOUT, summary, retained = false)
        }
    }

    private suspend fun nextOutcome(
        signals: Channel<Signal>,
        token: String,
    ): Outcome {
        var outcome: Outcome? = null
        while (outcome == null) {
            outcome =
                when (val signal = signals.receive()) {
                    is Signal.Ready -> Outcome.READY.takeIf { signal.token == token }
                    is Signal.Exited -> Outcome.EXITED
                }
        }
        return outcome
    }

    private suspend fun listening(signals: Channel<Signal>): Outcome {
        while (true) {
            if (signals.tryReceive().getOrNull() is Signal.Exited) return Outcome.EXITED
            if (withContext(probeDispatcher) { listenProbe.isListening(config.port) }) return Outcome.READY
            delay(LISTEN_POLL)
        }
    }

    private enum class Outcome { READY, EXITED }

    private fun failed(
        code: String,
        summary: String,
        retained: Boolean,
    ): DevelopmentHostRestartResult.Failed {
        consecutiveFailures++
        val diagnostics = mutableListOf(diagnostic(code, summary))
        if (consecutiveFailures >= config.crashLoopLimit) {
            diagnostics += diagnostic(CRASH_LOOP, "The application failed to start $consecutiveFailures times in a row")
        }
        return DevelopmentHostRestartResult.Failed(diagnostics, retained)
    }

    private fun diagnostic(
        code: String,
        summary: String,
    ): DevelopmentDiagnostic =
        DevelopmentDiagnostic(
            DevelopmentDiagnosticCode.of("${config.diagnosticPrefix}-$code"),
            DevelopmentDiagnosticSeverity.ERROR,
            DevelopmentDiagnosticSummary.of(summary),
        )

    private companion object {
        const val READY_PREFIX = "WOGE-DEV-READY "
        const val PORT_IN_USE = "PORT-IN-USE"
        const val START_FAILED = "START-FAILED"
        const val START_TIMEOUT = "START-TIMEOUT"
        const val EXITED = "EXITED"
        const val TRIGGER_FAILED = "TRIGGER-FAILED"
        const val CRASH_LOOP = "CRASH-LOOP"
        val LISTEN_POLL = 50.milliseconds
    }
}
