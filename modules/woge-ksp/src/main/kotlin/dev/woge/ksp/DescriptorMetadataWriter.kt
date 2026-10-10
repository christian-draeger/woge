package dev.woge.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile

internal data class DescriptorEntry(
    val kind: String,
    val id: String,
    val declaration: String,
    val inputType: String? = null,
    val path: String? = null,
    val component: String? = null,
    val keyType: String? = null,
) {
    fun fields(): Map<String, String> =
        sortedMapOf<String, String>().apply {
            put("kind", kind)
            put("id", id)
            put("declaration", declaration)
            inputType?.let { put("inputType", it) }
            path?.let { put("path", it) }
            component?.let { put("component", it) }
            keyType?.let { put("keyType", it) }
        }
}

/** Aggregating structural catalogues are invalidated by KSP when their contributing symbols change. */
internal class DescriptorMetadataWriter(
    private val generator: CodeGenerator,
    private val logger: KSPLogger,
) {
    private val packages = sortedMapOf<String, MutableMap<String, DescriptorEntry>>()
    private val sources = mutableMapOf<String, MutableSet<KSFile>>()
    private val identities = mutableMapOf<Pair<String, String>, String>()

    fun add(
        packageName: String,
        files: List<KSFile>,
        entry: DescriptorEntry,
    ) {
        val previous = identities.putIfAbsent(entry.kind to entry.id, entry.declaration)
        if (previous != null && previous != entry.declaration) {
            logger.error(
                "WOGE-MANIFEST-001 duplicate ${entry.kind} identity '${entry.id}': $previous and ${entry.declaration}",
            )
            return
        }
        packages.getOrPut(packageName) { sortedMapOf() }["${entry.kind}:${entry.id}"] = entry
        sources.getOrPut(packageName) { linkedSetOf() }.addAll(files)
    }

    @Suppress("SpreadOperator")
    fun write() {
        packages.forEach { (packageName, entries) ->
            val dependencies = Dependencies(true, *sources.getValue(packageName).toTypedArray())
            val name = if (packageName.isEmpty()) "root" else "package.$packageName"
            generator
                .createNewFile(
                    dependencies,
                    "",
                    "META-INF/woge/descriptors/$name",
                    "properties",
                ).bufferedWriter()
                .use { it.write(catalogueSource(entries.values)) }
            generator
                .createNewFile(dependencies, packageName, "WogeDescriptors")
                .bufferedWriter()
                .use { it.write(descriptorsSource(packageName, entries.values)) }
        }
    }
}

private fun catalogueSource(entries: Collection<DescriptorEntry>): String =
    buildString {
        appendLine("schemaVersion=1")
        entries.forEachIndexed { index, entry ->
            entry.fields().forEach { (field, value) -> appendLine("$index.$field=${propertyValue(value)}") }
        }
    }

private fun descriptorsSource(
    packageName: String,
    entries: Collection<DescriptorEntry>,
): String =
    buildString {
        if (packageName.isNotEmpty()) appendLine("package $packageName")
        appendLine()
        appendLine("public val wogeDescriptors: List<dev.woge.host.DescriptorMetadata> =")
        appendLine("    java.util.Collections.unmodifiableList(listOf(")
        entries.forEach { entry ->
            appendLine("    dev.woge.host.DescriptorMetadata(")
            appendLine("        kind = dev.woge.host.DescriptorKind.${entry.kind},")
            entry.fields().filterKeys { it != "kind" }.forEach { (field, value) ->
                appendLine("        $field = ${kotlinString(value)},")
            }
            appendLine("    ),")
        }
        appendLine("))")
    }

private fun propertyValue(value: String): String = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")

private fun kotlinString(value: String): String =
    "\"" +
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") +
        "\""
