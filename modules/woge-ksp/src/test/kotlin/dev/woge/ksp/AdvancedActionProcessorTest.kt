package dev.woge.ksp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdvancedActionProcessorTest {
    @Test
    fun `nested row declarations are typed and their source dependencies tracked`() {
        val action =
            """
            package shop
            import dev.woge.host.*
            import dev.woge.html.applicationUrl
            @WogeAction("save")
            suspend fun save(command: MultipartSubmission<Command>): PageResult =
                redirect(applicationUrl("/done"))
            """.trimIndent()
        val result =
            runKsp(
                mapOf(
                    "Action.kt" to action,
                    "Command.kt" to
                        "package shop\ndata class Command(val rows: List<Row>, val matrix: List<List<Int>>)",
                    "Row.kt" to "package shop\ninternal data class Row(val key: Int, val title: String)",
                ),
            )
        assertEquals(emptyList<String>(), result.errors)
        assertTrue(result.generated.getValue("SaveAction.kt").contains("internal object SaveAction"))
        assertEquals(
            RecordedDependencies(true, listOf("Action.kt", "Command.kt", "Row.kt")),
            result.dependencies.getValue("SaveAction.kt"),
        )
    }

    @Test
    fun `recursive and mutable nested rows are rejected before code generation`() {
        for (row in listOf("data class Row(val child: Row?)", "data class Row(var title: String)")) {
            val source =
                """
                package shop
                import dev.woge.host.*
                import dev.woge.html.applicationUrl
                data class Command(val rows: List<Row>)
                $row
                @WogeAction("save")
                suspend fun save(command: Command): PageResult = redirect(applicationUrl("/done"))
                """.trimIndent()
            val result = runKsp(mapOf("Action.kt" to source))
            assertTrue(result.errors.any { it.contains("WOGE-ACTION-003") }, result.errors.toString())
        }
    }
}
