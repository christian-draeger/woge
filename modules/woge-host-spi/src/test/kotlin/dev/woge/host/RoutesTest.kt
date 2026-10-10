package dev.woge.host

import dev.woge.html.ApplicationUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class RoutesTest {
    private enum class View { SHELL, IN_PROGRESS }

    private data class TaskInput(
        val project: String,
        val task: Long,
        val view: View?,
    )

    /** Written like the generated code, so the base class is tested through its real use. */
    private object TaskRoute : PageRoute<TaskInput>("/projects/{project}/tasks/{task}") {
        override fun url(input: TaskInput): ApplicationUrl =
            buildUrl(
                path = mapOf("project" to input.project, "task" to input.task.toString()),
                query = listOf("view" to input.view?.let { RouteValues.format(it) }),
            )

        override fun decode(parameters: RouteParameters): TaskInput =
            TaskInput(
                project = pathValue(parameters, "project") { it },
                task = pathValue(parameters, "task") { it.toLongOrNull() },
                view = queryValue(parameters, "view") { RouteValues.enum<View>(it) },
            )
    }

    private fun parameters(
        path: Map<String, String>,
        query: Map<String, String> = emptyMap(),
    ) = object : RouteParameters {
        override fun path(name: String) = path[name]

        override fun query(name: String) = query[name]
    }

    @Test
    fun `links are ordinary encoded URLs`() {
        assertEquals("/projects/apollo/tasks/7", TaskRoute.url(TaskInput("apollo", 7, null)).value)
        assertEquals(
            "/projects/a%20b%2Fc%C3%A4/tasks/7?view=in-progress",
            TaskRoute.url(TaskInput("a b/cä", 7, View.IN_PROGRESS)).value,
        )
    }

    @Test
    fun `decoding reverses the link`() {
        val input = TaskInput("a b/cä", 7, View.IN_PROGRESS)
        val decoded =
            TaskRoute.decode(parameters(mapOf("project" to "a b/cä", "task" to "7"), mapOf("view" to "in-progress")))

        assertEquals(input, decoded)
    }

    @Test
    fun `an absent or empty query value is null`() {
        val path = mapOf("project" to "apollo", "task" to "7")

        assertNull(TaskRoute.decode(parameters(path)).view)
        assertNull(TaskRoute.decode(parameters(path, mapOf("view" to ""))).view)
    }

    @Test
    fun `an invalid path value means not found and an invalid query value a bad request`() {
        val badPath =
            assertThrows<RouteValueException> {
                TaskRoute.decode(
                    parameters(
                        mapOf(
                            "project" to "a",
                            "task" to "x",
                        ),
                    ),
                )
            }
        val badQuery =
            assertThrows<RouteValueException> {
                TaskRoute.decode(parameters(mapOf("project" to "a", "task" to "1"), mapOf("view" to "SHELL")))
            }

        assertEquals(FailureCategory.NOT_FOUND, badPath.category)
        assertEquals(FailureCategory.BAD_REQUEST, badQuery.category)
        assertEquals("Route parameter 'view' has an invalid value", badQuery.message)
    }

    @Test
    fun `route paths are checked`() {
        listOf("/", "/projects", "/projects/{project}/tasks/{task}", "/a.b/c-d/e_f~").forEach {
            assertEquals(true, RoutePath.isValid(it), it)
        }
        listOf("", "projects", "/projects/", "//", "/a/{b}/{b}", "/a/{b}c", "/a?b", "/{1a}", "/a b").forEach {
            assertEquals(false, RoutePath.isValid(it), it)
        }
    }

    @Test
    fun `uuids must use the standard form`() {
        val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        assertEquals(id, RouteValues.uuid(id.toString()))
        assertNull(RouteValues.uuid("1-2-3-4-5"))
    }
}
