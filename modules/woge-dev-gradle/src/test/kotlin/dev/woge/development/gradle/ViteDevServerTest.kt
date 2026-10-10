package dev.woge.development.gradle

import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.process.ChildLaunchSpec
import dev.woge.development.process.ChildLauncher
import dev.woge.development.process.ListenProbe
import dev.woge.development.process.ManagedChild
import dev.woge.development.process.PortProbe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

@OptIn(ExperimentalWogeDevelopmentApi::class)
class ViteDevServerTest {
    private val settings =
        ViteDevServerSettings(
            listOf("node", "--eval", "x"),
            Path.of("app"),
            5173,
            mapOf("WOGE_VITE_COMMAND" to "serve"),
        )
    private val printed = mutableListOf<String>()

    @Test
    fun `starts Vite with the app origins and waits until it listens`() =
        runTest {
            val launcher = FakeLauncher()
            var probes = 0
            val server = server(launcher, listen = { ++probes >= 3 })

            val child = server.start()

            val spec = launcher.spec!!
            assertEquals(settings.command, spec.command)
            assertEquals("5173", spec.environment["WOGE_VITE_PORT"])
            assertEquals("http://127.0.0.1:8080,http://localhost:8080", spec.environment["WOGE_APP_ORIGINS"])
            assertEquals("serve", spec.environment["WOGE_VITE_COMMAND"])
            assertEquals(listOf("[vite] VITE ready"), printed)
            server.stop(child)
            assertFalse(child.isAlive)
            assertTrue(printed.none { it.contains("stopped") }, "a planned stop is not reported")
        }

    @Test
    fun `a busy port fails before anything starts`() =
        runTest {
            val launcher = FakeLauncher()
            val error = assertThrows<ViteStartupException> { server(launcher, portFree = false).start() }
            assertTrue(error.message!!.contains("devPort"))
            assertEquals(null, launcher.spec)
        }

    @Test
    fun `an early exit shows the last Vite output`() =
        runTest {
            val launcher = FakeLauncher(exitCode = 1)
            val error = assertThrows<ViteStartupException> { server(launcher, listen = { false }).start() }
            assertTrue(error.message!!.contains("exit code 1"), error.message)
            assertTrue(error.message!!.contains("VITE ready"), error.message)
        }

    @Test
    fun `a missing Node install and a slow start are explained`() =
        runTest {
            val missing = ChildLauncher { _, _, _ -> throw IOException("not found") }
            assertTrue(
                assertThrows<ViteStartupException> { server(missing).start() }.message!!.contains("wogeVite { node"),
            )
            val slow = assertThrows<ViteStartupException> { server(FakeLauncher(), listen = { false }).start() }
            assertTrue(slow.message!!.contains("did not start"), slow.message)
        }

    private fun server(
        launcher: ChildLauncher,
        portFree: Boolean = true,
        listen: () -> Boolean = { true },
    ) = ViteDevServer(
        settings,
        applicationPort = 8080,
        print = { printed += it },
        launcher = launcher,
        probes = ViteProbes(PortProbe { portFree }, ListenProbe { listen() }, EmptyCoroutineContext),
    )

    private class FakeLauncher(
        private val exitCode: Int? = null,
    ) : ChildLauncher {
        var spec: ChildLaunchSpec? = null

        override fun launch(
            spec: ChildLaunchSpec,
            onLine: (String) -> Unit,
            onExit: (Int) -> Unit,
        ): ManagedChild {
            this.spec = spec
            onLine("VITE ready")
            exitCode?.let(onExit)
            return object : ManagedChild {
                override var isAlive: Boolean = exitCode == null

                override suspend fun stop(grace: Duration) {
                    if (isAlive) onExit(143)
                    isAlive = false
                }
            }
        }
    }
}
