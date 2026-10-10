package dev.woge.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DescriptorMetadataTest {
    @Test
    fun `catalogue includes declarations introduced in later compiler rounds`() {
        val result = runKsp(mapOf("Inputs.kt" to INPUTS), listOf(generatedRoute("/later")))
        assertEquals(emptyList<String>(), result.errors)
        val catalogue = result.resources.getValue("META-INF/woge/descriptors/package.shop.properties")
        assertTrue(catalogue.contains("declaration=shop.HomeRoute"))
        assertTrue(catalogue.contains("declaration=shop.LaterRoute"))
        assertTrue(result.generated.getValue("WogeDescriptors.kt").contains("shop.LaterRoute"))
    }

    @Test
    fun `identity introduced in a later round cannot overwrite an existing descriptor`() {
        val result = runKsp(mapOf("Inputs.kt" to INPUTS), listOf(generatedRoute("/")))
        assertTrue(result.errors.any { it.contains("WOGE-MANIFEST-001") }, result.errors.toString())
        assertTrue(result.resources.isEmpty())
    }

    @Test
    fun `all four descriptor kinds emit stable structural catalogues with aggregate dependencies`() {
        val sources = mapOf("Inputs.kt" to INPUTS, "Markup.kt" to MARKUP)
        val result = runKsp(sources)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(result.resources, runKsp(sources.entries.reversed().associate { it.toPair() }).resources)
        val catalogue = result.resources.getValue("META-INF/woge/descriptors/package.shop.properties")
        assertTrue(catalogue.startsWith("schemaVersion=1\n"))
        listOf("ACTION", "COMPONENT", "PAGE", "REGION").forEach { assertTrue(catalogue.contains("kind=$it\n")) }
        assertFalse(catalogue.contains("private body marker"))
        assertTrue(catalogue.contains("component=shop.Row"))
        assertTrue(catalogue.contains("inputType=shop.Command"))
        assertTrue(catalogue.contains("keyType=kotlin.String"))
        assertEquals(
            RecordedDependencies(true, listOf("Inputs.kt", "Markup.kt")),
            result.dependencies.getValue("META-INF/woge/descriptors/package.shop.properties"),
        )
        val model = result.generated.getValue("WogeDescriptors.kt")
        assertTrue(model.contains("List<dev.woge.host.DescriptorMetadata>"))
        assertFalse(model.contains("private body marker"))
    }

    @Test
    fun `duplicate or unsupported descriptors cannot enter the catalogue`() {
        val invalid =
            runKsp(
                mapOf(
                    "Inputs.kt" to INPUTS,
                    "Markup.kt" to MARKUP.replace("suspend fun save", "fun save"),
                ),
            )
        assertTrue(invalid.errors.isNotEmpty())
        assertFalse(invalid.resources.values.any { it.contains("kind=ACTION") })
        val duplicate =
            runKsp(
                mapOf("Inputs.kt" to INPUTS, "Markup.kt" to MARKUP + "\n" + ACTION.replace("fun save", "fun other")),
            )
        assertTrue(duplicate.errors.any { it.contains("WOGE-ACTION-007") })
        assertFalse(duplicate.resources.values.any { it.contains("kind=ACTION") })
    }

    @Test
    fun `root package and named default package keep distinct catalogues and qualified declarations`() {
        val result =
            runKsp(
                mapOf(
                    "Root.kt" to "@dev.woge.host.WogeRoute(\"/root\") data object RootInput",
                    "Named.kt" to "package default\n@dev.woge.host.WogeRoute(\"/named\") data object NamedInput",
                ),
            )
        assertEquals(emptyList<String>(), result.errors)
        assertTrue(
            result.resources.getValue("META-INF/woge/descriptors/root.properties").contains("declaration=RootRoute\n"),
        )
        assertTrue(
            result.resources
                .getValue("META-INF/woge/descriptors/package.default.properties")
                .contains("declaration=default.NamedRoute\n"),
        )
    }

    private fun generatedRoute(path: String): SymbolProcessorProvider =
        object : SymbolProcessorProvider {
            override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
                object : SymbolProcessor {
                    private var written = false

                    override fun process(resolver: Resolver): List<KSAnnotated> {
                        if (!written) {
                            environment.codeGenerator
                                .createNewFile(Dependencies.ALL_FILES, "shop", "Later")
                                .bufferedWriter()
                                .use {
                                    it.write("package shop\n@dev.woge.host.WogeRoute(\"$path\") data object LaterInput")
                                }
                            written = true
                        }
                        return emptyList()
                    }
                }
        }

    private companion object {
        val INPUTS =
            """
            package shop
            import dev.woge.host.*
            @WogeRoute("/") data object HomeInput
            @WogeComponent class Row(@WogeKey val id: String)
            data class Command(val title: String)
            """.trimIndent()
        val ACTION =
            """
            @WogeAction("save")
            suspend fun save(command: Command, context: RequestContext): PageResult =
                PageResult.Redirect(applicationUrl("/"))
            """.trimIndent()
        val MARKUP =
            """
            package shop
            import dev.woge.host.*
            import dev.woge.html.HtmlWriter
            import dev.woge.html.applicationUrl
            @WogeRegion(component = Row::class)
            fun HtmlWriter.status(command: Command) { text("private body marker") }
            $ACTION
            """.trimIndent()
    }
}
