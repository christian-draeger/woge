package dev.woge.development.gradle

import dev.woge.development.ExperimentalWogeDevelopmentApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalWogeDevelopmentApi::class)
class WogeDevelopmentSettingsTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `settings survive a round trip including paths with spaces`() {
        val settings =
            WogeDevelopmentSettings(
                projectDirectory = directory.resolve("my app"),
                stateDirectory = directory.resolve("my app/build/woge-dev"),
                buildCommand = listOf("/x/my app/gradlew", ":classes", "-PwogeSpringAdapter=mvc"),
                childJava = "/jdk/bin/java",
                childClasspath = listOf(directory.resolve("a.jar"), directory.resolve("b dir")),
                mainClassFile = directory.resolve("my app/build/resolvedMainClassName"),
                watchRoots = listOf(directory.resolve("my app/src/main")),
                buildFiles = listOf(directory.resolve("my app/build.gradle.kts")),
                port = 8080,
                fastRestart = false,
                pollIntervalMillis = 100,
            )
        val file = directory.resolve("settings.properties")

        settings.writeTo(file)

        assertEquals(settings, WogeDevelopmentSettings.readFrom(file))
    }
}
