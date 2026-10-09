package dev.woge.development.client

import dev.woge.development.BuildId
import dev.woge.development.ServerGeneration
import dev.woge.html.renderHtml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DevelopmentClientTest {
    private val settings =
        DevelopmentClientSettings(
            eventsUrl = "http://127.0.0.1:4100/events?token=secret",
            assetsUrl = "http://127.0.0.1:4100",
            detailsUrl = null,
            renderedBuild = BuildId.of(3),
            generation = ServerGeneration.of(2),
        )

    @Test
    fun `settings survive an atomic round trip`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("nested/client.properties")

        settings.writeTo(file)

        assertEquals(settings, DevelopmentClientSettings.readFrom(file))
        assertEquals(listOf("client.properties"), Files.list(file.parent).map { it.fileName.toString() }.toList())
    }

    @Test
    fun `missing or invalid files render no client`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("client.properties")
        assertNull(DevelopmentClientSettings.readFrom(file))

        Files.writeString(file, "events=http://example.com/events\nassets=http://127.0.0.1:1\nbuild=1\n")
        assertNull(DevelopmentClientSettings.readFrom(file))
        assertEquals("", renderHtml { FileDevelopmentHeadContribution(file).writeTo(this) })
    }

    @Test
    fun `only loopback HTTP endpoints are accepted`() {
        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(assetsUrl = "https://127.0.0.1:4100")
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.copy(eventsUrl = "http://localhost.evil.test/events")
        }
    }

    @Test
    fun `the head contribution renders the current file with typed tags`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("client.properties")
        val contribution = FileDevelopmentHeadContribution(file)
        settings.writeTo(file)

        val first = renderHtml { contribution.writeTo(this) }
        settings.copy(renderedBuild = BuildId.of(4), overlay = false).writeTo(file)
        Files.setLastModifiedTime(
            file,
            java.nio.file.attribute.FileTime
                .fromMillis(System.currentTimeMillis() + 5_000),
        )
        val second = renderHtml { contribution.writeTo(this) }

        assertTrue("&quot;build&quot;:&quot;3&quot;" in first, first)
        assertTrue("http://127.0.0.1:4100/overlay.css" in first, first)
        assertTrue("http://127.0.0.1:4100/client.js" in first, first)
        assertTrue("&quot;build&quot;:&quot;4&quot;" in second, second)
        assertTrue("overlay.css" !in second, second)
    }
}
