package dev.woge.development.browser

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile

class ProductionIsolationTest {
    @Test
    fun `every production artifact excludes development endpoint client overlay and Spring listener`() {
        val artifacts = System.getProperty("woge.production.artifacts").split(File.pathSeparator)
        assertTrue(artifacts.isNotEmpty())
        artifacts.forEach(::verifyArtifact)
    }

    private fun verifyArtifact(path: String) {
        JarFile(path).use { jar ->
            jar.entries().asSequence().forEach { entry ->
                assertFalse(
                    entry.name.startsWith("dev/woge/development/"),
                    "$path contains development code or resources: ${entry.name}",
                )
                verifyFactories(jar, entry, path)
            }
        }
    }

    private fun verifyFactories(
        jar: JarFile,
        entry: JarEntry,
        path: String,
    ) {
        if (entry.name != "META-INF/spring.factories") return
        val factories = jar.getInputStream(entry).bufferedReader().use { it.readText() }
        assertFalse(factories.contains("SpringDevelopmentReadyListener"), path)
    }
}
