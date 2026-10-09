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
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate

/** Entry point that KSP finds through `META-INF/services`. */
public class WogeProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        WogeProcessor(environment.codeGenerator, environment.logger)
}

/**
 * Generates one descriptor file per `@WogeRegion` function and per `@WogeRoute` class.
 *
 * A region file depends only on the source files that shape it, so KSP regenerates exactly the
 * regions whose sources changed. Route files are aggregating instead: KSP then hands every route
 * to each run, which the collision check needs. Routes are few and cheap to regenerate.
 */
internal class WogeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated =
            listOf(WOGE_COMPONENT, WOGE_REGION, WOGE_ROUTE).flatMap { resolver.getSymbolsWithAnnotation(it).toList() }
        val (ready, deferred) = annotated.partition { it.validate() }
        val classes = ready.filterIsInstance<KSClassDeclaration>()
        processRegions(classes, ready.filterIsInstance<KSFunctionDeclaration>())
        processRoutes(classes.filter { it.hasAnnotation(WOGE_ROUTE) })
        return deferred
    }

    private fun processRegions(
        classes: List<KSClassDeclaration>,
        functions: List<KSFunctionDeclaration>,
    ) {
        val reader = RegionReader(logger)
        classes.filter { it.hasAnnotation(WOGE_COMPONENT) }.forEach(reader::component)
        functions
            .mapNotNull(reader::region)
            .groupBy { it.packageName to it.descriptorName }
            .values
            .forEach { sameName ->
                if (sameName.size == 1) {
                    val region = sameName.single()
                    write(
                        region.packageName,
                        region.descriptorName,
                        region.sources,
                        aggregating = false,
                        region.source(),
                    )
                } else {
                    sameName.forEach(reader::reportDuplicateName)
                }
            }
    }

    private fun processRoutes(classes: List<KSClassDeclaration>) {
        val reader = RouteReader(logger)
        val routes = classes.mapNotNull(reader::route)
        val names = routes.groupBy { it.packageName to it.descriptorName }
        val patterns = routes.groupBy { it.pattern }
        routes.forEach { route ->
            val sameName = names.getValue(route.packageName to route.descriptorName)
            val samePattern = patterns.getValue(route.pattern)
            when {
                sameName.size > 1 -> reader.reportDuplicateName(route)
                samePattern.size > 1 -> reader.reportCollision(route, samePattern - route)
                else ->
                    write(
                        route.packageName,
                        route.descriptorName,
                        route.sources,
                        aggregating = true,
                        route.source(),
                    )
            }
        }
    }

    @Suppress("SpreadOperator") // KSP only offers a vararg constructor; the array holds a few files.
    private fun write(
        packageName: String,
        fileName: String,
        sources: List<KSFile>,
        aggregating: Boolean,
        source: String,
    ) {
        codeGenerator
            .createNewFile(Dependencies(aggregating, *sources.toTypedArray()), packageName, fileName)
            .bufferedWriter()
            .use { it.write(source) }
    }
}
