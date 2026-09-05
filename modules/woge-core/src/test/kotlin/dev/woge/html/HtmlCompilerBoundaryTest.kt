package dev.woge.html

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class HtmlCompilerBoundaryTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `WOGE-XSS-001 ordinary strings cannot cross the raw HTML boundary`() {
        assertFixtureFails("raw-string", "UnsafeHtml")
    }

    @Test
    fun `WOGE-XSS-002 ordinary strings cannot cross the validated URL boundary`() {
        assertFixtureFails("url-string", "HtmlUrl")
    }

    @Test
    fun `a complete stylesheet cannot cross the declaration-list boundary`() {
        assertFixtureFails("stylesheet-in-attribute", "CssDeclarations", "CssStylesheet")
    }

    @Test
    fun `a declaration list cannot cross the stylesheet boundary`() {
        assertFixtureFails("declarations-in-style-block", "CssStylesheet", "CssDeclarations")
    }

    private fun assertFixtureFails(
        name: String,
        expectedType: String,
        actualType: String = "String",
    ) {
        val fixtureDirectory = projectDirectory.resolve(name).createDirectories()
        val repositoryRoot = Path.of(requireNotNull(System.getProperty("woge.repository.root")))
        val coreJar = Path.of(requireNotNull(System.getProperty("woge.core.jar")))

        fixtureDirectory.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                includeBuild("${repositoryRoot.resolve("build-logic").asKotlinString()}")
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            rootProject.name = "html-$name-negative-fixture"
            """.trimIndent(),
        )
        fixtureDirectory.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.woge.kotlin-jvm-library")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation(files("${coreJar.asKotlinString()}"))
                compileOnly("org.jetbrains:annotations:26.1.0")
            }
            """.trimIndent(),
        )

        val sourceDirectory = fixtureDirectory.resolve("src/main/kotlin/fixture").createDirectories()
        val fixtureSource =
            requireNotNull(javaClass.getResource("/compiler-fixtures/$name.kt"))
                .toURI()
                .let(Path::of)
                .readText()
        sourceDirectory.resolve("Fixture.kt").writeText(fixtureSource)

        val result =
            GradleRunner
                .create()
                .withProjectDir(fixtureDirectory.toFile())
                .withArguments("compileKotlin", "--stacktrace", "--no-configuration-cache")
                .buildAndFail()

        assertTrue(result.output.contains("Argument type mismatch"), result.output)
        assertTrue(result.output.contains(actualType), result.output)
        assertTrue(result.output.contains(expectedType), result.output)
    }
}

private fun Path.asKotlinString(): String = toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\"")
