package dev.woge.development.gradle

import dev.woge.development.DevelopmentSourcePath
import dev.woge.development.ExperimentalWogeDevelopmentApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalWogeDevelopmentApi::class)
class KotlinDiagnosticsTest {
    @TempDir
    lateinit var project: Path

    @Test
    fun `kotlin errors become located diagnostics relative to the project`() {
        val file = project.resolve("src/main/kotlin/app/Home Page.kt")
        val uriPath = file.toUri().rawPath
        val diagnostics =
            KotlinDiagnostics.parse(
                listOf(
                    "> Task :compileKotlin FAILED",
                    "w: file://$uriPath:1:1 Unused variable",
                    "e: file://$uriPath:12:5 Unresolved reference 'tittle'.",
                    "e: file://$uriPath:12:5 Unresolved reference 'tittle'.",
                ),
                project,
            )

        assertEquals(1, diagnostics.size)
        val diagnostic = diagnostics.single()
        assertEquals(KotlinDiagnostics.kotlinError, diagnostic.code)
        assertEquals("Unresolved reference 'tittle'.", diagnostic.summary.value)
        assertEquals(DevelopmentSourcePath.of("src/main/kotlin/app/Home Page.kt"), diagnostic.location?.path)
        assertEquals(12, diagnostic.location?.line)
        assertEquals(5, diagnostic.location?.column)
    }

    @Test
    fun `woge processor errors keep their ID, location and repair hint`() {
        val file = project.resolve("src/main/kotlin/app/Regions.kt")
        val diagnostics =
            KotlinDiagnostics.parse(
                listOf(
                    "> Task :kspKotlin FAILED",
                    "e: [ksp] $file:6: WOGE-REF-002 A @WogeRegion function takes exactly one input parameter.",
                    "Received: suspend fun HtmlWriter.broken(input: String)",
                    "Valid: @WogeRegion fun HtmlWriter.summary(input: Summary) { ... }",
                    "",
                    "FAILURE: Build failed with an exception.",
                ),
                project,
            )

        val diagnostic = diagnostics.single()
        assertEquals("WOGE-REF-002", diagnostic.code.value)
        assertEquals(
            "WOGE-REF-002 A @WogeRegion function takes exactly one input parameter. " +
                "Received: suspend fun HtmlWriter.broken(input: String) " +
                "Valid: @WogeRegion fun HtmlWriter.summary(input: Summary) { ... }",
            diagnostic.summary.value,
        )
        assertEquals(DevelopmentSourcePath.of("src/main/kotlin/app/Regions.kt"), diagnostic.location?.path)
        assertEquals(6, diagnostic.location?.line)
    }

    @Test
    fun `other processor errors are located too`() {
        val file = project.resolve("src/main/kotlin/app/Other.kt")
        val diagnostic = KotlinDiagnostics.parse(listOf("e: [ksp] $file:3: Something else"), project).single()

        assertEquals(KotlinDiagnostics.processorError, diagnostic.code)
        assertEquals(3, diagnostic.location?.line)
    }

    @Test
    fun `errors in generated code are marked as generated`() {
        val file = project.resolve("build/generated/ksp/main/kotlin/app/SummaryRegion.kt")
        val diagnostic =
            KotlinDiagnostics
                .parse(
                    listOf("e: file://${file.toUri().rawPath}:4:9 Unresolved reference."),
                    project,
                ).single()

        assertEquals(KotlinDiagnostics.generatedCode, diagnostic.code)
        assertEquals("Generated code does not compile: Unresolved reference.", diagnostic.summary.value)
    }

    @Test
    fun `files outside the project keep the message but drop the location`() {
        val diagnostics = KotlinDiagnostics.parse(listOf("e: /elsewhere/Other.kt:3:1 Broken"), project)

        assertEquals("Broken", diagnostics.single().summary.value)
        assertNull(diagnostics.single().location)
    }

    @Test
    fun `unknown failures fall back to one generic diagnostic`() {
        val diagnostics =
            KotlinDiagnostics.parse(
                listOf("FAILURE: Build failed with an exception.", "\u001b[31m"),
                project,
            )

        assertEquals(KotlinDiagnostics.buildFailed, diagnostics.single().code)
    }
}
