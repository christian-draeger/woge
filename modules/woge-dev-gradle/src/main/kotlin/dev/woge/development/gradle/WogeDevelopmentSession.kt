package dev.woge.development.gradle

import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.browser.DevelopmentBrowserChannel
import dev.woge.development.browser.clientSettings
import dev.woge.development.client.FileDevelopmentHeadContribution
import dev.woge.development.mcp.DevelopmentMcpServer
import dev.woge.development.orchestrator.DevelopmentAdapters
import dev.woge.development.orchestrator.DevelopmentHostAdapter
import dev.woge.development.orchestrator.DevelopmentManifestSource
import dev.woge.development.orchestrator.DevelopmentOrchestrator
import dev.woge.development.process.ChildLaunchSpec
import dev.woge.development.process.ChildProcessDevelopmentHost
import dev.woge.development.process.ChildProcessHostConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

/**
 * One `wogeDev` session: watch sources, build with Gradle, run the Spring Boot or Ktor child and keep
 * browsers informed. Cancel the calling coroutine to stop everything, including the child.
 */
@ExperimentalWogeDevelopmentApi
public class WogeDevelopmentSession(
    private val settings: WogeDevelopmentSettings,
    private val print: (String) -> Unit = ::println,
    private val hostFactory: (ChildProcessHostConfig) -> DevelopmentHostAdapter = { config ->
        ChildProcessDevelopmentHost(config, ForwardingChildLauncher(print))
    },
) {
    public suspend fun run() {
        val vite = settings.vite?.let { ViteDevServer(it, settings.port, print) }
        val viteChild = vite?.start()
        try {
            runWoge()
        } finally {
            if (vite != null && viteChild != null) withContext(NonCancellable) { vite.stop(viteChild) }
        }
    }

    private suspend fun runWoge(): Unit =
        coroutineScope {
            Files.createDirectories(settings.triggerFile.parent)
            settings.clientFile.deleteIfExists()
            val clientFile = AtomicReference<ClientFile>()
            val build = GradleBuildAdapter(settings.projectDirectory, settings.buildCommand)
            val host =
                ClientFileHostAdapter(
                    LazyHostAdapter(settings.host) { mainClass()?.let { hostFactory(hostConfig(it)) } },
                ) {
                    clientFile.get()
                }
            val orchestrator =
                DevelopmentOrchestrator.start(
                    this,
                    DevelopmentAdapters(
                        build,
                        host,
                        ClientFileFrontendAdapter { clientFile.get() },
                        if (settings.mcp) FileManifestSource(settings.manifestFile) else DevelopmentManifestSource.none,
                    ),
                )
            val channel =
                DevelopmentBrowserChannel(
                    this,
                    orchestrator,
                    setOf("http://127.0.0.1:${settings.port}", "http://localhost:${settings.port}"),
                    details = build,
                )
            clientFile.set(
                ClientFile(settings.clientFile) { buildId, generation -> channel.clientSettings(buildId, generation) },
            )
            val reporter = TerminalReporter(print)
            launch { orchestrator.events.collect { reporter.report(it.event) } }
            val mcp = if (settings.mcp) startMcp(orchestrator) else null
            try {
                watch(orchestrator)
            } finally {
                channel.close()
                mcp?.close()
                if (mcp != null) settings.mcpFile.deleteIfExists()
            }
        }

    private fun startMcp(orchestrator: DevelopmentOrchestrator): DevelopmentMcpServer {
        val server = DevelopmentMcpServer(orchestrator, settings.mcpPort)
        writeMcpConnection(settings.mcpFile, server)
        print(
            "[woge] Experimental MCP endpoint for coding agents: ${server.url} " +
                "(URL and token in ${settings.projectDirectory.relativize(settings.mcpFile)})",
        )
        return server
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.watch(orchestrator: DevelopmentOrchestrator) {
        val watcher = SourceWatcher(settings.projectDirectory, settings.watchRoots, settings.buildFiles)
        orchestrator.reportChange(DevelopmentChange(DevelopmentChangeKind.UNKNOWN))
        while (isActive) {
            delay(settings.pollIntervalMillis)
            val changes = watcher.poll()
            if (changes.isEmpty()) continue
            if (changes.any { it.kind == DevelopmentChangeKind.BUILD_CONFIGURATION }) {
                print(
                    "[woge] A build file changed. Woge restarts the application; " +
                        "restart wogeDev if you changed dependencies or plugins.",
                )
            }
            orchestrator.reportChanges(changes)
        }
    }

    private companion object {
        /** Read by `ViteAssets` in the application, so pages load modules from the Vite dev server. */
        const val VITE_ORIGIN_VARIABLE = "WOGE_VITE_ORIGIN"
    }

    private fun mainClass(): String? =
        runCatching { settings.mainClassFile.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun hostConfig(mainClass: String): ChildProcessHostConfig {
        val launch =
            ChildLaunchSpec(
                command = settings.childCommand(mainClass),
                workingDirectory = settings.projectDirectory,
                environment =
                    listOfNotNull(
                        FileDevelopmentHeadContribution.CLIENT_FILE_VARIABLE to settings.clientFile.toString(),
                        settings.vite?.let { VITE_ORIGIN_VARIABLE to it.origin },
                    ).toMap(),
            )
        return when (settings.host) {
            WogeDevelopmentHost.SPRING_BOOT ->
                ChildProcessHostConfig.springBoot(launch, settings.port, settings.triggerFile, settings.fastRestart)
            WogeDevelopmentHost.KTOR -> ChildProcessHostConfig.ktor(launch, settings.port)
        }
    }
}
