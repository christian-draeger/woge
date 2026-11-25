package dev.woge.html

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class HtmlCompilerBoundaryTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `WOGE-XSS-001 ordinary strings cannot cross the raw HTML boundary`() {
        assertFixtureFails("raw-string", "UnsafeHtml")
    }

    @Test
    fun `generic event attribute escape hatch requires unsafe API opt-in`() {
        assertFixtureHasDiagnostics(
            "unsafe-attribute-without-opt-in",
            "[WOGE-HTML-UNSAFE-001]",
            "@OptIn(UnsafeWogeHtmlApi::class)",
        )
    }

    @Test
    fun `WOGE-XSS-002 ordinary strings cannot cross the validated URL boundary`() {
        assertFixtureFails("url-string", "HtmlUrl")
    }

    @Test
    fun `a complete stylesheet cannot cross the declaration-list boundary`() {
        assertFixtureFails("stylesheet-in-attribute", "CssDeclarations", "CssStylesheet")
    }

    @Test
    fun `a declaration list cannot cross the stylesheet boundary`() {
        assertFixtureFails("declarations-in-style-block", "CssStylesheet", "CssDeclarations")
    }

    @Test
    fun `mistyped standard tag name is rejected by the compiler`() {
        assertFixtureHasDiagnostics("html-tag-typo", "Unresolved reference", "sectoin")
    }

    @Test
    fun `the typed DSL has no base element that could retarget relative URLs`() {
        assertFixtureHasDiagnostics("html-base-element", "Unresolved reference", "base")
    }

    @Test
    fun `void element cannot receive nested HTML content`() {
        assertFixtureHasDiagnostics("html-void-content", "cannot be called in this context", "text")
    }

    @Test
    fun `escapable raw text element cannot receive nested HTML content`() {
        assertFixtureHasDiagnostics("html-rcdata-content", "cannot be called in this context", "strong")
    }

    @Test
    fun `active raw text element cannot receive an ordinary source string`() {
        assertFixtureHasDiagnostics("html-script-content", "cannot be called in this context", "text")
    }

    @Test
    fun `an isolated consumer can compile typed HTML`() {
        val (exit, diagnostics) = compileFixture("typed-html")
        assertEquals(ExitCode.OK, exit, diagnostics.allErrors())
    }

    private fun assertFixtureFails(
        name: String,
        expectedType: String,
        actualType: String = "String",
    ) {
        val output = compileFixtureAndFail(name)

        assertTrue(output.contains("Argument type mismatch"), output)
        assertTrue(output.contains(actualType), output)
        assertTrue(output.contains(expectedType), output)
    }

    private fun assertFixtureHasDiagnostics(
        name: String,
        vararg expectedFragments: String,
    ) {
        val output = compileFixtureAndFail(name)

        expectedFragments.forEach { fragment -> assertTrue(output.contains(fragment), output) }
    }

    private fun compileFixtureAndFail(name: String): String {
        val (exit, diagnostics) = compileFixture(name)
        assertEquals(ExitCode.COMPILATION_ERROR, exit, diagnostics.allErrors())
        return diagnostics.sourceErrors().also {
            assertTrue(it.isNotBlank(), diagnostics.allErrors())
        }
    }

    private fun compileFixture(name: String): Pair<ExitCode, FixtureDiagnostics> {
        val fixture = projectDirectory.resolve(name).createDirectories().resolve("Fixture.kt")
        val resource = requireNotNull(javaClass.getResource("/compiler-fixtures/$name.kt")).toURI()
        fixture.writeText(Path.of(resource).readText())

        val coreJar = requireNotNull(System.getProperty("woge.core.jar"))
        val dependencies = requireNotNull(System.getProperty("woge.core.fixture.classpath"))
        val arguments =
            K2JVMCompilerArguments().apply {
                classpath = listOf(coreJar, dependencies).joinToString(File.pathSeparator)
                noStdlib = true
                noReflect = true
                jvmTarget = "17"
                explicitApi = "strict"
                destination = fixture.parent.resolve("classes").toString()
                freeArgs = listOf(fixture.toString())
            }
        val diagnostics = FixtureDiagnostics(fixture)
        return K2JVMCompiler().exec(diagnostics, Services.EMPTY, arguments) to diagnostics
    }
}

private class FixtureDiagnostics(
    private val source: Path,
) : MessageCollector {
    private val errors = mutableListOf<Pair<String?, String>>()

    override fun clear() {
        errors.clear()
    }

    override fun hasErrors(): Boolean = errors.isNotEmpty()

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        if (severity.isError) errors += location?.path to message
    }

    fun allErrors(): String = errors.joinToString("\n") { (path, message) -> "${path ?: "<compiler>"}: $message" }

    fun sourceErrors(): String =
        errors
            .filter { (path, _) -> path != null && Path.of(path) == source }
            .joinToString("\n") { (_, message) -> message }
}
