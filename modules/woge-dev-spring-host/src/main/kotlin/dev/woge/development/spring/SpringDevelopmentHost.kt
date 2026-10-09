package dev.woge.development.spring

import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentDiagnosticCode
import dev.woge.development.DevelopmentDiagnosticSeverity
import dev.woge.development.DevelopmentDiagnosticSummary
import dev.woge.development.DevelopmentUrl
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import dev.woge.development.orchestrator.DevelopmentHostAdapter
import dev.woge.development.orchestrator.DevelopmentHostRestartRequest
import dev.woge.development.orchestrator.DevelopmentHostRestartResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.file.Files
import kotlin.time.Duration

/**
 * Runs the Spring Boot application as a child process owned by Woge.
 *
 * - `SERVER_RESTART` with a live child touches the trigger file. Spring's restart tool then swaps
 *   its class loader inside the same JVM. The host waits for a new "started" log line.
 * - `SERVER_RESTART` without a live child starts a fresh child.
 * - `SERVER_RESTART` without a trigger file answers [DevelopmentHostRestartResult.Unsupported], so
 *   the orchestrator escalates.
 * - `COLD_RESTART` stops the child, checks that the port is free and starts a new child.
 *
 * Child output is only matched against the ready marker. It never ends up in diagnostics.
 */
@ExperimentalWogeDevelopmentApi
public class SpringDevelopmentHost(
    private val config: SpringDevelopmentHostConfig,
    private val launcher: ChildLauncher = ProcessChildLauncher,
    private val portProbe: PortProbe = PortProbe.loopback,
) : DevelopmentHostAdapter {
    private sealed interface Signal {
        data class Line(
            val text: String,
        ) : Signal

        data class Exited(
            val code: Int,
        ) : Signal
    }

    private class Running(
        val child: ManagedChild,
        val signals: Channel<Signal>,
    )

    private val lock = Mutex()
    private var running: Running? = null
    private var consecutiveFailures = 0

    override suspend fun restart(request: DevelopmentHostRestartRequest): DevelopmentHostRestartResult =
        lock.withLock {
            val current = running?.takeIf { it.child.isAlive }
            when {
                request.level == ReloadLevel.COLD_RESTART -> startFresh()
                current == null -> startFresh()
                config.triggerFile == null -> DevelopmentHostRestartResult.Unsupported
                else -> triggerRestart(current, request)
            }
        }

    override suspend fun shutdown() {
        withContext(NonCancellable) { stopCurrent() }
    }

    private suspend fun stopCurrent() {
        val old = running ?: return
        running = null
        old.child.stop(config.stopGrace)
    }

    private suspend fun startFresh(): DevelopmentHostRestartResult {
        stopCurrent()
        if (!portProbe.isFree(config.port)) {
            return failed(PORT_IN_USE, "The application port ${config.port} is already in use", retained = false)
        }
        val signals = Channel<Signal>(Channel.UNLIMITED)
        val child =
            try {
                launcher.launch(
                    config.launch,
                    { signals.trySend(Signal.Line(it)) },
                    { signals.trySend(Signal.Exited(it)) },
                )
            } catch (expected: IOException) {
                null
            }
        if (child != null) running = Running(child, signals)
        return if (child == null) {
            failed(START_FAILED, "The application child could not be started", retained = false)
        } else {
            awaitReady(config.startupTimeout)
        }
    }

    private suspend fun triggerRestart(
        current: Running,
        request: DevelopmentHostRestartRequest,
    ): DevelopmentHostRestartResult {
        while (current.signals.tryReceive().isSuccess) {
            // Drop output from the previous generation so only a new ready line counts.
        }
        val trigger = checkNotNull(config.triggerFile)
        try {
            trigger.parent?.let { Files.createDirectories(it) }
            Files.writeString(trigger, "build=${request.buildId.value} generation=${request.generation.value}\n")
        } catch (expected: IOException) {
            return failed(TRIGGER_FAILED, "The restart trigger file could not be written", retained = true)
        }
        return awaitReady(config.restartTimeout)
    }

    private suspend fun awaitReady(timeout: Duration): DevelopmentHostRestartResult {
        val outcome = withTimeoutOrNull(timeout) { nextOutcome(checkNotNull(running).signals) }
        if (outcome == Outcome.READY) {
            consecutiveFailures = 0
            return DevelopmentHostRestartResult.Ready(listOf(DevelopmentUrl.local("http://localhost:${config.port}/")))
        }
        stopCurrent()
        return if (outcome == Outcome.EXITED) {
            failed(EXITED, "The application child exited before it was ready", retained = false)
        } else {
            failed(START_TIMEOUT, "The application child was not ready in time", retained = false)
        }
    }

    private suspend fun nextOutcome(signals: Channel<Signal>): Outcome {
        var outcome: Outcome? = null
        while (outcome == null) {
            outcome =
                when (val signal = signals.receive()) {
                    is Signal.Line -> Outcome.READY.takeIf { config.readyMarker.containsMatchIn(signal.text) }
                    is Signal.Exited -> Outcome.EXITED
                }
        }
        return outcome
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
            DevelopmentDiagnosticCode.of(code),
            DevelopmentDiagnosticSeverity.ERROR,
            DevelopmentDiagnosticSummary.of(summary),
        )

    private companion object {
        const val PORT_IN_USE = "SPRING-HOST-PORT-IN-USE"
        const val START_FAILED = "SPRING-HOST-START-FAILED"
        const val START_TIMEOUT = "SPRING-HOST-START-TIMEOUT"
        const val EXITED = "SPRING-HOST-EXITED"
        const val TRIGGER_FAILED = "SPRING-HOST-TRIGGER-FAILED"
        const val CRASH_LOOP = "SPRING-HOST-CRASH-LOOP"
    }
}
