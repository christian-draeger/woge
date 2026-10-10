package dev.woge.development.process

import dev.woge.development.BuildId
import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.DevelopmentSessionPhase
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import dev.woge.development.ServerGeneration
import dev.woge.development.orchestrator.DevelopmentAdapters
import dev.woge.development.orchestrator.DevelopmentHostRestartRequest
import dev.woge.development.orchestrator.DevelopmentHostRestartResult
import dev.woge.development.orchestrator.DevelopmentOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalWogeDevelopmentApi::class)
class KtorDevelopmentIntegrationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `real Ktor restarts on Kotlin and generated edits and keeps serving through compile errors`() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val fixture = Fixture(directory)
            val orchestrator =
                DevelopmentOrchestrator.start(
                    scope,
                    DevelopmentAdapters(fixture.build, ChildProcessDevelopmentHost(fixture.config)),
                )
            try {
                fixture.edit("one")
                saveAndAwait(orchestrator, 1)
                assertEquals("one generated-one", fixture.fetch())
                val firstPid = fixture.pid()

                fixture.edit("two")
                saveAndAwait(orchestrator, 2)
                assertEquals("two generated-one", fixture.fetch())
                val secondPid = fixture.pid()
                assertNotEquals(firstPid, secondPid, "Ktor changes restart the application process")

                fixture.edit("broken", broken = true)
                orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE))
                val failed =
                    withTimeout(60.seconds) {
                        orchestrator.state.first {
                            it.latestRequestedBuild?.value == 3L && it.phase == DevelopmentSessionPhase.BUILD_FAILED
                        }
                    }
                assertTrue(failed.hasLastValidApplication)
                assertEquals("two generated-one", fixture.fetch())
                assertEquals(secondPid, fixture.pid())

                fixture.edit("recovered")
                fixture.generate("generated-two")
                orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE))
                orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.GENERATED_SOURCE))
                withTimeout(60.seconds) {
                    orchestrator.state.first {
                        it.phase == DevelopmentSessionPhase.READY && (it.lastSuccessfulBuild?.value ?: 0) >= 4
                    }
                }
                assertEquals("recovered generated-two", fixture.fetch())
                assertNotEquals(secondPid, fixture.pid())
            } finally {
                orchestrator.stop()
                scope.cancel()
            }
            assertTrue(fixture.portIsFree(), "Stopping the session stops the Ktor child")
        }

    @Test
    fun `an occupied port is reported before Ktor starts`() =
        runBlocking {
            val fixture = Fixture(directory)
            ServerSocket(fixture.port, 1, InetAddress.getLoopbackAddress()).use {
                val result =
                    ChildProcessDevelopmentHost(fixture.config).restart(
                        DevelopmentHostRestartRequest(BuildId.of(1), ServerGeneration.of(1), ReloadLevel.COLD_RESTART),
                    )
                val failed = assertInstanceOf(DevelopmentHostRestartResult.Failed::class.java, result)
                assertEquals(listOf("KTOR-HOST-PORT-IN-USE"), failed.diagnostics.map { it.code.value })
            }
        }

    private suspend fun saveAndAwait(
        orchestrator: DevelopmentOrchestrator,
        build: Long,
    ) {
        orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE))
        withTimeout(60.seconds) {
            orchestrator.state.first {
                it.lastSuccessfulBuild?.value == build && it.phase == DevelopmentSessionPhase.READY
            }
        }
    }

    private class Fixture(
        root: Path,
    ) {
        private val live = root.resolve("live").createDirectories()
        private val source = root.resolve("Fixture.kt")
        private val generated = root.resolve("Generated.kt")
        private val classpath = System.getProperty("woge.dev.test.classpath")
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        private val client = HttpClient.newHttpClient()
        val config =
            ChildProcessHostConfig.ktor(
                ChildLaunchSpec(
                    listOf(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp",
                        "$live${File.pathSeparator}$classpath",
                        "fixture.FixtureKt",
                    ),
                    root,
                ),
                port,
            )
        val build = fixtureBuild(root, live, classpath, listOf(source, generated))

        init {
            generate("generated-one")
        }

        fun edit(
            value: String,
            broken: Boolean = false,
        ) {
            val page = if (broken) "missingSymbol" else "\"$value \" + generated()"
            source.writeText(
                """
                package fixture
                import io.ktor.server.engine.embeddedServer
                import io.ktor.server.netty.Netty
                import io.ktor.server.response.respondText
                import io.ktor.server.routing.get
                import io.ktor.server.routing.routing
                fun main() {
                    embeddedServer(Netty, port = System.getenv("PORT").toInt(), host = "127.0.0.1") {
                        routing {
                            get("/") { call.respondText($page) }
                            get("/pid") { call.respondText(ProcessHandle.current().pid().toString()) }
                        }
                    }.start(wait = true)
                }
                """.trimIndent(),
            )
        }

        fun generate(value: String) {
            generated.writeText("package fixture\nfun generated(): String = \"$value\"\n")
        }

        fun fetch(): String = get("/")

        fun pid(): String = get("/pid")

        fun portIsFree(): Boolean = PortProbe.local.isFree(port)

        private fun get(path: String): String =
            client
                .send(
                    HttpRequest
                        .newBuilder(URI("http://127.0.0.1:$port$path"))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                ).also { assertEquals(200, it.statusCode()) }
                .body()
    }
}
