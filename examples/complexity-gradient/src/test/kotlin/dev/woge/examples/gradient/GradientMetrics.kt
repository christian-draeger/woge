package dev.woge.examples.gradient

import kotlinx.serialization.Serializable
import java.io.File

const val SCHEMA_VERSION = 1
const val SUITE_VERSION = "1"

/** One rung of the ladder: the task id, its Kotlin package directory and the Spring route file. */
data class Task(
    val id: String,
    val packageName: String,
)

val ladder =
    listOf(
        Task("static-page", "t01staticpage"),
        Task("component", "t02component"),
        Task("form", "t03form"),
        Task("validation", "t04validation"),
        Task("enhanced-action", "t05enhancedaction"),
        Task("deferred-region", "t06deferredregion"),
        Task("live-update", "t07liveupdate"),
        Task("custom-javascript", "t08customjavascript"),
        Task("island", "t09island"),
    )

@Serializable
data class TaskMetrics(
    val id: String,
    val files: Int,
    val nonblankLines: Int,
    val kotlinLines: Int,
    val javascriptLines: Int,
    val cssLines: Int,
    val wogeDeclarations: Int,
    val conceptCount: Int,
    val concepts: List<String>,
    val manualConfiguration: Int,
    val forbiddenConcepts: List<String>,
    val sharedSetup: List<String>,
    val knownGaps: List<String>,
)

@Serializable
data class Gradient(
    val schemaVersion: Int,
    val suiteVersion: String,
    val tasks: List<TaskMetrics>,
)

private val forbiddenNames =
    setOf(
        "PatchProtocolVersion",
        "PageEpoch",
        "TargetRevision",
        "InteractionSequence",
        "ServerGeneration",
        "DevGeneration",
        "PageIdentity",
        "RenderIdentitySecret",
        "FormBody",
        "MultipartBody",
        "PatchStreamLimits",
        "ResourceLimit",
        "MutationReservations",
        "MutationRequestIdentity",
        "DescriptorMetadata",
        "RequestHeaders",
        "RequestSecurity",
        "RequestTrace",
        "RequestId",
        "CorrelationId",
    )
private val forbiddenPackages = listOf("dev.woge.protocol.", "dev.woge.development.")
private val importPattern = Regex("""^import\s+(dev\.woge\.[\w.]+)""")
private val declarationPattern = Regex("""@Woge(Route|Action|Region|Component|Key)\b""")
private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

private const val SUPPORT_PACKAGE = "dev.woge.examples.gradient.support."

private fun forbiddenIn(imports: List<String>): List<String> =
    imports
        .filter { import ->
            forbiddenPackages.any(import::startsWith) || import.substringAfterLast('.') in forbiddenNames
        }.map { it.substringAfterLast('.') }
        .distinct()
        .sorted()

private fun importsOf(lines: List<String>): List<String> =
    lines
        .mapNotNull {
            importPattern
                .find(it)
                ?.groupValues
                ?.get(1)
                ?.substringBefore(" as ")
        }.distinct()

/** Non-blank lines that are not comments (`//` and block comments). */
fun codeLines(file: File): List<String> =
    file
        .readText()
        .replace(blockComment, "")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("//") }

/**
 * Computes the metrics for one task from the sources under [root] (the module directory).
 *
 * `manualConfiguration` counts the `handlers.` calls in the task's `Routes.kt`: each one binds a typed
 * page, action, deferred region or live route to Spring WebFlux and has to be written by hand.
 * Concepts are the distinct simple names imported from `dev.woge.*`, excluding the examples' own packages.
 */
fun measure(
    root: File,
    task: Task,
): TaskMetrics {
    val kotlinFiles =
        File(root, "src/main/kotlin/dev/woge/examples/gradient/${task.packageName}")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .sortedBy { it.name }
            .toList()
    val resources =
        File(root, "src/main/resources/gradient/${task.id}")
            .walkTopDown()
            .filter { it.isFile }
            .toList()
    val kotlin = kotlinFiles.flatMap(::codeLines)
    val javascript = resources.filter { it.extension == "js" }.flatMap(::codeLines)
    val css = resources.filter { it.extension == "css" }.flatMap(::codeLines)
    val allImports = importsOf(kotlin)
    val imports = allImports.filterNot { it.startsWith("dev.woge.examples.") }
    val concepts = imports.map { it.substringAfterLast('.') }.distinct().sorted()
    // Application-wide setup shared by several tasks still has to be written once; its internals are known gaps.
    val sharedSetup = allImports.filter { it.startsWith(SUPPORT_PACKAGE) }.map { it.substringAfterLast('.') }.sorted()
    val supportDirectory = File(root, "src/main/kotlin/dev/woge/examples/gradient/support")
    val knownGaps =
        sharedSetup
            .flatMap { name -> importsOf(codeLines(File(supportDirectory, "$name.kt"))) }
            .let(::forbiddenIn)
    val routes = kotlinFiles.single { it.name == "Routes.kt" }.readText()
    return TaskMetrics(
        id = task.id,
        files = kotlinFiles.size + resources.size,
        nonblankLines = kotlin.size + javascript.size + css.size,
        kotlinLines = kotlin.size,
        javascriptLines = javascript.size,
        cssLines = css.size,
        wogeDeclarations = kotlin.sumOf { declarationPattern.findAll(it).count() },
        conceptCount = concepts.size,
        concepts = concepts,
        manualConfiguration = Regex("""\bhandlers\.""").findAll(routes).count(),
        forbiddenConcepts = forbiddenIn(imports),
        sharedSetup = sharedSetup,
        knownGaps = knownGaps,
    )
}
