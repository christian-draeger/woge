package dev.woge.development.process

import dev.woge.development.ExperimentalWogeDevelopmentApi
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How the host learns that a freshly started or restarted child serves requests. */
@ExperimentalWogeDevelopmentApi
public sealed interface ChildReadiness {
    /**
     * The child prints `WOGE-DEV-READY <token>` once it is ready. The host writes a fresh token to
     * [triggerFile] for every attempt. With [fastRestart], touching the file also restarts the
     * application inside the running JVM (Spring DevTools).
     */
    public class ReadyMarker(
        public val triggerFile: Path,
        public val fastRestart: Boolean = true,
    ) : ChildReadiness

    /**
     * The child is ready once its port accepts connections. Every restart is a new process, and the
     * port is checked to be free before it starts, so an older process can never answer.
     */
    public data object PortAccepting : ChildReadiness
}

/**
 * Settings for the application child.
 *
 * [diagnosticPrefix] names the host in diagnostic codes, such as `SPRING-HOST-EXITED`.
 * Use [springBoot] or [ktor] instead of filling this in by hand.
 */
@ExperimentalWogeDevelopmentApi
@Suppress("LongParameterList")
public class ChildProcessHostConfig(
    public val launch: ChildLaunchSpec,
    public val port: Int,
    public val readiness: ChildReadiness,
    public val diagnosticPrefix: String,
    public val startupTimeout: Duration = 60.seconds,
    public val restartTimeout: Duration = 30.seconds,
    public val stopGrace: Duration = 10.seconds,
    public val crashLoopLimit: Int = 3,
) {
    init {
        require(port in 1..MAX_PORT) { "The application port must be between 1 and $MAX_PORT" }
        require(startupTimeout.isPositive() && restartTimeout.isPositive()) { "Timeouts must be positive" }
        require(startupTimeout.isFinite() && restartTimeout.isFinite()) { "Timeouts must be finite" }
        require(stopGrace.isFinite() && !stopGrace.isNegative()) { "Stop grace must be finite and non-negative" }
        require(crashLoopLimit >= 1) { "The crash loop limit must be at least 1" }
        require(diagnosticPrefix.matches(Regex("[A-Z][A-Z0-9-]*"))) { "The diagnostic prefix must be upper case" }
    }

    public companion object {
        private const val MAX_PORT = 65_535

        /**
         * A Spring Boot child. [triggerFile] must sit in its own directory on the child's classpath
         * (ADR 0041); Spring DevTools watches it when [fastRestart] is on.
         */
        public fun springBoot(
            launch: ChildLaunchSpec,
            port: Int,
            triggerFile: Path,
            fastRestart: Boolean = true,
        ): ChildProcessHostConfig =
            ChildProcessHostConfig(
                launch.withEnvironment(
                    "WOGE_DEV_TRIGGER_FILE" to triggerFile.toAbsolutePath().toString(),
                    "SERVER_ADDRESS" to "127.0.0.1",
                    "SERVER_PORT" to port.toString(),
                    "SPRING_DEVTOOLS_RESTART_TRIGGER_FILE" to triggerFile.fileName.toString(),
                    "SPRING_DEVTOOLS_RESTART_ENABLED" to fastRestart.toString(),
                    "SPRING_DEVTOOLS_LIVERELOAD_ENABLED" to "false",
                ),
                port,
                ChildReadiness.ReadyMarker(triggerFile, fastRestart),
                "SPRING-HOST",
            )

        /** A Ktor child. The application reads its port from the `PORT` environment variable. */
        public fun ktor(
            launch: ChildLaunchSpec,
            port: Int,
        ): ChildProcessHostConfig =
            ChildProcessHostConfig(
                launch.withEnvironment("PORT" to port.toString()),
                port,
                ChildReadiness.PortAccepting,
                "KTOR-HOST",
                restartTimeout = 60.seconds,
            )

        private fun ChildLaunchSpec.withEnvironment(vararg values: Pair<String, String>): ChildLaunchSpec =
            copy(environment = environment + values)
    }
}

/** Tells whether the fixed application port can be bound. */
@ExperimentalWogeDevelopmentApi
public fun interface PortProbe {
    public fun isFree(port: Int): Boolean

    public companion object {
        public val loopback: PortProbe =
            PortProbe { port ->
                runCatching { ServerSocket(port, 1, InetAddress.getLoopbackAddress()).use { true } }
                    .getOrDefault(false)
            }

        /** Also catches listeners on the wildcard address, such as an IPv6 `*:8080` server. */
        public val local: PortProbe =
            PortProbe { port ->
                loopback.isFree(port) &&
                    runCatching { ServerSocket(port).use { true } }.getOrDefault(false)
            }
    }
}

/** Tells whether something accepts connections on the application port. */
@ExperimentalWogeDevelopmentApi
public fun interface ListenProbe {
    public fun isListening(port: Int): Boolean

    public companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 200

        /** Tries IPv4 and IPv6 loopback, so `0.0.0.0`, `127.0.0.1` and `::1` servers all count. */
        public val loopback: ListenProbe =
            ListenProbe { port ->
                listOf("127.0.0.1", "::1").any { host ->
                    runCatching {
                        Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS) }
                        true
                    }.getOrDefault(false)
                }
            }
    }
}
