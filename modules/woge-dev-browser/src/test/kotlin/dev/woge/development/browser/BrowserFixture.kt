package dev.woge.development.browser

import com.sun.net.httpserver.HttpServer
import dev.woge.development.BuildId
import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentDiagnosticCode
import dev.woge.development.DevelopmentDiagnosticSeverity
import dev.woge.development.DevelopmentDiagnosticSummary
import dev.woge.development.DevelopmentSourceLocation
import dev.woge.development.DevelopmentSourcePath
import dev.woge.development.DevelopmentUrl
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ServerGeneration
import dev.woge.development.orchestrator.DevelopmentAdapters
import dev.woge.development.orchestrator.DevelopmentBuildAdapter
import dev.woge.development.orchestrator.DevelopmentBuildResult
import dev.woge.development.orchestrator.DevelopmentHostAdapter
import dev.woge.development.orchestrator.DevelopmentHostRestartRequest
import dev.woge.development.orchestrator.DevelopmentHostRestartResult
import dev.woge.development.orchestrator.DevelopmentOrchestrator
import dev.woge.html.renderHtml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Test-only application/control endpoints; never included in a development or production artifact. */
@OptIn(ExperimentalWogeDevelopmentApi::class)
internal object BrowserFixture {
    @JvmStatic
    @Suppress("LongMethod")
    fun main(args: Array<String>) {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val fail = AtomicBoolean(false)
        val servedBuild = AtomicReference<BuildId?>(null)
        val servedGeneration = AtomicReference<ServerGeneration?>(null)
        val adapters =
            DevelopmentAdapters(
                DevelopmentBuildAdapter {
                    delay(100)
                    if (fail.get()) {
                        DevelopmentBuildResult.Failed(
                            listOf(
                                DevelopmentDiagnostic(
                                    DevelopmentDiagnosticCode.of("WOGE-TEST-COMPILE"),
                                    DevelopmentDiagnosticSeverity.ERROR,
                                    DevelopmentDiagnosticSummary.of(
                                        "Type mismatch <img src=x onerror=alert(1)>. Fix the source and save again.",
                                    ),
                                    DevelopmentSourceLocation(DevelopmentSourcePath.of("src/main/Page.kt"), 4, 8),
                                ),
                            ),
                        )
                    } else {
                        DevelopmentBuildResult.Succeeded()
                    }
                },
                object : DevelopmentHostAdapter {
                    override suspend fun restart(request: DevelopmentHostRestartRequest): DevelopmentHostRestartResult {
                        delay(100)
                        servedBuild.set(request.buildId)
                        servedGeneration.set(request.generation)
                        return DevelopmentHostRestartResult.Ready(
                            listOf(DevelopmentUrl.local("http://127.0.0.1:4273/")),
                        )
                    }

                    override suspend fun shutdown() = Unit
                },
            )
        val orchestrator = DevelopmentOrchestrator.start(scope, adapters)
        val channel =
            DevelopmentBrowserChannel(
                scope,
                orchestrator,
                setOf("http://127.0.0.1:4273"),
                port = 4274,
                details = DevelopmentBuildDetails { "Test-only compiler detail output" },
            )
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 4273), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/") { exchange ->
            val isControl = exchange.requestURI.path == "/control"
            val command = exchange.requestURI.rawQuery
            val body =
                if (isControl) {
                    if (command == "fail") {
                        fail.set(true)
                    } else if (command == "save") {
                        fail.set(false)
                    }
                    orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE))
                    "ok"
                } else {
                    renderHtml {
                        doctype()
                        element("html") {
                            element("head") { developmentClient(channel, servedBuild.get(), servedGeneration.get()) }
                            element("body") {
                                element("h1") { text("Development fixture") }
                                element("p", { attribute("id", "build") }) {
                                    text(
                                        servedBuild.get()?.value?.toString() ?: "0",
                                    )
                                }
                                element("form") {
                                    element("label", { attribute("for", "name") }) { text("Name") }
                                    voidElement("input") {
                                        attribute("id", "name")
                                        attribute("name", "name")
                                    }
                                }
                            }
                        }
                    }
                }
            val bytes = body.toByteArray()
            exchange.responseHeaders.set("Content-Type", if (isControl) "text/plain" else "text/html")
            exchange.responseHeaders.set("Cache-Control", "no-store")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                server.stop(0)
                executor.shutdownNow()
                channel.close()
                runBlocking { orchestrator.stop() }
            },
        )
        server.start()
        CountDownLatch(1).await()
    }
}
