package dev.woge.development.spring

import dev.woge.development.ExperimentalWogeDevelopmentApi
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** How to start the application child: a plain command line, like you would type in a terminal. */
@ExperimentalWogeDevelopmentApi
public data class ChildLaunchSpec(
    public val command: List<String>,
    public val workingDirectory: Path,
    public val environment: Map<String, String> = emptyMap(),
) {
    init {
        require(command.isNotEmpty()) { "The child command must not be empty" }
    }

    override fun toString(): String = "ChildLaunchSpec(command=<${command.size} args>, environment=<redacted>)"
}

/** A running application child. */
@ExperimentalWogeDevelopmentApi
public interface ManagedChild {
    public val isAlive: Boolean

    /** Asks the child to stop, then kills it after [grace]. Must be safe to call more than once. */
    public suspend fun stop(grace: Duration)
}

/** Starts a child and reports each output line and the exit code. Replaceable in tests. */
@ExperimentalWogeDevelopmentApi
public fun interface ChildLauncher {
    public fun launch(
        spec: ChildLaunchSpec,
        onLine: (String) -> Unit,
        onExit: (Int) -> Unit,
    ): ManagedChild
}

/** Starts a real operating-system process with merged stdout and stderr. */
@ExperimentalWogeDevelopmentApi
public object ProcessChildLauncher : ChildLauncher {
    override fun launch(
        spec: ChildLaunchSpec,
        onLine: (String) -> Unit,
        onExit: (Int) -> Unit,
    ): ManagedChild {
        val builder =
            ProcessBuilder(spec.command)
                .directory(spec.workingDirectory.toFile())
                .redirectErrorStream(true)
        builder.environment().putAll(spec.environment)
        val process = builder.start()
        thread(isDaemon = true, name = "woge-dev-child-output") {
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLine) }
            onExit(process.waitFor())
        }
        return object : ManagedChild {
            override val isAlive: Boolean get() = process.isAlive

            override suspend fun stop(grace: Duration) {
                process.destroy()
                if (!process.waitFor(grace.toLong(DurationUnit.MILLISECONDS), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly().waitFor()
                }
            }
        }
    }
}
