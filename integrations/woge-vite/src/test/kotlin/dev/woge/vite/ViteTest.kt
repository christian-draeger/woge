package dev.woge.vite

import dev.woge.html.AssetUrls
import dev.woge.html.cspNonce
import dev.woge.html.renderHtml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViteTest {
    @Test
    fun `production loads the hashed stylesheet and script of the entry`() {
        val vite = ViteAssets(assets("/vite/main.js", "/vite/main.css"), developmentOrigin = null)

        assertEquals(
            """<link rel="stylesheet" href="/_woge/assets/$HASH/vite/main.css">""" +
                """<script type="module" src="/_woge/assets/$HASH/vite/main.js"></script>""",
            renderHtml { viteEntry(vite, ViteEntry("main.ts")) },
        )
    }

    @Test
    fun `an entry without CSS gets only its script`() {
        val vite = ViteAssets(assets("/front/admin.js"), developmentOrigin = "", outputPath = "front")

        assertEquals(
            """<script type="module" src="/_woge/assets/$HASH/front/admin.js"></script>""",
            renderHtml { viteEntry(vite, ViteEntry("admin.tsx")) },
        )
    }

    @Test
    fun `development loads the Vite client and the entry source from the dev server`() {
        val vite = ViteAssets(assets(), developmentOrigin = "http://127.0.0.1:5173")

        assertEquals(
            """<script type="module" src="http://127.0.0.1:5173/@vite/client"></script>""" +
                """<script type="module" src="http://127.0.0.1:5173/main.ts"></script>""",
            renderHtml { viteEntry(vite, ViteEntry("main.ts")) },
        )
    }

    @Test
    fun `development shares the response nonce with the styles the Vite client injects`() {
        val vite = ViteAssets(assets(), developmentOrigin = "http://127.0.0.1:5173")
        val html = renderHtml { viteEntry(vite, ViteEntry("main.ts"), nonce = cspNonce("abc123")) }

        assertTrue(html.startsWith("""<meta property="csp-nonce" nonce="abc123">"""), html)
        assertTrue(html.contains("""<script type="module" src="http://127.0.0.1:5173/@vite/client" nonce="abc123">"""))
    }

    @Test
    fun `a missing production build fails instead of rendering a broken URL`() {
        val vite = ViteAssets(assets(), developmentOrigin = null)

        assertThrows(IllegalArgumentException::class.java) { renderHtml { viteEntry(vite, ViteEntry("main.ts")) } }
    }

    @Test
    fun `entries origins and output paths are validated`() {
        listOf("src/main.ts", "../main.ts", "main.css", "", ".ts").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { ViteEntry(name) }
        }
        listOf("https://127.0.0.1:5173", "http://localhost:5173", "http://127.0.0.1:5173/x", "http://127.0.0.1")
            .forEach { origin ->
                assertThrows(IllegalArgumentException::class.java) { ViteAssets(assets(), developmentOrigin = origin) }
            }
        listOf("/vite", "vite/", "../vite", "a b").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                ViteAssets(assets(), developmentOrigin = null, outputPath = path)
            }
        }
    }

    private fun assets(vararg paths: String): AssetUrls =
        AssetUrls.read(
            (listOf("schemaVersion=1", "bundleHash=$HASH") + paths.map { "asset.$it=/_woge/assets/$HASH$it" })
                .joinToString("\n")
                .byteInputStream(),
        )

    private companion object {
        const val HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
