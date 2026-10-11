package dev.woge.development.gradle

import dev.woge.development.BuildId
import dev.woge.development.DevelopmentApplicationManifest
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.mcp.DevelopmentMcpServer
import dev.woge.development.mcp.JsonDevelopmentManifest
import dev.woge.development.orchestrator.DevelopmentManifestSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Reads `.woge/manifest.json`. Compile tasks delete the file before they run and `wogeManifest`
 * writes it afterwards, so a present file always describes the latest compiled build.
 */
@ExperimentalWogeDevelopmentApi
internal class FileManifestSource(
    private val file: Path,
) : DevelopmentManifestSource {
    override suspend fun manifest(buildId: BuildId): DevelopmentApplicationManifest? =
        withContext(Dispatchers.IO) {
            val document =
                try {
                    Json.parseToJsonElement(file.readText()) as? JsonObject
                } catch (_: IOException) {
                    null
                } catch (_: SerializationException) {
                    null
                }
            val version = document?.get("schemaVersion")?.jsonPrimitive?.intOrNull
            if (document == null || version == null) null else JsonDevelopmentManifest(version, buildId, document)
        }
}

/**
 * Writes the MCP connection as an owner-only JSON file in the same shape most coding agents use for
 * an HTTP MCP server entry: `{"type": "http", "url": ..., "headers": {"Authorization": ...}}`.
 */
@ExperimentalWogeDevelopmentApi
internal fun writeMcpConnection(
    file: Path,
    server: DevelopmentMcpServer,
) {
    val connection =
        buildJsonObject {
            put("type", "http")
            put("url", server.url)
            putJsonObject("headers") { put("Authorization", "Bearer ${server.token}") }
        }
    Files.createDirectories(file.parent)
    val temporary = file.resolveSibling("${file.fileName}.tmp")
    Files.deleteIfExists(temporary)
    if ("posix" in FileSystems.getDefault().supportedFileAttributeViews()) {
        Files.createFile(temporary, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
    }
    temporary.writeText(connection.toString())
    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
