package dev.woge.development.browser

import dev.woge.development.BuildId
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ServerGeneration
import dev.woge.html.CspNonce
import dev.woge.html.HtmlWriter
import dev.woge.html.externalUrl
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.stylesheet
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Explicit opt-in for a development document. Call only from the development host, never from a
 * production template. The IDs must describe the application generation that rendered this page.
 */
@ExperimentalWogeDevelopmentApi
public fun HtmlWriter.developmentClient(
    channel: DevelopmentBrowserChannel,
    renderedBuild: BuildId?,
    generation: ServerGeneration?,
    overlay: Boolean = true,
    nonce: CspNonce? = null,
) {
    metadata(
        "woge-development",
        buildJsonObject {
            put("endpoint", channel.eventsUrl)
            put("build", renderedBuild?.value?.toString() ?: "0")
            put("generation", generation?.value?.toString() ?: "0")
            put("overlay", overlay)
            put("details", channel.detailsUrl)
        }.toString(),
    )
    if (overlay) stylesheet(externalUrl("${channel.baseUrl}/overlay.css"))
    moduleScript(externalUrl("${channel.baseUrl}/client.js"), nonce = nonce)
}
