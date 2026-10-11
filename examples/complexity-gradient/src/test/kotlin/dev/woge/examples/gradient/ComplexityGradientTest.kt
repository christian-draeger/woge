package dev.woge.examples.gradient

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
private val json =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

class ComplexityGradientTest {
    private val root = File(System.getProperty("woge.gradient.root"))
    private val baseline = File(root, "gradient-baseline.json")

    private fun computed() = Gradient(SCHEMA_VERSION, SUITE_VERSION, ladder.map { measure(root, it) })

    @Test
    fun `the ladder has nine existing tasks`() {
        assertEquals(9, ladder.size)
        for (task in ladder) {
            assertTrue(
                File(root, "src/main/kotlin/dev/woge/examples/gradient/${task.packageName}").isDirectory,
                task.id,
            )
            assertTrue(File(root, "src/main/resources/gradient/${task.id}").isDirectory, task.id)
        }
    }

    @Test
    fun `static page, component, form and validation need no protocol or host internals`() {
        val simple = computed().tasks.take(4)
        assertEquals(listOf("static-page", "component", "form", "validation"), simple.map { it.id })
        for (task in simple) {
            assertEquals(emptyList<String>(), task.forbiddenConcepts, task.id)
            assertEquals(emptyList<String>(), task.knownGaps, task.id)
        }
    }

    @Test
    fun `metrics match the committed baseline`() {
        val actual = json.encodeToString(Gradient.serializer(), computed()) + "\n"
        if (System.getProperty("woge.gradient.update") == "true") {
            baseline.writeText(actual)
            return
        }
        assertEquals(
            baseline.readText(),
            actual,
            "Gradient metrics changed. New metrics:\n$actual\n" +
                "If the change is intended, run ./gradlew :woge-complexity-gradient:test -Pwoge.gradient.update=true",
        )
    }
}
