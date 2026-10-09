package dev.woge.development.browser

import dev.woge.development.BuildId
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ServerGeneration
import dev.woge.development.client.DevelopmentClientSettings
import dev.woge.development.client.developmentClient
import dev.woge.html.CspNonce
import dev.woge.html.HtmlWriter

/** The settings a page rendered by [renderedBuild] and [generation] needs to connect to this channel. */
@ExperimentalWogeDevelopmentApi
public fun DevelopmentBrowserChannel.clientSettings(
    renderedBuild: BuildId?,
    generation: ServerGeneration?,
    overlay: Boolean = true,
): DevelopmentClientSettings =
    DevelopmentClientSettings(eventsUrl, baseUrl, detailsUrl, renderedBuild, generation, overlay)

/**
 * Explicit opt-in for a development document in the same process as the channel. Applications
 * started by `wogeDev` receive the client automatically through `woge-dev-client`.
 */
@ExperimentalWogeDevelopmentApi
public fun HtmlWriter.developmentClient(
    channel: DevelopmentBrowserChannel,
    renderedBuild: BuildId?,
    generation: ServerGeneration?,
    overlay: Boolean = true,
    nonce: CspNonce? = null,
) {
    developmentClient(channel.clientSettings(renderedBuild, generation, overlay), nonce)
}
