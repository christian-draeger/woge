package dev.woge.tck

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Dedicated opt-in browser contract; it never makes the ordinary JVM gate require Node. */
public class NativeDpuBrowserContract(
    private val script: Path,
) : AdapterTckExtension {
    override val name: String = "initial-document-dpu-browser"

    override suspend fun verify(server: AdapterTckServer) {
        val process =
            ProcessBuilder("node", script.toAbsolutePath().toString(), server.origin.toString())
                .inheritIO()
                .start()
        try {
            check(process.waitFor(DPU_BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Initial document browser contract did not finish"
            }
            check(process.exitValue() == 0) { "Initial document browser contract exit code ${process.exitValue()}" }
        } finally {
            if (process.isAlive) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }
    }
}

private const val DPU_BROWSER_TIMEOUT_SECONDS = 60L
