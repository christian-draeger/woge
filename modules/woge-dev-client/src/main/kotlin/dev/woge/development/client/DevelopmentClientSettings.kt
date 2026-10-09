package dev.woge.development.client

import dev.woge.development.BuildId
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ServerGeneration
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * What a development page needs to connect to the session's SSE channel.
 *
 * [renderedBuild] and [generation] must describe the application that renders the page. The
 * browser compares them with the session to decide whether a reload is needed.
 */
@ExperimentalWogeDevelopmentApi
public data class DevelopmentClientSettings(
    public val eventsUrl: String,
    public val assetsUrl: String,
    public val detailsUrl: String?,
    public val renderedBuild: BuildId?,
    public val generation: ServerGeneration?,
    public val overlay: Boolean = true,
) {
    init {
        listOfNotNull(eventsUrl, assetsUrl, detailsUrl).forEach(::requireLoopbackHttp)
    }

    override fun toString(): String =
        "DevelopmentClientSettings(assetsUrl=$assetsUrl, renderedBuild=$renderedBuild, generation=$generation)"

    /** Writes the settings atomically, so a page never reads a half-written file. */
    public fun writeTo(file: Path) {
        val properties = Properties()
        properties["events"] = eventsUrl
        properties["assets"] = assetsUrl
        detailsUrl?.let { properties["details"] = it }
        properties["build"] = (renderedBuild?.value ?: 0L).toString()
        properties["generation"] = (generation?.value ?: 0L).toString()
        properties["overlay"] = overlay.toString()
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = Files.createTempFile(file.toAbsolutePath().parent, ".woge-client", ".tmp")
        try {
            Files.newBufferedWriter(temporary).use { properties.store(it, null) }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    public companion object {
        /** Returns `null` when the file is missing or invalid; a page then renders without the client. */
        public fun readFrom(file: Path): DevelopmentClientSettings? =
            try {
                val properties = Properties()
                Files.newBufferedReader(file).use(properties::load)
                DevelopmentClientSettings(
                    eventsUrl = properties.getProperty("events"),
                    assetsUrl = properties.getProperty("assets"),
                    detailsUrl = properties.getProperty("details"),
                    renderedBuild =
                        properties
                            .getProperty("build")
                            .toLong()
                            .takeIf { it > 0 }
                            ?.let(BuildId::of),
                    generation =
                        properties
                            .getProperty("generation")
                            .toLong()
                            .takeIf { it > 0 }
                            ?.let(ServerGeneration::of),
                    overlay = properties.getProperty("overlay").toBooleanStrict(),
                )
            } catch (expected: IOException) {
                null
            } catch (expected: IllegalArgumentException) {
                null
            } catch (expected: NullPointerException) {
                null
            }
    }
}

private fun requireLoopbackHttp(value: String) {
    val uri = URI(value)
    require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.userInfo == null) {
        "Development client URLs must use http://127.0.0.1"
    }
}
