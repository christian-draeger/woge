package dev.woge.development.browser

import dev.woge.development.BuildId
import dev.woge.development.DevelopmentSessionState
import dev.woge.development.ExperimentalWogeDevelopmentApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@OptIn(ExperimentalWogeDevelopmentApi::class)
internal data class BrowserSnapshot(
    val sequence: Long,
    val state: DevelopmentSessionState,
    val renderedBuild: BuildId?,
    /** Last build whose HTML reached the browser; newer rendered builds only changed stylesheets. */
    val documentBuild: BuildId? = renderedBuild,
) {
    fun json(session: String): String =
        buildJsonObject {
            put("version", 1)
            put("session", session)
            put("sequence", sequence.toString())
            put("build", state.latestRequestedBuild?.value?.toString() ?: "0")
            put("generation", state.activeServerGeneration?.value?.toString() ?: "0")
            put("renderedBuild", renderedBuild?.value?.toString() ?: "0")
            put("documentBuild", documentBuild?.value?.toString() ?: "0")
            put("phase", state.phase.name)
            put("diagnosticsTruncated", state.diagnostics.size > MAX_DIAGNOSTICS)
            putJsonArray("diagnostics") {
                state.diagnostics.take(MAX_DIAGNOSTICS).forEach { diagnostic ->
                    add(
                        buildJsonObject {
                            put("code", diagnostic.code.value)
                            put("summary", diagnostic.summary.value)
                            diagnostic.location?.let { location ->
                                put("path", location.path.value)
                                put("line", location.line)
                                put("column", location.column)
                            }
                        },
                    )
                }
            }
        }.toString()

    private companion object {
        const val MAX_DIAGNOSTICS = 20
    }
}
