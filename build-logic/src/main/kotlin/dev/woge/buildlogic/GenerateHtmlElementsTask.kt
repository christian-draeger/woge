package dev.woge.buildlogic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Generates Woge's common HTML element wrappers from a pinned Webref-derived dataset. */
@CacheableTask
public abstract class GenerateHtmlElementsTask : DefaultTask() {
    /** The checked-in, provenance-documented element dataset. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val datasetFile: RegularFileProperty

    /** Root directory for the generated Kotlin source set. */
    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    /** Generates the wrapper source. */
    @TaskAction
    public fun generate() {
        val source = HtmlElementSourceGenerator.generate(datasetFile.get().asFile.readText())
        val target =
            outputDirectory
                .file("dev/woge/html/GeneratedHtmlElements.kt")
                .get()
                .asFile
                .toPath()
        Files.createDirectories(target.parent)
        Files.writeString(target, source, StandardCharsets.UTF_8)
    }
}

/** Fails when the pinned dataset changes without an explicit provenance and ABI review. */
@DisableCachingByDefault(because = "This verification task has no outputs and should always inspect its input")
public abstract class VerifyHtmlElementDatasetTask : DefaultTask() {
    /** The checked-in Webref-derived element dataset. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val datasetFile: RegularFileProperty

    /** SHA-256 recorded when the upstream dataset was reviewed. */
    @get:Input
    public var expectedSha256: String = ""

    /** Verifies provenance drift and deterministic generation. */
    @TaskAction
    public fun verify() {
        val bytes = datasetFile.get().asFile.readBytes()
        val actualSha256 =
            MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { byte -> "%02x".format(byte) }
        check(actualSha256 == expectedSha256) {
            "HTML element dataset changed: expected $expectedSha256 but found $actualSha256. " +
                "Review provenance, generated ABI and config/html-elements/README.md before updating the pin."
        }

        val dataset = bytes.toString(StandardCharsets.UTF_8)
        val first = HtmlElementSourceGenerator.generate(dataset)
        val second = HtmlElementSourceGenerator.generate(dataset)
        check(first == second) { "HTML element source generation is not deterministic" }
    }
}

internal object HtmlElementSourceGenerator {
    private val elementName = Regex("[a-z][a-z0-9-]*")
    private val kotlinKeywords =
        setOf(
            "as",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "interface",
            "is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "typeof",
            "val",
            "var",
            "when",
            "while",
        )

    fun generate(dataset: String): String {
        val definitions = parse(dataset)
        require(definitions.any { it.name == "html" }) { "Dataset must contain the html element" }
        require(definitions.any { it.name == "style" && it.kind == ElementKind.RAW_TEXT }) {
            "Dataset must classify style as RAW_TEXT"
        }

        return buildString {
            appendLine("package dev.woge.html")
            appendLine()
            appendLine("// Generated from config/html-elements/webref-elements-2.8.0.tsv. Do not edit by hand.")
            definitions.forEach { definition ->
                appendLine()
                appendWrapper(definition)
            }
        }
    }

    private fun parse(dataset: String): List<ElementDefinition> {
        val records = dataset.lineSequence().filterNot { it.isBlank() || it.startsWith("#") }.toList()
        require(records.firstOrNull() == "name\tkind\tinterface\tspecification") {
            "Dataset must start with the expected TSV header"
        }
        val definitions =
            records.drop(1).mapIndexed { index, record ->
                val columns = record.split('\t')
                require(columns.size == 4) { "Invalid dataset record ${index + 2}: expected four columns" }
                val (name, rawKind, domInterface, specification) = columns
                require(elementName.matches(name)) { "Invalid element name '$name'" }
                require(domInterface.isNotBlank()) { "Element '$name' must declare its DOM interface" }
                require(specification.startsWith("https://html.spec.whatwg.org/")) {
                    "Element '$name' must link to the WHATWG HTML specification"
                }
                ElementDefinition(name, ElementKind.valueOf(rawKind), domInterface, specification)
            }
        val duplicates = definitions.groupingBy(ElementDefinition::name).eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate element names: ${duplicates.sorted().joinToString()}" }
        return definitions
    }

    private fun StringBuilder.appendWrapper(definition: ElementDefinition) {
        if (definition.name == "style" && definition.kind == ElementKind.RAW_TEXT) {
            appendLine("// The specialized style(CssStylesheet, ...) wrapper is maintained in HeadAssets.kt.")
            return
        }
        val functionName = definition.name.asKotlinIdentifier()
        appendLine("/**")
        appendLine(" * Writes the standard `${definition.name}` element (`${definition.domInterface}`).")
        appendLine(" *")
        appendLine(" * [HTML specification](${definition.specification})")
        when (definition.kind) {
            ElementKind.VOID -> {
                appendLine(" */")
                appendLine(
                    "public fun HtmlWriter.$functionName(attributes: Attributes.() -> Unit = {}): Unit = " +
                        "voidElement(\"${definition.name}\", attributes)",
                )
            }

            ElementKind.NORMAL -> {
                appendLine(" */")
                appendLine("public fun HtmlWriter.$functionName(")
                appendLine("    attributes: Attributes.() -> Unit = {},")
                appendLine("    content: HtmlWriter.() -> Unit = {},")
                appendLine("): Unit = element(\"${definition.name}\", attributes, content)")
            }

            ElementKind.ESCAPABLE_RAW_TEXT -> {
                appendLine(" *")
                appendLine(" * [value] is escaped as text; nested markup is deliberately unavailable in this context.")
                appendLine(" */")
                appendLine("public fun HtmlWriter.$functionName(")
                appendLine("    value: String,")
                appendLine("    attributes: Attributes.() -> Unit = {},")
                appendLine("): Unit = escapableRawTextElement(\"${definition.name}\", attributes, value)")
            }

            ElementKind.RAW_TEXT -> appendRawTextWrapper(definition, functionName)
        }
    }

    private fun StringBuilder.appendRawTextWrapper(
        definition: ElementDefinition,
        functionName: String,
    ) {
        when (definition.name) {
            "script", "iframe" -> {
                appendLine(" *")
                appendLine(" * Only an empty body is exposed so ordinary strings cannot become active raw text.")
                appendLine(" */")
                appendLine(
                    "public fun HtmlWriter.$functionName(attributes: Attributes.() -> Unit = {}): Unit =",
                )
                appendLine("    rawTextElement(\"${definition.name}\", attributes, content = \"\")")
            }

            else -> error("Unsupported raw-text element '${definition.name}'")
        }
    }

    private fun String.asKotlinIdentifier(): String = if (this in kotlinKeywords) "`$this`" else this
}

private enum class ElementKind {
    NORMAL,
    VOID,
    ESCAPABLE_RAW_TEXT,
    RAW_TEXT,
}

private data class ElementDefinition(
    val name: String,
    val kind: ElementKind,
    val domInterface: String,
    val specification: String,
)
