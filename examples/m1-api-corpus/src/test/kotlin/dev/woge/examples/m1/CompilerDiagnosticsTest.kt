package dev.woge.examples.m1

import dev.woge.host.PageUseCase
import dev.woge.html.HtmlWriter
import dev.woge.protocol.PatchTarget
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Path

class CompilerDiagnosticsTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `invalid public API shapes fail with source-located searchable diagnostics`() {
        fixtures.forEachIndexed { index, fixture ->
            val output = ByteArrayOutputStream()
            val destination = temporaryDirectory.resolve("classes-$index").toFile().apply { mkdirs() }
            val source = requireNotNull(javaClass.getResource("/negative/${fixture.file}"))
            val exitCode =
                PrintStream(output, true, Charsets.UTF_8).use { messages ->
                    K2JVMCompiler().exec(
                        messages,
                        "-no-stdlib",
                        "-no-reflect",
                        "-Xrender-internal-diagnostic-names",
                        "-classpath",
                        compilationClasspath,
                        "-d",
                        destination.absolutePath,
                        Path.of(source.toURI()).toString(),
                    )
                }
            val diagnostics = output.toString(Charsets.UTF_8)

            assertEquals(ExitCode.COMPILATION_ERROR, exitCode, fixture.id)
            assertTrue(diagnostics.contains(fixture.file), "$fixture did not report its source file:\n$diagnostics")
            fixture.expectedFragments.forEach { expected ->
                assertTrue(diagnostics.contains(expected), "$fixture did not report '$expected':\n$diagnostics")
            }
        }
    }

    private val compilationClasspath: String =
        listOf(
            HtmlWriter::class.java,
            PatchTarget::class.java,
            PageUseCase::class.java,
            Unit::class.java,
            Language::class.java,
        ).map { marker ->
            File(
                marker.protectionDomain.codeSource.location
                    .toURI(),
            ).absolutePath
        }.distinct()
            .joinToString(File.pathSeparator)

    private data class Fixture(
        val id: String,
        val file: String,
        val expectedFragments: List<String>,
    )

    private companion object {
        val fixtures =
            listOf(
                Fixture(
                    id = "WOGE-COMPILE-HTML-001",
                    file = "html-raw-string.kt",
                    expectedFragments = listOf("[ARGUMENT_TYPE_MISMATCH]", "String", "UnsafeHtml"),
                ),
                Fixture(
                    id = "WOGE-COMPILE-HTML-002",
                    file = "html-url-string.kt",
                    expectedFragments = listOf("[ARGUMENT_TYPE_MISMATCH]", "String", "HtmlUrl"),
                ),
                Fixture(
                    id = "WOGE-COMPILE-HTML-003",
                    file = "unsafe-opt-in-required.kt",
                    expectedFragments =
                        listOf(
                            "[OPT_IN_USAGE_ERROR]",
                            "[WOGE-HTML-UNSAFE-001]",
                            "Add @OptIn(UnsafeWogeHtmlApi::class) only at the reviewed boundary",
                        ),
                ),
                Fixture(
                    id = "WOGE-COMPILE-PROTOCOL-001",
                    file = "protocol-target-shape.kt",
                    expectedFragments =
                        listOf(
                            "[ARGUMENT_TYPE_MISMATCH]",
                            "RegionTargetId",
                            "PageEpoch",
                        ),
                ),
                Fixture(
                    id = "WOGE-COMPILE-PORT-001",
                    file = "portable-framework-leak.kt",
                    expectedFragments = listOf("[UNRESOLVED_REFERENCE]", "springframework", "ServerRequest"),
                ),
            )
    }
}
