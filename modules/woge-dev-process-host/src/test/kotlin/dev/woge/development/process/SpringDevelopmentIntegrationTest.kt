package dev.woge.development.process

import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.DevelopmentSessionPhase
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ServerReady
import dev.woge.development.orchestrator.DevelopmentAdapters
import dev.woge.development.orchestrator.DevelopmentOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalWogeDevelopmentApi::class)
class SpringDevelopmentIntegrationTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["servlet", "reactive"])
    @Suppress("LongMethod")
    fun `real Spring handles Kotlin and generated edits, compile recovery and consecutive saves`(webType: String) =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val fixture = Fixture(directory, webType)
            val host = ChildProcessDevelopmentHost(fixture.config)
            val orchestrator = DevelopmentOrchestrator.start(scope, DevelopmentAdapters(fixture.build, host))
            try {
                fixture.edit("one")
                saveAndAwait(orchestrator, 1)
                assertEquals("one generated-one", fixture.fetch())
                val firstPid = fixture.pid()

                fixture.edit("two")
                saveAndAwait(orchestrator, 2)
                assertEquals("two generated-one", fixture.fetch())
                assertEquals(firstPid, fixture.pid(), "DevTools must retain the JVM for a fast restart")

                val beforeFailure = Files.readString(fixture.triggerFile)
                fixture.edit("broken", broken = true)
                orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE))
                val failed =
                    withTimeout(60.seconds) {
                        orchestrator.state.first {
                            it.latestRequestedBuild?.value == 3L &&
                                it.phase == DevelopmentSessionPhase.BUILD_FAILED
                        }
                    }
                assertTrue(failed.hasLastValidApplication)
                assertEquals(beforeFailure, Files.readString(fixture.triggerFile))
                assertEquals("two generated-one", fixture.fetch())

                fixture.edit("recovered")
                saveAndAwait(orchestrator, 4)
                assertEquals("recovered generated-one", fixture.fetch())

                fixture.generate("generated-two", structural = true)
                saveAndAwait(orchestrator, 5, DevelopmentChangeKind.GENERATED_SOURCE)
                assertEquals("recovered generated-two", fixture.fetch())
                assertEquals(firstPid, fixture.pid())

                fixture.edit("latest")
                orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE))
                orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE))
                val latest =
                    withTimeout(60.seconds) {
                        orchestrator.state.first {
                            it.phase == DevelopmentSessionPhase.READY &&
                                (it.lastSuccessfulBuild?.value ?: 0) >= 6
                        }
                    }
                assertEquals("latest generated-two", fixture.fetch())
                val readyEvents =
                    orchestrator.events.replayCache
                        .map { it.event }
                        .filterIsInstance<ServerReady>()
                assertEquals(
                    readyEvents.map { it.generation.value }.sorted().distinct(),
                    readyEvents.map { it.generation.value },
                )
                assertEquals(latest.lastSuccessfulBuild, readyEvents.last().buildId)

                fixture.edit("cold")
                saveAndAwait(
                    orchestrator,
                    latest.lastSuccessfulBuild!!.value + 1,
                    DevelopmentChangeKind.BUILD_CONFIGURATION,
                )
                assertEquals("cold generated-two", fixture.fetch())
                assertFalse(firstPid == fixture.pid(), "Cold restart must replace the JVM")
            } finally {
                orchestrator.stop()
                scope.cancel()
            }
        }

    private suspend fun saveAndAwait(
        orchestrator: DevelopmentOrchestrator,
        build: Long,
        kind: DevelopmentChangeKind = DevelopmentChangeKind.KOTLIN_SOURCE,
    ) {
        orchestrator.reportChange(DevelopmentChange(kind))
        withTimeout(60.seconds) {
            orchestrator.state.first {
                it.lastSuccessfulBuild?.value == build &&
                    it.phase == DevelopmentSessionPhase.READY
            }
        }
    }

    private class Fixture(
        private val root: Path,
        webType: String,
    ) {
        private val live = root.resolve("live").createDirectories()
        private val source = root.resolve("Fixture.kt")
        private val generated = root.resolve("Generated.kt")
        private val classpath = System.getProperty("woge.dev.test.classpath")
        private val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        private val client = HttpClient.newHttpClient()
        val triggerFile: Path = live.resolve(".woge-restart")
        val config =
            ChildProcessHostConfig.springBoot(
                launch =
                    ChildLaunchSpec(
                        listOf(
                            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                            "-Dspring.devtools.restart.poll-interval=100ms",
                            "-Dspring.devtools.restart.quiet-period=50ms",
                            "-cp",
                            "$live${File.pathSeparator}$classpath",
                            "fixture.FixtureKt",
                            "--spring.main.web-application-type=$webType",
                            "--spring.main.banner-mode=off",
                        ),
                        root,
                    ),
                port = port,
                triggerFile = triggerFile,
            )

        init {
            generate("generated-one")
        }

        val build = fixtureBuild(root, live, classpath, listOf(source, generated))

        fun edit(
            value: String,
            broken: Boolean = false,
        ) {
            val pageExpression = if (broken) "missingSymbol" else "\"$value \" + generated()"
            source.writeText(
                """
                package fixture
                import org.springframework.boot.SpringApplication
                import org.springframework.boot.autoconfigure.EnableAutoConfiguration
                import org.springframework.context.annotation.Configuration
                import org.springframework.context.annotation.Import
                import org.springframework.web.bind.annotation.GetMapping
                import org.springframework.web.bind.annotation.RestController
                @Configuration(proxyBeanMethods = false)
                @EnableAutoConfiguration
                @Import(Routes::class)
                class App
                @RestController
                class Routes {
                    @GetMapping("/")
                    fun page(): String = $pageExpression
                    @GetMapping("/pid")
                    fun pid(): String = ProcessHandle.current().pid().toString()
                }
                fun main(args: Array<String>) { SpringApplication.run(App::class.java, *args) }
                """.trimIndent(),
            )
        }

        fun generate(
            value: String,
            structural: Boolean = false,
        ) {
            generated.writeText(
                "package fixture\n" +
                    if (structural) {
                        "class Generated { fun render(): String = \"$value\" }\n" +
                            "fun generated() = Generated().render()\n"
                    } else {
                        "fun generated(): String = \"$value\"\n"
                    },
            )
        }

        fun fetch(): String = get("/")

        fun pid(): String = get("/pid")

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
