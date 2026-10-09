package dev.woge.development.client

import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.html.CspNonce
import dev.woge.html.DevelopmentHeadContribution
import dev.woge.html.HtmlWriter
import dev.woge.html.WogeDevelopmentHook
import dev.woge.html.externalUrl
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.stylesheet
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 * Writes the development client's configuration, script and optional overlay stylesheet.
 * Only development tooling calls this; production templates never do.
 */
@ExperimentalWogeDevelopmentApi
public fun HtmlWriter.developmentClient(
    settings: DevelopmentClientSettings,
    nonce: CspNonce? = null,
) {
    metadata(
        "woge-development",
        buildJsonObject {
            put("endpoint", settings.eventsUrl)
            put("build", settings.renderedBuild?.value?.toString() ?: "0")
            put("generation", settings.generation?.value?.toString() ?: "0")
            put("overlay", settings.overlay)
            put("details", settings.detailsUrl)
        }.toString(),
    )
    if (settings.overlay) stylesheet(externalUrl("${settings.assetsUrl}/overlay.css"), nonce = nonce)
    moduleScript(externalUrl("${settings.assetsUrl}/client.js"), nonce = nonce)
}

/**
 * Adds the client to every document head while `wogeDev` runs.
 *
 * `wogeDev` passes the settings file through `WOGE_DEV_CLIENT_FILE` and rewrites it before each new
 * application generation or document refresh becomes visible to browsers.
 */
@ExperimentalWogeDevelopmentApi
@OptIn(WogeDevelopmentHook::class)
public class FileDevelopmentHeadContribution internal constructor(
    private val file: Path?,
) : DevelopmentHeadContribution {
    public constructor() : this(System.getenv(CLIENT_FILE_VARIABLE)?.let(Path::of))

    private class Cached(
        val modified: FileTime,
        val settings: DevelopmentClientSettings?,
    )

    @Volatile
    private var cached: Cached? = null

    override fun writeTo(
        head: HtmlWriter,
        nonce: CspNonce?,
    ) {
        current()?.let { head.developmentClient(it, nonce) }
    }

    /** The settings of the running session, or `null` outside `wogeDev`. */
    public fun current(): DevelopmentClientSettings? {
        val source = file ?: return null
        val modified = runCatching { Files.getLastModifiedTime(source) }.getOrNull()
        val known = cached?.takeIf { modified != null && it.modified == modified }
        return when {
            modified == null -> null
            known != null -> known.settings
            else -> DevelopmentClientSettings.readFrom(source).also { cached = Cached(modified, it) }
        }
    }

    public companion object {
        public const val CLIENT_FILE_VARIABLE: String = "WOGE_DEV_CLIENT_FILE"
    }
}
