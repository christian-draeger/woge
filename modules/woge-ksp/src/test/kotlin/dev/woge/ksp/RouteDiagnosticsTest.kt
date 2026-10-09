package dev.woge.ksp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** Locks every route diagnostic: stable ID, source location, rule and received shape. */
class RouteDiagnosticsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    internal fun `invalid routes are rejected with a located diagnostic`(
        rule: Rule,
        source: String,
        expected: List<String>,
    ) {
        val result = runKsp(mapOf("Fixture.kt" to PRELUDE + source.trimIndent()))

        assertEquals(expected, result.errors)
        assertTrue(expected.all { it.contains(rule.id) })
        assertEquals(emptyMap<String, String>(), result.generated.filterKeys { it.endsWith("Route.kt") })
    }

    private companion object {
        const val PRELUDE = "package fixture\n\nimport dev.woge.host.WogeRoute\n\n"

        fun message(
            line: Int,
            rule: Rule,
            received: String,
        ): String = "Fixture.kt:$line ${rule.message(received)}"

        @JvmStatic
        fun fixtures(): List<Arguments> = FIXTURES

        private const val PROJECT = "@WogeRoute(\"/projects/{id}\") ProjectInput"
        private const val ARCHIVE = "@WogeRoute(\"/projects/{key}\") ArchiveInput"

        private val FIXTURES: List<Arguments> =
            listOf(
                Arguments.of(
                    Rule.ROUTE_PATH,
                    """
                    @WogeRoute("projects/{id}")
                    data class PageInput(val id: String)
                    """,
                    listOf(message(6, Rule.ROUTE_PATH, "@WogeRoute(\"projects/{id}\") PageInput")),
                ),
                Arguments.of(
                    Rule.ROUTE_PATH,
                    """
                    @WogeRoute("/a/{id}/b/{id}")
                    data class PageInput(val id: String)
                    """,
                    listOf(message(6, Rule.ROUTE_PATH, "@WogeRoute(\"/a/{id}/b/{id}\") PageInput")),
                ),
                Arguments.of(
                    Rule.ROUTE_TARGET,
                    """
                    @WogeRoute("/")
                    interface PageInput
                    """,
                    listOf(message(6, Rule.ROUTE_TARGET, "@WogeRoute(\"/\") PageInput")),
                ),
                Arguments.of(
                    Rule.ROUTE_TARGET,
                    """
                    @WogeRoute("/{id}")
                    class PageInput(id: String)
                    """,
                    listOf(message(6, Rule.ROUTE_TARGET, "constructor parameter id: String is not a val")),
                ),
                Arguments.of(
                    Rule.ROUTE_PATH_PARAMETER,
                    """
                    @WogeRoute("/{id}")
                    data class PageInput(val key: String?)
                    """,
                    listOf(message(6, Rule.ROUTE_PATH_PARAMETER, "@WogeRoute(\"/{id}\") PageInput has no property id")),
                ),
                Arguments.of(
                    Rule.ROUTE_PATH_PARAMETER,
                    """
                    @WogeRoute("/{id}")
                    data class PageInput(val id: String?)
                    """,
                    listOf(message(6, Rule.ROUTE_PATH_PARAMETER, "id: String?")),
                ),
                Arguments.of(
                    Rule.ROUTE_QUERY_PARAMETER,
                    """
                    @WogeRoute("/")
                    data class PageInput(val page: Int = 1)
                    """,
                    listOf(message(6, Rule.ROUTE_QUERY_PARAMETER, "page: Int")),
                ),
                Arguments.of(
                    Rule.ROUTE_VALUE_TYPE,
                    """
                    @WogeRoute("/")
                    data class PageInput(val tags: List<String>?)
                    """,
                    listOf(message(6, Rule.ROUTE_VALUE_TYPE, "tags: List<String>?")),
                ),
                Arguments.of(
                    Rule.ROUTE_VISIBILITY,
                    """
                    @WogeRoute("/")
                    private data object PageInput
                    """,
                    listOf(message(6, Rule.ROUTE_VISIBILITY, "@WogeRoute(\"/\") PageInput")),
                ),
                Arguments.of(
                    Rule.ROUTE_NAME,
                    """
                    @WogeRoute("/a")
                    data object Page

                    @WogeRoute("/b")
                    data object PageInput
                    """,
                    listOf(
                        message(6, Rule.ROUTE_NAME, "@WogeRoute(\"/a\") Page generates PageRoute"),
                        message(9, Rule.ROUTE_NAME, "@WogeRoute(\"/b\") PageInput generates PageRoute"),
                    ),
                ),
                Arguments.of(
                    Rule.ROUTE_COLLISION,
                    """
                    @WogeRoute("/projects/{id}")
                    data class ProjectInput(val id: String)

                    @WogeRoute("/projects/{key}")
                    data class ArchiveInput(val key: String)
                    """,
                    listOf(
                        message(6, Rule.ROUTE_COLLISION, "$PROJECT and $ARCHIVE"),
                        message(9, Rule.ROUTE_COLLISION, "$ARCHIVE and $PROJECT"),
                    ),
                ),
            )
    }
}
