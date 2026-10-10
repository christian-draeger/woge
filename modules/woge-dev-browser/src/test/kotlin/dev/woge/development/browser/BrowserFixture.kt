package dev.woge.development.browser

import com.sun.net.httpserver.HttpServer
import dev.woge.css.declarations
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
import dev.woge.development.ReloadLevel
import dev.woge.development.ServerGeneration
import dev.woge.development.orchestrator.DevelopmentAdapters
import dev.woge.development.orchestrator.DevelopmentBuildAdapter
import dev.woge.development.orchestrator.DevelopmentBuildResult
import dev.woge.development.orchestrator.DevelopmentFrontendAdapter
import dev.woge.development.orchestrator.DevelopmentHostAdapter
import dev.woge.development.orchestrator.DevelopmentHostRestartRequest
import dev.woge.development.orchestrator.DevelopmentHostRestartResult
import dev.woge.development.orchestrator.DevelopmentOrchestrator
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.renderHtml
import dev.woge.html.stylesheet
import dev.woge.html.textarea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.URLDecoder
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
        val resetName = AtomicBoolean(false)
        val servedBuild = AtomicReference<BuildId?>(null)
        val servedGeneration = AtomicReference<ServerGeneration?>(null)
        val cssColor = AtomicReference("rgb(0, 0, 0)")
        val cssBroken = AtomicBoolean(false)
        val adapters =
            DevelopmentAdapters(
                DevelopmentBuildAdapter { request ->
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
                    } else if (request.changes.all { it.kind == DevelopmentChangeKind.CSS }) {
                        DevelopmentBuildResult.Succeeded(ReloadLevel.HOT_ASSET)
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
                DevelopmentFrontendAdapter { buildId, level ->
                    val supported = level == ReloadLevel.HOT_ASSET || level == ReloadLevel.DOCUMENT_REFRESH
                    if (supported) servedBuild.set(buildId)
                    supported
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
        server.createContext("/styles") { exchange ->
            val body =
                if (exchange.requestURI.path == "/styles/imported.css") {
                    "#build { color: ${cssColor.get()}; }"
                } else {
                    "@import url(\"imported.css\");\nh1 { color: ${cssColor.get()}; }"
                }
            val bytes = body.toByteArray()
            exchange.responseHeaders.set("Content-Type", "text/css")
            exchange.responseHeaders.set("Cache-Control", "no-store")
            exchange.sendResponseHeaders(if (cssBroken.get()) 404 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/") { exchange ->
            val isControl = exchange.requestURI.path == "/control"
            val command = exchange.requestURI.rawQuery
            val body =
                if (isControl) {
                    val kind = control(command, fail, resetName, cssColor, cssBroken)
                    orchestrator.reportChange(DevelopmentChange(kind))
                    "ok"
                } else {
                    renderHtml {
                        doctype()
                        element("html") {
                            element("head") {
                                stylesheet(applicationUrl("/styles/main.css"))
                                developmentClient(channel, servedBuild.get(), servedGeneration.get())
                            }
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
                                        attribute("data-woge-state-key", "name")
                                        attribute("data-woge-development-preserve", "")
                                        if (resetName.get()) attribute("data-woge-state", "reset")
                                    }
                                    stateControls()
                                }
                                element("div", { styles(declarations("height: 2400px;")) }) { text("Scroll fixture") }
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

    private fun control(
        command: String?,
        fail: AtomicBoolean,
        resetName: AtomicBoolean,
        cssColor: AtomicReference<String>,
        cssBroken: AtomicBoolean,
    ): DevelopmentChangeKind {
        when {
            command == "fail" -> fail.set(true)
            command == "save" || command == "reset" -> {
                fail.set(false)
                resetName.set(command == "reset")
            }
            command == "css-broken" -> cssBroken.set(true)
            command?.startsWith("css=") == true -> {
                cssColor.set(URLDecoder.decode(command.removePrefix("css="), Charsets.UTF_8))
                cssBroken.set(false)
            }
        }
        val stylesheet = command == "css-broken" || command?.startsWith("css=") == true
        return if (stylesheet) DevelopmentChangeKind.CSS else DevelopmentChangeKind.KOTLIN_SOURCE
    }

    private fun HtmlWriter.stateControls() {
        element("label", { attribute("for", "notes") }) { text("Notes") }
        textarea("Server notes") {
            attribute("id", "notes")
            attribute("data-woge-state-key", "notes")
            attribute("data-woge-development-preserve", "")
        }
        for (type in listOf("checkbox", "radio", "password", "file", "hidden")) {
            voidElement("input") {
                attribute("id", type)
                attribute("type", type)
                attribute("data-woge-state-key", type)
                attribute("data-woge-development-preserve", "")
            }
        }
        element("select", {
            attribute("id", "choice")
            boolean("multiple")
            attribute("data-woge-state-key", "choice")
            attribute("data-woge-development-preserve", "")
        }) {
            for (value in listOf("one", "two", "three")) {
                element("option", { attribute("value", value) }) { text(value) }
            }
        }
        voidElement("input") {
            attribute("id", "private")
            attribute("data-woge-state-key", "private")
        }
    }
}
