package dev.woge.development.spring

import dev.woge.development.ExperimentalWogeDevelopmentApi
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Settings for the Spring Boot child.
 *
 * [triggerFile] must be on the application's restart classpath. The host creates it before launch.
 * [fastRestart] enables Spring DevTools; disabling it offers only complete child restarts.
 */
@ExperimentalWogeDevelopmentApi
@Suppress("LongParameterList")
public class SpringDevelopmentHostConfig(
    public val launch: ChildLaunchSpec,
    public val port: Int,
    public val triggerFile: Path,
    public val fastRestart: Boolean = true,
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
    }

    private companion object {
        const val MAX_PORT = 65_535
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
    }
}
