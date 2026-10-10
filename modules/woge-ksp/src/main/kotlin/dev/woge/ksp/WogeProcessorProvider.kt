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
 * Generates descriptors for typed regions, routes and actions.
 *
 * A region file depends only on the source files that shape it, so KSP regenerates exactly the
 * regions whose sources changed. Route files are aggregating instead: KSP then hands every route
 * to each run, which the collision check needs. Actions follow the same rule for IDs and registries.
 */
internal class WogeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private val metadata = DescriptorMetadataWriter(codeGenerator, logger)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated =
            listOf(
                WOGE_COMPONENT,
                WOGE_REGION,
                WOGE_ROUTE,
                WOGE_ACTION,
            ).flatMap { resolver.getSymbolsWithAnnotation(it).toList() }
        val (ready, deferred) = annotated.partition { it.validate() }
        val classes = ready.filterIsInstance<KSClassDeclaration>()
        processRegions(
            classes,
            ready.filterIsInstance<KSFunctionDeclaration>().filter { it.hasAnnotation(WOGE_REGION) },
        )
        processRoutes(classes.filter { it.hasAnnotation(WOGE_ROUTE) })
        processActions(ready.filterIsInstance<KSFunctionDeclaration>().filter { it.hasAnnotation(WOGE_ACTION) })
        return deferred
    }

    override fun finish() {
        metadata.write()
    }

    private fun processActions(functions: List<KSFunctionDeclaration>) {
        val reader = ActionReader(logger)
        val actions = functions.mapNotNull(reader::action)
        val ids = actions.groupBy { it.id }
        val names = actions.groupBy { it.packageName to it.descriptorName }
        val valid =
            actions.filter { action ->
                val unique =
                    ids.getValue(action.id).size == 1 &&
                        names.getValue(action.packageName to action.descriptorName).size == 1
                if (!unique) reader.reportCollision(action)
                unique
            }
        valid.forEach { action ->
            write(action.packageName, action.descriptorName, action.sources, aggregating = true, action.source())
            metadata.add(
                action.packageName,
                action.sources,
                DescriptorEntry(
                    "ACTION",
                    action.id,
                    action.descriptorName.inPackage(action.packageName),
                    action.commandType,
                    path = "/woge-actions/${action.id}",
                ),
            )
        }
        valid.groupBy { it.packageName }.forEach { (packageName, packageActions) ->
            write(
                packageName,
                "WogeActions",
                packageActions.flatMap { it.sources }.distinct(),
                aggregating = true,
                packageActions.registrySource(packageName),
            )
        }
    }

    private fun processRegions(
        classes: List<KSClassDeclaration>,
        functions: List<KSFunctionDeclaration>,
    ) {
        val reader = RegionReader(logger)
        classes.filter { it.hasAnnotation(WOGE_COMPONENT) }.forEach { declaration ->
            reader.component(declaration)?.let { component ->
                metadata.add(
                    declaration.packageName.asString(),
                    component.declarations.mapNotNull { it.containingFile },
                    DescriptorEntry(
                        "COMPONENT",
                        component.identityName,
                        component.identityName,
                        keyType = component.key?.type,
                    ),
                )
            }
        }
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
                    metadata.add(
                        region.packageName,
                        region.sources,
                        DescriptorEntry(
                            "REGION",
                            region.identityName,
                            region.descriptorName.inPackage(region.packageName),
                            region.inputType,
                            component = region.component?.identityName,
                            keyType = region.component?.key?.type,
                        ),
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
                else -> {
                    write(
                        route.packageName,
                        route.descriptorName,
                        route.sources,
                        aggregating = true,
                        route.source(),
                    )
                    metadata.add(
                        route.packageName,
                        route.sources,
                        DescriptorEntry(
                            "PAGE",
                            route.path,
                            route.descriptorName.inPackage(route.packageName),
                            route.inputType,
                            path = route.path,
                        ),
                    )
                }
            }
        }
    }

    private fun String.inPackage(packageName: String): String =
        if (packageName.isEmpty()) this else "$packageName.$this"

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
