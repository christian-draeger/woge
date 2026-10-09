package dev.woge.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate

/** Entry point that KSP finds through `META-INF/services`. */
public class WogeProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RegionProcessor(environment.codeGenerator, environment.logger)
}

/**
 * Generates one descriptor file per `@WogeRegion` function.
 *
 * Each file depends only on the region's own source file and its component's source file. KSP can
 * therefore delete exactly the outputs of changed or removed sources; there is no shared registry
 * that could go stale.
 */
internal class RegionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val reader = DeclarationReader(logger)
        val annotated =
            resolver.getSymbolsWithAnnotation(WOGE_COMPONENT) + resolver.getSymbolsWithAnnotation(WOGE_REGION)
        val (ready, deferred) = annotated.toList().partition { it.validate() }

        ready.filterIsInstance<KSClassDeclaration>().forEach(reader::component)
        val regions = ready.filterIsInstance<KSFunctionDeclaration>().mapNotNull(reader::region)
        regions
            .groupBy { it.packageName to it.descriptorName }
            .values
            .forEach { sameName ->
                if (sameName.size == 1) write(sameName.single()) else sameName.forEach(reader::reportDuplicateName)
            }
        return deferred
    }

    @Suppress("SpreadOperator") // KSP only offers a vararg constructor; the array has at most two files.
    private fun write(region: RegionModel) {
        codeGenerator
            .createNewFile(
                Dependencies(aggregating = false, *region.sources.toTypedArray()),
                region.packageName,
                region.descriptorName,
            ).bufferedWriter()
            .use { it.write(region.source()) }
    }
}
