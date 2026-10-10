package dev.woge.host

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
public enum class ManifestHostAdapter {
    @SerialName("spring-mvc")
    SPRING_MVC,

    @SerialName("spring-webflux")
    SPRING_WEBFLUX,

    @SerialName("ktor")
    KTOR,
}

/** Public, non-secret build metadata. This is not a registry of running endpoints or a security policy. */
@Serializable
public data class ApplicationManifest(
    public val schemaVersion: Int,
    public val wogeVersion: String,
    public val kotlinVersion: String,
    public val hostAdapter: ManifestHostAdapter,
    public val capabilities: List<String>,
    public val frontendMode: String,
    public val documentation: Map<String, String>,
    public val descriptors: List<DescriptorMetadata>,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "Unsupported Woge application manifest schema: $schemaVersion" }
    }

    public companion object {
        public const val SCHEMA_VERSION: Int = 1

        private val json = Json { ignoreUnknownKeys = true }

        /** Read the manifest file explicitly; no classpath scanning or terminal-output parsing. */
        public fun decode(source: String): ApplicationManifest = json.decodeFromString(source)
    }
}
