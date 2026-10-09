package dev.woge.development.gradle

import dev.woge.development.BuildCancelled
import dev.woge.development.BuildFailed
import dev.woge.development.BuildStarted
import dev.woge.development.BuildSucceeded
import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentEvent
import dev.woge.development.DevelopmentSessionStopped
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadApplied
import dev.woge.development.ServerExited
import dev.woge.development.ServerReady
import dev.woge.development.ServerRestartFailed
import dev.woge.development.ServerRestarting

/** Short, readable status lines for the terminal that runs `./gradlew wogeDev`. */
@ExperimentalWogeDevelopmentApi
internal class TerminalReporter(
    private val print: (String) -> Unit,
) {
    fun report(event: DevelopmentEvent) {
        message(event)?.let { print("[woge] $it") }
        when (event) {
            is BuildFailed -> event.diagnostics.forEach { print("[woge]   ${describe(it)}") }
            is ServerRestartFailed -> event.diagnostics.forEach { print("[woge]   ${describe(it)}") }
            is ServerExited -> event.diagnostics.forEach { print("[woge]   ${describe(it)}") }
            else -> Unit
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun message(event: DevelopmentEvent): String? =
        when (event) {
            is BuildStarted -> "Building #${event.buildId.value} ..."
            is BuildSucceeded -> "Build #${event.buildId.value} succeeded in ${event.duration.inWholeMilliseconds} ms"
            is BuildFailed ->
                "Build #${event.buildId.value} failed. The last working version keeps running; save a fix to retry."
            is BuildCancelled -> null
            is ServerRestarting -> "Restarting the application ..."
            is ServerReady -> "Ready: ${event.urls.joinToString { it.value }}"
            is ServerRestartFailed ->
                if (event.previousApplicationRetained) {
                    "The new version did not start. The previous version keeps running."
                } else {
                    "The application did not start."
                }
            is ServerExited -> "The application stopped unexpectedly. Save a change to start it again."
            is ReloadApplied -> "Browsers refreshed with build #${event.buildId.value}"
            DevelopmentSessionStopped -> "Development session stopped."
            else -> null
        }

    private fun describe(diagnostic: DevelopmentDiagnostic): String {
        val location = diagnostic.location?.let { "${it.path.value}:${it.line}:${it.column} " } ?: ""
        return "$location${diagnostic.summary.value}"
    }
}
