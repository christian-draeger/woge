package dev.woge.vite

import dev.woge.html.AssetUrls
import dev.woge.html.CspNonce
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.cspNonceMetadata
import dev.woge.html.externalUrl
import dev.woge.html.moduleScript
import dev.woge.html.stylesheet
import java.net.URI

/**
 * A Vite entry module, such as `main.ts`. Entries live directly in the frontend folder
 * (`src/main/frontend` by default); everything else is imported from them.
 */
@JvmInline
public value class ViteEntry(
    public val fileName: String,
) {
    init {
        require(ENTRY.matches(fileName)) {
            "A Vite entry is a file name in the frontend folder, such as main.ts, but was '$fileName'"
        }
    }

    /** The stable production name without extension: `main.ts` becomes `main.js` and `main.css`. */
    internal val outputName: String get() = fileName.substringBeforeLast('.')

    private companion object {
        val ENTRY = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*\\.(ts|tsx|js|jsx|mts|mjs)")
    }
}

/**
 * Where a page loads its Vite entries from.
 *
 * While `wogeDev` runs, it sets [DEVELOPMENT_ORIGIN_VARIABLE] and pages load the modules straight from
 * the Vite dev server, which hot-updates them. Otherwise they come from Woge's content-hashed asset
 * tree, where the `dev.woge.vite` Gradle plugin put the `vite build` output below [outputPath].
 */
public class ViteAssets(
    private val assets: AssetUrls,
    developmentOrigin: String? = System.getenv(DEVELOPMENT_ORIGIN_VARIABLE),
    private val outputPath: String = DEFAULT_OUTPUT_PATH,
) {
    /** The Vite dev server origin, such as `http://127.0.0.1:5173`, or `null` for built assets. */
    public val developmentOrigin: String? = developmentOrigin?.takeIf(String::isNotEmpty)?.also(::requireLoopback)

    init {
        require(OUTPUT_PATH.matches(outputPath)) { "The Vite output path must be a relative folder, such as vite" }
    }

    internal fun write(
        html: HtmlWriter,
        entry: ViteEntry,
        nonce: CspNonce?,
    ) {
        val origin = developmentOrigin
        if (origin != null) {
            // Browsers run a module URL once, so repeating the client for a second entry is harmless.
            // The Vite client injects imported CSS as <style> elements and tags them with this nonce.
            nonce?.let(html::cspNonceMetadata)
            html.moduleScript(externalUrl("$origin/@vite/client"), nonce = nonce)
            html.moduleScript(externalUrl("$origin/${entry.fileName}"), nonce = nonce)
            return
        }
        val stylesheet = applicationUrl("/$outputPath/${entry.outputName}.css")
        if (stylesheet in assets) html.stylesheet(assets.url(stylesheet), nonce = nonce)
        html.moduleScript(assets.url(applicationUrl("/$outputPath/${entry.outputName}.js")), nonce = nonce)
    }

    public companion object {
        /** Set by `wogeDev` for the application child; never set it in production. */
        public const val DEVELOPMENT_ORIGIN_VARIABLE: String = "WOGE_VITE_ORIGIN"
        public const val DEFAULT_OUTPUT_PATH: String = "vite"

        private val OUTPUT_PATH = Regex("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*")

        private fun requireLoopback(origin: String) {
            val uri = URI(origin)
            require(
                uri.scheme == "http" &&
                    uri.host == "127.0.0.1" &&
                    uri.port > 0 &&
                    uri.rawPath.isNullOrEmpty() &&
                    uri.rawUserInfo == null &&
                    uri.rawQuery == null,
            ) { "The Vite development origin must look like http://127.0.0.1:5173" }
        }
    }
}

/**
 * Writes the tags that load a Vite [entry]: in development the Vite client and the entry module from
 * the Vite dev server; in production the entry's hashed stylesheet (when it imports CSS) and script.
 * Use it in the document `head`. Pages never change when you add or remove Vite.
 */
public fun HtmlWriter.viteEntry(
    vite: ViteAssets,
    entry: ViteEntry,
    nonce: CspNonce? = null,
) {
    vite.write(this, entry, nonce)
}
