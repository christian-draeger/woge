package dev.woge.examples.m1

import dev.woge.protocol.PatchStreamV1
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText

class FrameworkIndexTest {
    private val repositoryRoot = Path.of(requireNotNull(System.getProperty("woge.repository.root")))
    private val index =
        Json.parseToJsonElement(repositoryRoot.resolve("docs/ai-dx/woge-framework-index.json").readText()).jsonObject

    @Test
    fun `framework index matches canonical versions and module manifest`() {
        assertEquals(1, index.requiredInt("schemaVersion"))
        assertEquals(gradleProperty("wogeVersion"), index.requiredString("frameworkVersion"))
        assertEquals(catalogVersion("kotlin"), index.requiredObject("language").requiredString("kotlinVersion"))
        assertEquals(PatchStreamV1.VERSION, index.requiredObject("protocol").requiredInt("patchStreamVersion"))
        assertEquals(catalogVersion("springBoot"), index.requiredObject("hosts").requiredString("springBootVersion"))
        assertEquals(catalogVersion("ktor"), index.requiredObject("hosts").requiredString("ktorVersion"))

        val packageMetadata =
            Json
                .parseToJsonElement(repositoryRoot.resolve("client/woge-fallback-client/package.json").readText())
                .jsonObject
        val browser = index.requiredObject("browserClient")
        assertEquals(packageMetadata.requiredString("name"), browser.requiredString("npmPackage"))
        assertEquals(packageMetadata.requiredString("version"), browser.requiredString("packageVersion"))
        assertEquals(
            packageMetadata.requiredObject("woge").requiredInt("patchProtocolVersion"),
            browser.requiredInt("patchProtocolVersion"),
        )

        val manifestModules = readModuleManifest()
        val indexedModules =
            index.requiredArray("modules").associate { module ->
                module.jsonObject.requiredString("name") to module.jsonObject
            }
        assertEquals(manifestModules.keys, indexedModules.keys)
        manifestModules.forEach { (name, manifest) ->
            val indexed = requireNotNull(indexedModules[name])
            assertEquals(manifest.role, indexed.requiredString("role"), name)
            assertEquals(manifest.exposure, indexed.requiredString("exposure"), name)
            assertEquals(manifest.path, indexed.requiredString("sourcePath"), name)
            assertTrue(
                indexed.requiredArray("concepts").all { concept ->
                    concept is JsonPrimitive && concept.isString
                },
                name,
            )
            if (manifest.exposure == "public") {
                assertEquals("dev.woge:$name", indexed.requiredString("artifact"), name)
            }
            indexed.optionalString("guide")?.let(::assertRepositoryPath)
            indexed.optionalString("example")?.let(::assertRepositoryPath)
        }
    }

    @Test
    fun `framework index points to unique compile-verified examples and explicit future owners`() {
        val examples = index.requiredArray("examples").map(JsonElement::jsonObject)
        assertEquals(examples.size, examples.map { it.requiredString("id") }.distinct().size)
        examples.forEach { example ->
            assertRepositoryPath(example.requiredString("source"))
            assertTrue(example.requiredArray("concepts").isNotEmpty(), example.requiredString("id"))
        }
        assertEquals(
            listOf(26, 27, 28),
            index.requiredArray("plannedKspDiagnosticIssues").map { it.jsonPrimitive.int },
        )
        assertRepositoryPath(index.requiredString("browserSupportPolicy"))
    }

    private fun readModuleManifest(): LinkedHashMap<String, ModuleManifestEntry> =
        linkedMapOf<String, ModuleManifestEntry>().apply {
            repositoryRoot
                .resolve("config/architecture/module-boundaries.tsv")
                .readLines()
                .filterNot { it.isBlank() || it.startsWith("#") }
                .forEach { row ->
                    val columns = row.split('\t')
                    put(columns[0], ModuleManifestEntry(columns[1], columns[2], columns[3]))
                }
        }

    private fun gradleProperty(name: String): String =
        repositoryRoot
            .resolve("gradle.properties")
            .readLines()
            .first { it.startsWith("$name=") }
            .substringAfter('=')

    private fun catalogVersion(name: String): String =
        requireNotNull(
            Regex("(?m)^${Regex.escape(name)} = \\\"([^\\\"]+)\\\"")
                .find(repositoryRoot.resolve("gradle/libs.versions.toml").readText())
                ?.groupValues
                ?.get(1),
        )

    private fun assertRepositoryPath(value: String) {
        assertTrue(Files.exists(repositoryRoot.resolve(value)), "Framework index path does not exist: $value")
    }

    private fun JsonObject.requiredObject(name: String): JsonObject = requireNotNull(this[name]).jsonObject

    private fun JsonObject.requiredArray(name: String): JsonArray = requireNotNull(this[name]).jsonArray

    private fun JsonObject.requiredString(name: String): String = requireNotNull(this[name]).jsonPrimitive.content

    private fun JsonObject.requiredInt(name: String): Int = requireNotNull(this[name]).jsonPrimitive.int

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    private data class ModuleManifestEntry(
        val role: String,
        val exposure: String,
        val path: String,
    )
}
