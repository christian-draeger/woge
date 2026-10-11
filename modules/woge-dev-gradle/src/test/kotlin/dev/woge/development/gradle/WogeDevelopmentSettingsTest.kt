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
                host = WogeDevelopmentHost.KTOR,
                fastRestart = false,
                pollIntervalMillis = 100,
                vite =
                    ViteDevServerSettings(
                        command = listOf("node", "--eval", "import('vite')"),
                        directory = directory.resolve("my app"),
                        port = 5173,
                        environment = mapOf("WOGE_VITE_ROOT" to "/x/my app/src/main/frontend", "A" to "b=c"),
                    ),
                mcp = true,
                mcpPort = 7311,
            )
        val file = directory.resolve("settings.properties")

        settings.writeTo(file)

        assertEquals(settings, WogeDevelopmentSettings.readFrom(file))
    }

    @Test
    fun `only Spring Boot children get the trigger directory on their classpath`() {
        val spring =
            WogeDevelopmentSettings(
                projectDirectory = directory,
                stateDirectory = directory.resolve("state"),
                buildCommand = listOf("gradlew", ":classes"),
                childJava = "java",
                childClasspath = listOf(directory.resolve("classes")),
                mainClassFile = directory.resolve("main-class.txt"),
                watchRoots = emptyList(),
                buildFiles = emptyList(),
                port = 8080,
            )
        val ktor = spring.copy(host = WogeDevelopmentHost.KTOR)

        assertEquals(
            listOf(directory.resolve("classes"), spring.triggerFile.parent).joinToString(java.io.File.pathSeparator),
            spring.childCommand("App")[3],
        )
        assertEquals(directory.resolve("classes").toString(), ktor.childCommand("App")[3])
    }
}
