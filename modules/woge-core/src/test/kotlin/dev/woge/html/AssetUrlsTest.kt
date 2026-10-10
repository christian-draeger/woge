package dev.woge.html

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class AssetUrlsTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `production resolves hashed paths while development keeps plain registered URLs`() {
        val logical = applicationUrl("/css/site.css")
        val production = read()
        assertEquals("/_woge/assets/$HASH/css/site.css", production.url(logical).value)
        assertEquals("/css/site.css", read(development = true).url(logical).value)
        assertEquals(
            """<link rel="stylesheet" href="/_woge/assets/$HASH/css/site.css">""",
            renderHtml { stylesheet(production.url(logical)) },
        )
        assertThrows(IllegalArgumentException::class.java) { production.url(applicationUrl("/missing.css")) }
        assertTrue(logical in production)
        assertFalse(applicationUrl("/missing.css") in production)
        assertThrows(
            IllegalArgumentException::class.java,
        ) { read(development = true).url(applicationUrl("/missing.css")) }
    }

    @Test
    fun `invalid schema hashes URLs fields and traversal fail explicitly`() {
        for (manifest in listOf(
            MANIFEST.replace("schemaVersion=1", "schemaVersion=2"),
            MANIFEST.replace(HASH, "short"),
            MANIFEST.replace("asset.", "unknown."),
            MANIFEST.replace("/_woge/assets/", "https://example.com/"),
            MANIFEST.replace("/css/site.css", "/../site.css"),
            MANIFEST.replace("/css/site.css", "/%2e%2e/site.css"),
            MANIFEST.replace("/css/site.css", "/css/site.css?query"),
        )) {
            assertThrows(IllegalArgumentException::class.java) { read(manifest) }
        }
    }

    @Test
    fun `classpath loading rejects missing or multiple application manifests`() {
        val first = root.resolve("one/META-INF/woge/assets.properties")
        Files.createDirectories(first.parent)
        Files.writeString(first, MANIFEST)
        URLClassLoader(arrayOf(root.resolve("one").toUri().toURL()), null).use { loader ->
            assertEquals(HASH, AssetUrls.load(loader).bundleHash)
        }
        val second = root.resolve("two/META-INF/woge/assets.properties")
        Files.createDirectories(second.parent)
        Files.writeString(second, MANIFEST)
        URLClassLoader(arrayOf(root.resolve("one").toUri().toURL(), root.resolve("two").toUri().toURL()), null).use {
            assertThrows(IllegalArgumentException::class.java) { AssetUrls.load(it) }
        }
        URLClassLoader(emptyArray(), null).use {
            assertThrows(IllegalArgumentException::class.java) { AssetUrls.load(it) }
        }
    }

    private fun read(
        manifest: String = MANIFEST,
        development: Boolean = false,
    ): AssetUrls = manifest.byteInputStream().use { AssetUrls.read(it, development) }

    private companion object {
        val HASH = "a".repeat(64)
        val MANIFEST = "schemaVersion=1\nbundleHash=$HASH\nasset./css/site.css=/_woge/assets/$HASH/css/site.css\n"
    }
}
