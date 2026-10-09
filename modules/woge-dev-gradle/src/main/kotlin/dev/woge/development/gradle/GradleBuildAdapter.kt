package dev.woge.development.gradle

import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.browser.DevelopmentBuildDetails
import dev.woge.development.orchestrator.DevelopmentBuildAdapter
import dev.woge.development.orchestrator.DevelopmentBuildRequest
import dev.woge.development.orchestrator.DevelopmentBuildResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs one Gradle build in a separate Gradle client, like typing `./gradlew classes` in a terminal.
 *
 * Gradle keeps its own incremental Kotlin compilation and build cache. A cancelled build stops the
 * client process, which tells the Gradle daemon to cancel the build.
 */
@ExperimentalWogeDevelopmentApi
public class GradleBuildAdapter(
    private val projectDirectory: Path,
    private val command: List<String>,
    private val detailsLimit: Int = DEFAULT_DETAILS_LIMIT,
) : DevelopmentBuildAdapter,
    DevelopmentBuildDetails {
    init {
        require(command.isNotEmpty()) { "The Gradle command must not be empty" }
        require(detailsLimit > 0) { "The details limit must be positive" }
    }

    @Volatile
    private var lastOutput: String = ""

    override fun read(): String = lastOutput

    override suspend fun build(request: DevelopmentBuildRequest): DevelopmentBuildResult {
        val output = BoundedOutput(detailsLimit)
        val process =
            withContext(Dispatchers.IO) {
                ProcessBuilder(command)
                    .directory(projectDirectory.toFile())
                    .redirectErrorStream(true)
                    .start()
            }
        val reader =
            thread(name = "woge-dev-gradle-output", isDaemon = true) {
                process.inputStream.bufferedReader().useLines { lines -> lines.forEach(output::add) }
            }
        try {
            val exit = process.onExit().await().exitValue()
            withContext(Dispatchers.IO) { reader.join(READER_JOIN_MILLIS) }
            lastOutput = output.text()
            return if (exit == 0) {
                DevelopmentBuildResult.Succeeded()
            } else {
                DevelopmentBuildResult.Failed(KotlinDiagnostics.parse(output.lines(), projectDirectory))
            }
        } finally {
            if (process.isAlive) {
                withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { stop(process) }
            }
        }
    }

    private fun stop(process: Process) {
        val descendants = process.descendants().toList()
        process.destroy()
        descendants.forEach(ProcessHandle::destroy)
        if (!process.waitFor(STOP_GRACE_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    private class BoundedOutput(
        private val limit: Int,
    ) {
        private val buffer = ArrayDeque<String>()
        private var size = 0

        @Synchronized
        fun add(line: String) {
            buffer.addLast(line)
            size += line.length + 1
            while (size > limit && buffer.size > 1) size -= buffer.removeFirst().length + 1
        }

        @Synchronized
        fun lines(): List<String> = buffer.toList()

        @Synchronized
        fun text(): String = buffer.joinToString("\n")
    }

    private companion object {
        const val DEFAULT_DETAILS_LIMIT = 256 * 1024
        const val STOP_GRACE_SECONDS = 5L
        const val READER_JOIN_MILLIS = 2_000L
    }
}
