package dev.woge.development.gradle

import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.process.ChildLaunchSpec
import dev.woge.development.process.ChildLauncher
import dev.woge.development.process.ListenProbe
import dev.woge.development.process.ManagedChild
import dev.woge.development.process.PortProbe
import dev.woge.development.process.ProcessChildLauncher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Thrown when `wogeDev` cannot start the Vite dev server; the message says what to do. */
@ExperimentalWogeDevelopmentApi
public class ViteStartupException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Runs the project's own Vite dev server for one `wogeDev` session. Vite owns hot updates for the files
 * in the frontend folder; Woge keeps owning Kotlin builds, the application child and full reloads.
 */
@ExperimentalWogeDevelopmentApi
internal class ViteDevServer(
    private val settings: ViteDevServerSettings,
    private val applicationPort: Int,
    private val print: (String) -> Unit,
    private val launcher: ChildLauncher = ProcessChildLauncher,
    private val probes: ViteProbes = ViteProbes(),
    private val startupTimeout: Duration = 30.seconds,
) {
    private val recentOutput = ArrayDeque<String>()

    @Volatile private var stopping = false

    /** Starts Vite and returns once it accepts connections. */
    suspend fun start(): ManagedChild {
        val port = settings.port
        if (!withContext(probes.context) { probes.port.isFree(port) }) {
            throw ViteStartupException(
                "Port $port for the Vite dev server is already in use. Stop the other process, " +
                    "or set `wogeVite { devPort = ... }`.",
            )
        }
        val exited = CompletableDeferred<Int>()
        val child = launch(exited)
        val started = TimeSource.Monotonic.markNow()
        while (!withContext(probes.context) { probes.listen.isListening(port) }) {
            val failure =
                when {
                    exited.isCompleted -> "Vite stopped with exit code ${exited.getCompleted()} before it was ready."
                    started.elapsedNow() > startupTimeout -> "Vite did not start within $startupTimeout."
                    else -> null
                }
            if (failure != null) {
                stop(child)
                throw ViteStartupException("$failure${lastLines()}")
            }
            delay(POLL_INTERVAL)
        }
        return child
    }

    private fun launch(exited: CompletableDeferred<Int>): ManagedChild =
        try {
            launcher.launch(spec(), ::output) { code ->
                exited.complete(code)
                if (!stopping) print("[woge] The Vite dev server stopped (exit code $code). Restart wogeDev.")
            }
        } catch (error: IOException) {
            throw ViteStartupException(
                "Could not start Node.js (`${settings.command.first()}`). Install Node.js, or set " +
                    "`wogeVite { node = \"/path/to/node\" }`.",
                error,
            )
        }

    suspend fun stop(child: ManagedChild) {
        stopping = true
        child.stop(STOP_GRACE)
    }

    private fun spec(): ChildLaunchSpec {
        val appOrigins = "http://127.0.0.1:$applicationPort,http://localhost:$applicationPort"
        return ChildLaunchSpec(
            command = settings.command,
            workingDirectory = settings.directory,
            environment =
                settings.environment +
                    mapOf("WOGE_VITE_PORT" to settings.port.toString(), "WOGE_APP_ORIGINS" to appOrigins),
        )
    }

    private fun output(line: String) {
        synchronized(recentOutput) {
            recentOutput.addLast(line)
            if (recentOutput.size > MAX_RECENT_LINES) recentOutput.removeFirst()
        }
        print("[vite] $line")
    }

    private fun lastLines(): String {
        val lines = synchronized(recentOutput) { recentOutput.toList() }
        return if (lines.isEmpty()) "" else lines.joinToString("\n", prefix = "\nLast Vite output:\n")
    }

    private companion object {
        val POLL_INTERVAL = 100.milliseconds
        val STOP_GRACE = 5.seconds
        const val MAX_RECENT_LINES = 20
    }
}

/** How [ViteDevServer] checks its port; replaceable in tests. */
internal class ViteProbes(
    val port: PortProbe = PortProbe.local,
    val listen: ListenProbe = ListenProbe.loopback,
    val context: CoroutineContext = Dispatchers.IO,
)
