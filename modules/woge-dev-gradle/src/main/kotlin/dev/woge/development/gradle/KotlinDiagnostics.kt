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
 * Kotlin reports errors as `e: file:///path/File.kt:12:5 message`. Only those lines become
 * source-located diagnostics; everything else stays in the raw build details.
 */
@ExperimentalWogeDevelopmentApi
internal object KotlinDiagnostics {
    private val compilerLine = Regex("""^(e|w): (file://)?(.+?):(\d+):(\d+) (.+)$""")
    private const val MAX_SUMMARY = 500
    private const val MAX_DIAGNOSTICS = 20

    val kotlinError: DevelopmentDiagnosticCode = DevelopmentDiagnosticCode.of("KOTLIN-COMPILE-ERROR")
    val buildFailed: DevelopmentDiagnosticCode = DevelopmentDiagnosticCode.of("GRADLE-BUILD-FAILED")

    fun parse(
        lines: List<String>,
        projectDirectory: Path,
    ): List<DevelopmentDiagnostic> {
        val errors =
            lines
                .asSequence()
                .mapNotNull { compilerLine.matchEntire(it.trim()) }
                .map { CompilerMessage.of(it) }
                .filter { it.severity == "e" }
                .map { message ->
                    DevelopmentDiagnostic(
                        code = kotlinError,
                        severity = DevelopmentDiagnosticSeverity.ERROR,
                        summary = summary(message.text),
                        location = location(message, projectDirectory),
                    )
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

    private fun location(
        message: CompilerMessage,
        projectDirectory: Path,
    ): DevelopmentSourceLocation? =
        runCatching {
            val file = if (message.fileUri) Path.of(URI("file://${message.path}")) else Path.of(message.path)
            val relative =
                projectDirectory
                    .toAbsolutePath()
                    .normalize()
                    .relativize(file.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/')
            require(!relative.startsWith("..") && relative.isNotBlank())
            DevelopmentSourceLocation(DevelopmentSourcePath.of(relative), message.line.toInt(), message.column.toInt())
        }.getOrNull()

    private fun summary(message: String): DevelopmentDiagnosticSummary =
        DevelopmentDiagnosticSummary.of(
            message
                .map { if (it.isISOControl()) ' ' else it }
                .joinToString("")
                .trim()
                .take(MAX_SUMMARY)
                .ifBlank { "Compilation failed" },
        )

    private class CompilerMessage(
        val severity: String,
        val fileUri: Boolean,
        val path: String,
        val line: String,
        val column: String,
        val text: String,
    ) {
        companion object {
            fun of(match: MatchResult): CompilerMessage {
                val groups = match.groupValues.drop(1)
                return CompilerMessage(
                    severity = groups[0],
                    fileUri = groups[1].isNotEmpty(),
                    path = groups[2],
                    line = groups[3],
                    column = groups[4],
                    text = groups.last(),
                )
            }
        }
    }
}
