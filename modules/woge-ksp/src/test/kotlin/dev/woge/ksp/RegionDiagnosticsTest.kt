package dev.woge.ksp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Locks every declaration diagnostic: stable ID, source location, rule, received shape and valid form.
 *
 * People and coding assistants repair code from these messages, so a wording change is a reviewed change.
 */
class RegionDiagnosticsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    internal fun `invalid declarations are rejected with a located diagnostic`(
        rule: Rule,
        source: String,
        expected: List<String>,
    ) {
        val result = runKsp(mapOf("Fixture.kt" to PRELUDE + source.trimIndent()))

        assertEquals(expected, result.errors)
        assertTrue(expected.all { it.contains(rule.id) })
        assertEquals(emptyMap<String, String>(), result.generated)
    }

    private companion object {
        const val PRELUDE =
            "package fixture\n\n" +
                "import dev.woge.host.WogeComponent\n" +
                "import dev.woge.host.WogeKey\n" +
                "import dev.woge.host.WogeRegion\n" +
                "import dev.woge.html.HtmlWriter\n\n"

        fun message(
            line: Int,
            rule: Rule,
            received: String,
        ): String = "Fixture.kt:$line ${rule.message(received)}"

        @JvmStatic
        fun fixtures(): List<Arguments> = FIXTURES

        private val FIXTURES: List<Arguments> =
            listOf(
                Arguments.of(
                    Rule.REGION_RECEIVER,
                    """
                    @WogeRegion
                    fun summary(input: String) = Unit
                    """,
                    listOf(message(9, Rule.REGION_RECEIVER, "fun summary(input: String)")),
                ),
                Arguments.of(
                    Rule.REGION_RECEIVER,
                    """
                    object Page {
                        @WogeRegion
                        fun HtmlWriter.summary(input: String) = Unit
                    }
                    """,
                    listOf(message(10, Rule.REGION_RECEIVER, "fun HtmlWriter.summary(input: String) inside Page")),
                ),
                Arguments.of(
                    Rule.REGION_SHAPE,
                    """
                    @WogeRegion
                    fun HtmlWriter.summary(title: String, count: Int) = Unit
                    """,
                    listOf(message(9, Rule.REGION_SHAPE, "fun HtmlWriter.summary(title: String, count: Int)")),
                ),
                Arguments.of(
                    Rule.REGION_SHAPE,
                    """
                    @WogeRegion
                    suspend fun HtmlWriter.summary(input: String) = Unit
                    """,
                    listOf(message(9, Rule.REGION_SHAPE, "suspend fun HtmlWriter.summary(input: String)")),
                ),
                Arguments.of(
                    Rule.REGION_SHAPE,
                    """
                    @WogeRegion
                    fun <T> HtmlWriter.summary(input: T) = Unit
                    """,
                    listOf(message(9, Rule.REGION_SHAPE, "fun <T> HtmlWriter.summary(input: T)")),
                ),
                Arguments.of(
                    Rule.REGION_VISIBILITY,
                    """
                    private class Secret

                    @WogeRegion
                    fun HtmlWriter.summary(input: Secret) = Unit
                    """,
                    listOf(message(11, Rule.REGION_VISIBILITY, "fun HtmlWriter.summary(input: Secret)")),
                ),
                Arguments.of(
                    Rule.REGION_COMPONENT,
                    """
                    class TaskRow

                    @WogeRegion(component = TaskRow::class)
                    fun HtmlWriter.status(input: String) = Unit
                    """,
                    listOf(message(11, Rule.REGION_COMPONENT, "@WogeRegion(component = TaskRow::class)")),
                ),
                Arguments.of(
                    Rule.COMPONENT_KEYS,
                    """
                    @WogeComponent
                    class TaskRow(@WogeKey val id: Long, @WogeKey val slug: String)
                    """,
                    listOf(message(9, Rule.COMPONENT_KEYS, "2 @WogeKey parameters: id, slug")),
                ),
                Arguments.of(
                    Rule.COMPONENT_KEY_TYPE,
                    """
                    @WogeComponent
                    class TaskRow(@WogeKey val id: Double)
                    """,
                    listOf(message(9, Rule.COMPONENT_KEY_TYPE, "@WogeKey id: kotlin.Double")),
                ),
                Arguments.of(
                    Rule.COMPONENT_KEY_TYPE,
                    """
                    @WogeComponent
                    class TaskRow(@WogeKey val id: String?)
                    """,
                    listOf(message(9, Rule.COMPONENT_KEY_TYPE, "@WogeKey id: kotlin.String?")),
                ),
                Arguments.of(
                    Rule.DESCRIPTOR_NAME,
                    """
                    @WogeRegion
                    fun HtmlWriter.summary(input: String) = Unit
                    """ + "\n" +
                        """
                    @WogeRegion
                    fun HtmlWriter.summary(input: Int) = Unit
                    """,
                    listOf(
                        message(9, Rule.DESCRIPTOR_NAME, "fun HtmlWriter.summary(input: String) $GENERATES_SUMMARY"),
                        message(13, Rule.DESCRIPTOR_NAME, "fun HtmlWriter.summary(input: Int) $GENERATES_SUMMARY"),
                    ),
                ),
                Arguments.of(
                    Rule.IDENTITY_NAME,
                    """
                    @WogeRegion
                    fun HtmlWriter.`summäry`(input: String) = Unit
                    """,
                    listOf(message(9, Rule.IDENTITY_NAME, "fixture.summäry")),
                ),
            )
    }
}

private const val GENERATES_SUMMARY = "generates SummaryRegion"
