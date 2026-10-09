package dev.woge.development.spring

import dev.woge.development.BuildId
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import dev.woge.development.ServerGeneration
import dev.woge.development.orchestrator.DevelopmentHostRestartRequest
import dev.woge.development.orchestrator.DevelopmentHostRestartResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalWogeDevelopmentApi::class, ExperimentalCoroutinesApi::class)
class SpringDevelopmentHostTest {
    @TempDir
    lateinit var directory: Path

    private class FakeChild(
        val onLine: (String) -> Unit,
        val onExit: (Int) -> Unit,
    ) : ManagedChild {
        override var isAlive: Boolean = true
        var stops = 0

        override suspend fun stop(grace: Duration) {
            stops++
            isAlive = false
        }

        fun say(line: String) = onLine(line)

        fun die(code: Int) {
            isAlive = false
            onExit(code)
        }
    }

    private class FakeLauncher : ChildLauncher {
        val children = mutableListOf<FakeChild>()

        override fun launch(
            spec: ChildLaunchSpec,
            onLine: (String) -> Unit,
            onExit: (Int) -> Unit,
        ): ManagedChild = FakeChild(onLine, onExit).also { children += it }
    }

    private val started = "Started DemoApplicationKt in 1.2 seconds (process running for 1.6)"

    private fun config(
        trigger: Boolean = true,
        crashLoopLimit: Int = 3,
        startupTimeout: Duration = 5.seconds,
    ) = SpringDevelopmentHostConfig(
        launch = ChildLaunchSpec(listOf("java", "-jar", "app.jar"), directory),
        port = 8080,
        triggerFile = directory.resolve("trigger/restart.txt").takeIf { trigger },
        startupTimeout = startupTimeout,
        restartTimeout = 5.seconds,
        crashLoopLimit = crashLoopLimit,
    )

    private fun request(
        build: Long,
        generation: Long,
        level: ReloadLevel = ReloadLevel.SERVER_RESTART,
    ) = DevelopmentHostRestartRequest(BuildId.of(build), ServerGeneration.of(generation), level)

    private fun failedCodes(result: DevelopmentHostRestartResult): List<String> =
        assertInstanceOf(DevelopmentHostRestartResult.Failed::class.java, result).diagnostics.map { it.code.value }

    @Test
    fun `the first restart starts the child and waits for the ready line`() =
        runTest {
            val launcher = FakeLauncher()
            val host = SpringDevelopmentHost(config(), launcher) { true }

            val result = async { host.restart(request(1, 1)) }
            runCurrent()
            launcher.children.single().say("noise")
            launcher.children.single().say(started)

            val ready = assertInstanceOf(DevelopmentHostRestartResult.Ready::class.java, result.await())
            assertEquals("http://localhost:8080/", ready.urls.single().value)
        }

    @Test
    fun `a server restart touches the trigger file and waits for a new ready line`() =
        runTest {
            val launcher = FakeLauncher()
            val host = SpringDevelopmentHost(config(), launcher) { true }
            val first = async { host.restart(request(1, 1)) }
            runCurrent()
            launcher.children.single().say(started)
            first.await()

            val second = async { host.restart(request(2, 2)) }
            runCurrent()
            val trigger = directory.resolve("trigger/restart.txt")
            assertTrue(Files.readString(trigger).contains("build=2 generation=2"))
            assertFalse(second.isCompleted)
            launcher.children.single().say(started)

            assertInstanceOf(DevelopmentHostRestartResult.Ready::class.java, second.await())
            assertEquals(1, launcher.children.size)
        }

    @Test
    fun `without a trigger file a live child cannot do a server restart`() =
        runTest {
            val launcher = FakeLauncher()
            val host = SpringDevelopmentHost(config(trigger = false), launcher) { true }
            val first = async { host.restart(request(1, 1)) }
            runCurrent()
            launcher.children.single().say(started)
            first.await()

            assertEquals(DevelopmentHostRestartResult.Unsupported, host.restart(request(2, 2)))
        }

    @Test
    fun `a cold restart replaces the child`() =
        runTest {
            val launcher = FakeLauncher()
            val host = SpringDevelopmentHost(config(), launcher) { true }
            val first = async { host.restart(request(1, 1)) }
            runCurrent()
            launcher.children.single().say(started)
            first.await()

            val cold = async { host.restart(request(2, 2, ReloadLevel.COLD_RESTART)) }
            runCurrent()
            assertEquals(2, launcher.children.size)
            assertEquals(1, launcher.children.first().stops)
            launcher.children.last().say(started)

            assertInstanceOf(DevelopmentHostRestartResult.Ready::class.java, cold.await())
        }

    @Test
    fun `an occupied port fails before any child is started`() =
        runTest {
            val launcher = FakeLauncher()
            val host = SpringDevelopmentHost(config(), launcher) { false }

            assertEquals(listOf("SPRING-HOST-PORT-IN-USE"), failedCodes(host.restart(request(1, 1))))
            assertTrue(launcher.children.isEmpty())
        }

    @Test
    fun `a child that exits during startup fails without leaking its output`() =
        runTest {
            val launcher = FakeLauncher()
            val host = SpringDevelopmentHost(config(), launcher) { true }

            val result = async { host.restart(request(1, 1)) }
            runCurrent()
            launcher.children.single().say("DATABASE_PASSWORD=hunter2")
            launcher.children.single().die(1)

            val failed = assertInstanceOf(DevelopmentHostRestartResult.Failed::class.java, result.await())
            assertEquals(listOf("SPRING-HOST-EXITED"), failed.diagnostics.map { it.code.value })
            assertFalse(failed.diagnostics.toString().contains("hunter2"))
            assertFalse(failed.previousApplicationRetained)
        }

    @Test
    fun `a child that never becomes ready times out and is stopped`() =
        runTest {
            val launcher = FakeLauncher()
            val host = SpringDevelopmentHost(config(), launcher) { true }

            val codes = failedCodes(host.restart(request(1, 1)))

            assertEquals(listOf("SPRING-HOST-START-TIMEOUT"), codes)
            assertEquals(1, launcher.children.single().stops)
        }

    @Test
    fun `repeated startup failures add a crash loop diagnostic`() =
        runTest {
            val host = SpringDevelopmentHost(config(crashLoopLimit = 2), FakeLauncher()) { true }

            host.restart(request(1, 1))
            val codes = failedCodes(host.restart(request(2, 2, ReloadLevel.COLD_RESTART)))

            assertEquals(listOf("SPRING-HOST-START-TIMEOUT", "SPRING-HOST-CRASH-LOOP"), codes)
        }

    @Test
    fun `shutdown stops the child`() =
        runTest {
            val launcher = FakeLauncher()
            val host = SpringDevelopmentHost(config(), launcher) { true }
            val first = async { host.restart(request(1, 1)) }
            runCurrent()
            launcher.children.single().say(started)
            first.await()

            host.shutdown()
            host.shutdown()

            assertEquals(1, launcher.children.single().stops)
        }

    @Test
    fun `a real process is started, detected as ready and stopped`() =
        runBlocking {
            val config =
                SpringDevelopmentHostConfig(
                    launch =
                        ChildLaunchSpec(
                            listOf("sh", "-c", "echo 'Started Demo in 0.1 seconds'; sleep 30"),
                            directory,
                        ),
                    port = 8080,
                    startupTimeout = 20.seconds,
                )
            val host = SpringDevelopmentHost(config, ProcessChildLauncher) { true }

            assertInstanceOf(DevelopmentHostRestartResult.Ready::class.java, host.restart(request(1, 1)))
            host.shutdown()
        }
}
