package dev.woge.ksp

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import java.io.OutputStream
import java.nio.file.Files

/** Result of one in-process KSP run over a few source files. */
internal class KspResult(
    val errors: List<String>,
    val generated: Map<String, String>,
    val dependencies: Map<String, RecordedDependencies>,
    val resources: Map<String, String>,
)

/** The `Dependencies` of one generated file, captured while KSP's symbols are still valid. */
internal data class RecordedDependencies(
    val aggregating: Boolean,
    val originatingFiles: List<String>,
)

/** Runs the Woge processor with KSP2 in this JVM, so tests see real KSP symbols and locations. */
internal fun runKsp(
    sources: Map<String, String>,
    additionalProviders: List<SymbolProcessorProvider> = emptyList(),
): KspResult {
    val root = Files.createTempDirectory("woge-ksp").toFile()
    try {
        val sourceRoot = File(root, "src").apply { mkdirs() }
        sources.forEach { (name, text) -> File(sourceRoot, name).writeText(text) }
        val output = File(root, "out")
        val logger = CollectingLogger()
        val dependencies = sortedMapOf<String, RecordedDependencies>()
        val provider =
            object : SymbolProcessorProvider {
                override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
                    WogeProcessor(RecordingCodeGenerator(environment.codeGenerator, dependencies), environment.logger)
            }
        val config =
            KSPJvmConfig
                .Builder()
                .apply {
                    moduleName = "test"
                    sourceRoots = listOf(sourceRoot)
                    javaSourceRoots = emptyList()
                    libraries = System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
                    jdkHome = File(System.getProperty("java.home"))
                    jvmTarget = "17"
                    languageVersion = "2.2"
                    apiVersion = "2.2"
                    projectBaseDir = root
                    outputBaseDir = output
                    cachesDir = File(output, "caches")
                    classOutputDir = File(output, "classes")
                    kotlinOutputDir = File(output, "kotlin")
                    javaOutputDir = File(output, "java")
                    resourceOutputDir = File(output, "resources")
                }.build()
        KotlinSymbolProcessing(config, listOf(provider) + additionalProviders, logger).execute()
        val generated =
            File(output, "kotlin")
                .walkTopDown()
                .filter(File::isFile)
                .associate { it.name to it.readText() }
                .toSortedMap()
        val resourceRoot = File(output, "resources")
        val resources =
            resourceRoot
                .walkTopDown()
                .filter(File::isFile)
                .associate { it.relativeTo(resourceRoot).invariantSeparatorsPath to it.readText() }
                .toSortedMap()
        return KspResult(logger.errors, generated, dependencies, resources)
    } finally {
        root.deleteRecursively()
    }
}

private class CollectingLogger : KSPLogger {
    val errors = mutableListOf<String>()

    override fun error(
        message: String,
        symbol: KSNode?,
    ) {
        val location = symbol?.location as? FileLocation
        errors += location?.let { "${File(it.filePath).name}:${it.lineNumber} $message" } ?: message
    }

    override fun exception(e: Throwable) {
        errors += "exception: $e"
    }

    override fun info(
        message: String,
        symbol: KSNode?,
    ) = Unit

    override fun logging(
        message: String,
        symbol: KSNode?,
    ) = Unit

    override fun warn(
        message: String,
        symbol: KSNode?,
    ) = Unit
}

private class RecordingCodeGenerator(
    private val delegate: CodeGenerator,
    private val dependencies: MutableMap<String, RecordedDependencies>,
) : CodeGenerator by delegate {
    override fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        extensionName: String,
    ): OutputStream {
        this.dependencies["$fileName.$extensionName"] =
            RecordedDependencies(dependencies.aggregating, dependencies.originatingFiles.map { it.fileName }.sorted())
        return delegate.createNewFile(dependencies, packageName, fileName, extensionName)
    }
}
