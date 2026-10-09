package dev.woge.development.gradle

import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentDiagnosticCode
import dev.woge.development.DevelopmentDiagnosticSeverity
import dev.woge.development.DevelopmentDiagnosticSummary
import dev.woge.development.DevelopmentSourceLocation
import dev.woge.development.DevelopmentSourcePath
import dev.woge.development.ExperimentalWogeDevelopmentApi
import java.net.URI
import java.nio.file.Path

/**
 * Turns Gradle output into structured diagnostics.
 *
 * Two kinds of lines become source-located diagnostics:
 * - Kotlin compiler errors: `e: file:///path/File.kt:12:5 message`;
 * - KSP processor errors: `e: [ksp] /path/File.kt:12: WOGE-REF-002 message`, followed by
 *   `Received:` and `Valid:` lines. The Woge diagnostic ID becomes the diagnostic code.
 *
 * An error inside generated code gets [generatedCode], so the session can regenerate once.
 * Everything else stays in the raw build details.
 */
@ExperimentalWogeDevelopmentApi
internal object KotlinDiagnostics {
    private val compilerLine = Regex("""^e: (file://)?(.+?):(\d+):(\d+) (.+)$""")
    private val processorLine = Regex("""^e: \[ksp] (.+?):(\d+): (.+)$""")
    private val wogeCode = Regex("""^WOGE-[A-Z]+-\d+""")
    private val continuation = listOf("Received:", "Valid:")
    private const val URI_SCHEME = 1
    private const val PATH = 2
    private const val LINE = 3
    private const val COLUMN = 4
    private const val TEXT = 5
    private const val MAX_SUMMARY = 500
    private const val MAX_DIAGNOSTICS = 20

    val kotlinError: DevelopmentDiagnosticCode = DevelopmentDiagnosticCode.of("KOTLIN-COMPILE-ERROR")
    val processorError: DevelopmentDiagnosticCode = DevelopmentDiagnosticCode.of("KSP-ERROR")
    val generatedCode: DevelopmentDiagnosticCode = DevelopmentDiagnosticCode.of("WOGE-GENERATED-CODE")
    val buildFailed: DevelopmentDiagnosticCode = DevelopmentDiagnosticCode.of("GRADLE-BUILD-FAILED")

    fun parse(
        lines: List<String>,
        projectDirectory: Path,
    ): List<DevelopmentDiagnostic> {
        val trimmed = lines.map(String::trim)
        val errors =
            trimmed
                .asSequence()
                .mapIndexedNotNull { index, line ->
                    compiler(line, projectDirectory) ?: processor(line, trimmed.drop(index + 1), projectDirectory)
                }.distinct()
                .take(MAX_DIAGNOSTICS)
                .toList()
        return errors.ifEmpty {
            listOf(
                DevelopmentDiagnostic(
                    code = buildFailed,
                    severity = DevelopmentDiagnosticSeverity.ERROR,
                    summary =
                        summary(
                            "The Gradle build failed. Open the build details, fix the problem and save again; " +
                                "the last working version keeps running.",
                        ),
                ),
            )
        }
    }

    private fun compiler(
        line: String,
        projectDirectory: Path,
    ): DevelopmentDiagnostic? {
        val groups = compilerLine.matchEntire(line)?.groupValues ?: return null
        val path = groups[PATH]
        val file = if (groups[URI_SCHEME].isNotEmpty()) Path.of(URI("file://$path")) else Path.of(path)
        val location = location(file, groups[LINE].toInt(), groups[COLUMN].toInt(), projectDirectory)
        val text = groups[TEXT]
        return if (location?.path?.isGenerated() == true) {
            error(generatedCode, "Generated code does not compile: $text", location)
        } else {
            error(kotlinError, text, location)
        }
    }

    private fun processor(
        line: String,
        following: List<String>,
        projectDirectory: Path,
    ): DevelopmentDiagnostic? {
        val (path, row, text) = processorLine.matchEntire(line)?.destructured ?: return null
        val details = following.takeWhile { next -> continuation.any(next::startsWith) }
        val code = wogeCode.find(text)?.value?.let(DevelopmentDiagnosticCode::of) ?: processorError
        val message = (listOf(text) + details).joinToString(" ")
        return error(code, message, location(Path.of(path), row.toInt(), 1, projectDirectory))
    }

    private fun error(
        code: DevelopmentDiagnosticCode,
        message: String,
        location: DevelopmentSourceLocation?,
    ): DevelopmentDiagnostic =
        DevelopmentDiagnostic(
            code = code,
            severity = DevelopmentDiagnosticSeverity.ERROR,
            summary = summary(message),
            location = location,
        )

    private fun location(
        file: Path,
        line: Int,
        column: Int,
        projectDirectory: Path,
    ): DevelopmentSourceLocation? =
        runCatching {
            val relative =
                projectDirectory
                    .toAbsolutePath()
                    .normalize()
                    .relativize(file.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/')
            require(!relative.startsWith("..") && relative.isNotBlank())
            DevelopmentSourceLocation(DevelopmentSourcePath.of(relative), line, column)
        }.getOrNull()

    private fun DevelopmentSourcePath.isGenerated(): Boolean = value.startsWith("build/generated/")

    private fun summary(message: String): DevelopmentDiagnosticSummary =
        DevelopmentDiagnosticSummary.of(
            message
                .map { if (it.isISOControl()) ' ' else it }
                .joinToString("")
                .trim()
                .take(MAX_SUMMARY)
                .ifBlank { "Compilation failed" },
        )
}
