package dev.woge.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KotlinJvmLibraryConventionTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `convention provides compiler quality and test report gates`() {
        writeProject()

        val result = runner("check").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":detekt")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":ktlintMainSourceSetCheck")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertTrue(projectDirectory.resolve("build/test-results/test/TEST-sample.GreetingTest.xml").toFile().isFile)
        assertTrue(projectDirectory.resolve("build/reports/tests/test/index.html").toFile().isFile)

        projectDirectory.resolve("src/main/kotlin/sample/Greeting.kt").writeText(
            """
            package sample

            fun missingExplicitApi() = "invalid"
            """.trimIndent(),
        )
        val failure = runner("compileKotlin", "--rerun-tasks").buildAndFail()
        assertTrue(failure.output.contains("Visibility must be specified in explicit API mode"))
    }

    private fun writeProject() {
        write("settings.gradle.kts", "rootProject.name = \"convention-fixture\"")
        write(
            "build.gradle.kts",
            """
            plugins {
                id("dev.woge.kotlin-jvm-library")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
            }
            """.trimIndent(),
        )
        write("config/detekt/detekt.yml", "build:\n  maxIssues: 0\n")
        write(
            "src/main/kotlin/sample/Greeting.kt",
            """
            package sample

            public object Greeting {
                public fun text(name: String): String = "Hello, ${'$'}name"
            }
            """.trimIndent(),
        )
        write(
            "src/test/kotlin/sample/GreetingTest.kt",
            """
            package sample

            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Test

            class GreetingTest {
                @Test
                fun `returns greeting`() {
                    assertEquals("Hello, Woge", Greeting.text("Woge"))
                }
            }
            """.trimIndent(),
        )
    }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments(*arguments, "--stacktrace")
            .withPluginClasspath()

    private fun write(relativePath: String, content: String) {
        val target = projectDirectory.resolve(relativePath)
        target.parent?.createDirectories()
        target.writeText("$content\n")
    }
}
