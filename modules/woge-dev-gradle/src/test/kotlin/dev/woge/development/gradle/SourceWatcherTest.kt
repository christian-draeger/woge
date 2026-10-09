package dev.woge.development.gradle

import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.DevelopmentSourcePath
import dev.woge.development.ExperimentalWogeDevelopmentApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText

@OptIn(ExperimentalWogeDevelopmentApi::class)
class SourceWatcherTest {
    @TempDir
    lateinit var project: Path

    @Test
    fun `reports edited, added and deleted files with their kind`() {
        val kotlin = project.resolve("src/main/kotlin/App.kt").write("fun main() {}")
        val css = project.resolve("src/main/resources/static/app.css").write("body {}")
        val buildFile = project.resolve("build.gradle.kts").write("plugins {}")
        val watcher =
            SourceWatcher(
                project,
                listOf(project.resolve("src/main/kotlin"), project.resolve("src/main/resources")),
                listOf(buildFile),
            )
        assertTrue(watcher.poll().isEmpty())

        kotlin.write("fun main() { println() }", later = true)
        css.deleteExisting()
        project.resolve("src/main/resources/templates/info.txt").write("hi")
        buildFile.write("plugins { java }", later = true)

        assertEquals(
            setOf(
                DevelopmentChange(
                    DevelopmentChangeKind.KOTLIN_SOURCE,
                    DevelopmentSourcePath.of("src/main/kotlin/App.kt"),
                ),
                DevelopmentChange(
                    DevelopmentChangeKind.CSS,
                    DevelopmentSourcePath.of("src/main/resources/static/app.css"),
                ),
                DevelopmentChange(
                    DevelopmentChangeKind.UNKNOWN,
                    DevelopmentSourcePath.of("src/main/resources/templates/info.txt"),
                ),
                DevelopmentChange(
                    DevelopmentChangeKind.BUILD_CONFIGURATION,
                    DevelopmentSourcePath.of("build.gradle.kts"),
                ),
            ),
            watcher.poll(),
        )
        assertTrue(watcher.poll().isEmpty())
    }

    @Test
    fun `ignores build output, node_modules and hidden folders inside roots`() {
        val root = project.resolve("src")
        val watcher = SourceWatcher(project, listOf(root))
        root.resolve("build/Generated.kt").write("x")
        root.resolve("node_modules/pkg/index.css").write("x")
        root.resolve(".cache/file.kt").write("x")

        assertTrue(watcher.poll().isEmpty())
    }

    private fun Path.write(
        text: String,
        later: Boolean = false,
    ): Path {
        parent.createDirectories()
        writeText(text)
        if (later) Files.setLastModifiedTime(this, FileTime.fromMillis(System.currentTimeMillis() + 10_000))
        return this
    }
}
