package dev.woge.development.spring

import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentDiagnosticCode
import dev.woge.development.DevelopmentDiagnosticSeverity
import dev.woge.development.DevelopmentDiagnosticSummary
import dev.woge.development.DevelopmentSessionPhase
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ServerReady
import dev.woge.development.orchestrator.DevelopmentAdapters
import dev.woge.development.orchestrator.DevelopmentBuildAdapter
import dev.woge.development.orchestrator.DevelopmentBuildResult
import dev.woge.development.orchestrator.DevelopmentOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
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
            val host = SpringDevelopmentHost(fixture.config)
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

                val beforeFailure = Files.readString(fixture.config.triggerFile)
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
                assertEquals(beforeFailure, Files.readString(fixture.config.triggerFile))
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
        val config =
            SpringDevelopmentHostConfig(
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
                triggerFile = live.resolve(".woge-restart"),
            )

        init {
            generate("generated-one")
        }

        val build =
            DevelopmentBuildAdapter {
                withContext(Dispatchers.IO) {
                    val destination = root.resolve("candidate-${it.buildId.value}").createDirectories()
                    val messages = ByteArrayOutputStream()
                    val exit =
                        PrintStream(messages).use { output ->
                            K2JVMCompiler().exec(
                                output,
                                "-no-stdlib",
                                "-no-reflect",
                                "-jvm-target",
                                "17",
                                "-classpath",
                                classpath,
                                "-d",
                                destination.toString(),
                                source.toString(),
                                generated.toString(),
                            )
                        }
                    if (exit == ExitCode.OK) {
                        Files.walk(destination).use { paths ->
                            paths.filter { Files.isRegularFile(it) }.forEach { file ->
                                val target = live.resolve(destination.relativize(file))
                                target.parent.createDirectories()
                                file.copyTo(target, overwrite = true)
                            }
                        }
                        DevelopmentBuildResult.Succeeded()
                    } else {
                        DevelopmentBuildResult.Failed(
                            listOf(
                                DevelopmentDiagnostic(
                                    DevelopmentDiagnosticCode.of("FIXTURE-COMPILE-FAILED"),
                                    DevelopmentDiagnosticSeverity.ERROR,
                                    DevelopmentDiagnosticSummary.of("Fix the Kotlin source and save again."),
                                ),
                            ),
                        )
                    }
                }
            }

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
