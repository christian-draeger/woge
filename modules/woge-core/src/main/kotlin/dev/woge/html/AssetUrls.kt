package dev.woge.html

import java.io.InputStream
import java.util.Collections
import java.util.Properties

/** Resolves ordinary asset paths to one reproducible content-addressed resource tree. */
public class AssetUrls private constructor(
    public val bundleHash: String,
    paths: Map<String, ApplicationUrl>,
    private val development: Boolean,
) {
    private val paths = Collections.unmodifiableMap(paths.toMap())

    /** Whether the manifest lists [logical], for assets that only some builds produce. */
    public operator fun contains(logical: ApplicationUrl): Boolean = logical.value in paths

    /** The logical URL must be registered; missing production assets never silently become plain URLs. */
    public fun url(logical: ApplicationUrl): ApplicationUrl {
        val hashed = requireNotNull(paths[logical.value]) { "Asset URL is absent from the Woge asset manifest" }
        return if (development) logical else hashed
    }

    public companion object {
        /** Loads the application's manifest. More than one manifest is an explicit configuration error. */
        public fun load(
            classLoader: ClassLoader = AssetUrls::class.java.classLoader,
            development: Boolean = System.getProperty("woge.development") == "true",
        ): AssetUrls {
            val resources = classLoader.getResources(ASSET_MANIFEST).toList()
            require(resources.size == 1) {
                "Expected one Woge asset manifest; apply dev.woge.application or dev.woge.spring-boot"
            }
            return resources.single().openStream().use { read(it, development) }
        }

        /** Reads trusted build metadata. The caller owns and closes [input]. */
        public fun read(
            input: InputStream,
            development: Boolean = false,
        ): AssetUrls {
            val properties = Properties().apply { load(input) }
            require(properties.remove("schemaVersion") == "1") { "Unsupported Woge asset manifest schema" }
            val hash = properties.remove("bundleHash") as? String
            require(hash != null && ASSET_HASH.matches(hash)) { "Invalid Woge asset bundle hash" }
            val paths =
                properties.stringPropertyNames().associate { key ->
                    require(key.startsWith("asset.")) { "Unknown Woge asset manifest field" }
                    val logical = applicationUrl(key.removePrefix("asset."))
                    requireAssetPath(logical)
                    val hashed = applicationUrl(properties.getProperty(key))
                    require(hashed.value == "/_woge/assets/$hash${logical.value}") { "Invalid hashed asset URL" }
                    logical.value to hashed
                }
            return AssetUrls(hash, paths, development)
        }
    }
}

private fun requireAssetPath(url: ApplicationUrl) {
    val path = java.net.URI(url.value).path
    require(
        url.value.startsWith("/") &&
            '?' !in url.value &&
            '#' !in url.value &&
            path.split('/').drop(1).all { it.isNotEmpty() && it != "." && it != ".." && '\\' !in it },
    ) { "Asset URLs must be absolute application paths without traversal, query or fragment" }
}

private const val ASSET_MANIFEST = "META-INF/woge/assets.properties"
private val ASSET_HASH = Regex("[0-9a-f]{64}")
