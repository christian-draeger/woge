package dev.woge.tck

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Optional real-browser extension. The caller supplies the repository's installed Playwright
 * script; ordinary JVM conformance tests do not require Node or a browser.
 */
public class NativeFormBrowserContract(
    private val script: Path,
) : AdapterTckExtension {
    override val name: String = "native-form-browser"

    override suspend fun verify(server: AdapterTckServer) {
        val process =
            ProcessBuilder("node", script.toAbsolutePath().toString(), server.origin.toString())
                .inheritIO()
                .start()
        try {
            check(
                process.waitFor(BROWSER_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            ) { "Native form browser contract timed out" }
            check(
                process.exitValue() == 0,
            ) { "Native form browser contract failed with exit code ${process.exitValue()}" }
        } finally {
            if (process.isAlive) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }
    }
}

private const val BROWSER_TIMEOUT_SECONDS = 60L
