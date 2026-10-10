package dev.woge.development.process

import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentDiagnosticCode
import dev.woge.development.DevelopmentDiagnosticSeverity
import dev.woge.development.DevelopmentDiagnosticSummary
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.orchestrator.DevelopmentBuildAdapter
import dev.woge.development.orchestrator.DevelopmentBuildResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

/** Compiles the fixture sources and copies the classes into [live], like a Gradle `classes` build. */
@OptIn(ExperimentalWogeDevelopmentApi::class)
internal fun fixtureBuild(
    root: Path,
    live: Path,
    classpath: String,
    sources: List<Path>,
): DevelopmentBuildAdapter =
    DevelopmentBuildAdapter {
        withContext(Dispatchers.IO) {
            val destination = root.resolve("candidate-${it.buildId.value}").createDirectories()
            val messages = ByteArrayOutputStream()
            val exit =
                PrintStream(messages).use { output ->
                    K2JVMCompiler().exec(
                        output,
                        "-no-stdlib",
                        "-no-reflect",
                        "-jvm-target",
                        "17",
                        "-classpath",
                        classpath,
                        "-d",
                        destination.toString(),
                        *sources.map(Path::toString).toTypedArray(),
                    )
                }
            if (exit == ExitCode.OK) {
                Files.walk(destination).use { paths ->
                    paths.filter { Files.isRegularFile(it) }.forEach { file ->
                        val target = live.resolve(destination.relativize(file))
                        target.parent.createDirectories()
                        file.copyTo(target, overwrite = true)
                    }
                }
                DevelopmentBuildResult.Succeeded()
            } else {
                DevelopmentBuildResult.Failed(
                    listOf(
                        DevelopmentDiagnostic(
                            DevelopmentDiagnosticCode.of("FIXTURE-COMPILE-FAILED"),
                            DevelopmentDiagnosticSeverity.ERROR,
                            DevelopmentDiagnosticSummary.of("Fix the Kotlin source and save again."),
                        ),
                    ),
                )
            }
        }
    }
