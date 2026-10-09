package dev.woge.development.spring

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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
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

/**
 * Runs the Spring Boot application as a child process owned by Woge.
 *
 * - `SERVER_RESTART` with a live child touches the trigger file. Spring's restart tool then swaps
 *   its class loader inside the same JVM. A Spring ready listener echoes the new request token.
 * - `SERVER_RESTART` without a live child starts a fresh child.
 * - `SERVER_RESTART` with fast restart disabled answers [DevelopmentHostRestartResult.Unsupported], so
 *   the orchestrator escalates.
 * - `COLD_RESTART` stops the child, checks that the port is free and starts a new child.
 *
 * Child output is only matched against the ready marker. It never ends up in diagnostics.
 */
@ExperimentalWogeDevelopmentApi
@Suppress("TooManyFunctions")
public class SpringDevelopmentHost(
    private val config: SpringDevelopmentHostConfig,
    private val launcher: ChildLauncher = ProcessChildLauncher,
    private val portProbe: PortProbe = PortProbe.local,
) : DevelopmentHostAdapter {
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
                    !config.fastRestart -> DevelopmentHostRestartResult.Unsupported
                    else -> triggerRestart(current, request.generation)
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
        if (!writeTrigger(token)) {
            return failed(TRIGGER_FAILED, "The restart trigger file could not be written", retained = false)
        }
        val signals = Channel<Signal>(Channel.CONFLATED)
        val intentionalStop = AtomicBoolean(false)
        val activeGeneration = AtomicReference<ServerGeneration?>(null)
        val child =
            try {
                launcher.launch(
                    childLaunch(),
                    { line ->
                        if (line.startsWith(READY_PREFIX)) {
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
        generation: ServerGeneration,
    ): DevelopmentHostRestartResult {
        while (current.signals.tryReceive().isSuccess) {
            // Drop output from the previous generation so only a new ready line counts.
        }
        val token = UUID.randomUUID().toString()
        if (!writeTrigger(token)) {
            return failed(
                TRIGGER_FAILED,
                "The restart trigger file could not be written",
                retained = current.child.isAlive,
            )
        }
        return awaitReady(config.restartTimeout, token, generation)
    }

    private fun childLaunch(): ChildLaunchSpec =
        config.launch.copy(
            environment =
                config.launch.environment +
                    mapOf(
                        "WOGE_DEV_TRIGGER_FILE" to config.triggerFile.toAbsolutePath().toString(),
                        "SERVER_ADDRESS" to "127.0.0.1",
                        "SERVER_PORT" to config.port.toString(),
                        "SPRING_DEVTOOLS_RESTART_TRIGGER_FILE" to config.triggerFile.fileName.toString(),
                        "SPRING_DEVTOOLS_RESTART_ENABLED" to config.fastRestart.toString(),
                        "SPRING_DEVTOOLS_LIVERELOAD_ENABLED" to "false",
                    ),
        )

    private fun writeTrigger(token: String): Boolean =
        try {
            config.triggerFile.parent?.let { Files.createDirectories(it) }
            Files.writeString(config.triggerFile, "$token\n")
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
        val outcome = withTimeoutOrNull(timeout) { nextOutcome(current.signals, token) }
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
            failed(START_TIMEOUT, "The application child was not ready in time", retained = false)
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
        const val READY_PREFIX = "WOGE-DEV-READY "
        const val PORT_IN_USE = "SPRING-HOST-PORT-IN-USE"
        const val START_FAILED = "SPRING-HOST-START-FAILED"
        const val START_TIMEOUT = "SPRING-HOST-START-TIMEOUT"
        const val EXITED = "SPRING-HOST-EXITED"
        const val TRIGGER_FAILED = "SPRING-HOST-TRIGGER-FAILED"
        const val CRASH_LOOP = "SPRING-HOST-CRASH-LOOP"
    }
}
