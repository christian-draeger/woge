package dev.woge.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Visibility

/** What the processor needs to write one route descriptor. */
internal data class RouteModel(
    val packageName: String,
    val inputType: String,
    val descriptorName: String,
    val visibility: String,
    val path: String,
    val isObject: Boolean,
    val parameters: List<RouteParameterModel>,
    val sources: List<KSFile>,
    val symbol: KSClassDeclaration,
) {
    /** The path with parameter names removed. Two routes with the same pattern match the same URLs. */
    val pattern: String = PATH_PARAMETER.replace(path, "{}")
}

/** One constructor property. Path values are required; query values are nullable. */
internal data class RouteParameterModel(
    val name: String,
    val inPath: Boolean,
    val codec: RouteValueCodec,
)

/** Reads `@WogeRoute` classes and reports every rule violation at its source location. */
internal class RouteReader(
    private val logger: KSPLogger,
) {
    fun route(declaration: KSClassDeclaration): RouteModel? = logger.readOrReport { readRoute(declaration) }

    fun reportDuplicateName(route: RouteModel) {
        logger.error(Rule.ROUTE_NAME.message("${route.received()} generates ${route.descriptorName}"), route.symbol)
    }

    fun reportCollision(
        route: RouteModel,
        others: List<RouteModel>,
    ) {
        val names = others.joinToString { it.received() }
        logger.error(Rule.ROUTE_COLLISION.message("${route.received()} and $names"), route.symbol)
    }

    private fun readRoute(declaration: KSClassDeclaration): RouteModel {
        val path = declaration.routePath()
        val received = "@WogeRoute(\"$path\") ${declaration.simpleName.asString()}"
        if (!ROUTE_PATH.matches(path) || path.pathNames().let { it.size != it.toSet().size }) {
            reject(Rule.ROUTE_PATH, received, declaration)
        }
        val isObject = declaration.classKind == ClassKind.OBJECT
        val constructor = declaration.primaryConstructor
        if (!isObject && (declaration.classKind != ClassKind.CLASS || constructor == null)) {
            reject(Rule.ROUTE_TARGET, received, declaration)
        }
        val referenced = mutableListOf<KSDeclaration>(declaration)
        constructor?.takeUnless { isObject }?.let { referenced += it }
        val parameters =
            if (isObject) {
                emptyList()
            } else {
                constructor?.parameters.orEmpty().map {
                    readParameter(declaration, it, path, referenced)
                }
            }
        val missing = path.pathNames() - parameters.filter { it.inPath }.map { it.name }.toSet()
        if (missing.isNotEmpty()) {
            reject(
                Rule.ROUTE_PATH_PARAMETER,
                "$received has no property ${missing.first()}",
                declaration,
            )
        }
        val visibility = referenced.effectiveVisibility()
        if (visibility == Visibility.PRIVATE) reject(Rule.ROUTE_VISIBILITY, received, declaration)
        val inputType = declaration.qualifiedName?.asString() ?: reject(Rule.ROUTE_TARGET, received, declaration)
        return RouteModel(
            packageName = declaration.packageName.asString(),
            inputType = inputType,
            descriptorName = declaration.simpleName.asString().removeSuffix("Input") + "Route",
            visibility = if (visibility == Visibility.INTERNAL) "internal" else "public",
            path = path,
            isObject = isObject,
            parameters = parameters,
            sources = referenced.mapNotNull { it.containingFile }.distinct().sortedBy { it.filePath },
            symbol = declaration,
        )
    }

    private fun readParameter(
        declaration: KSClassDeclaration,
        parameter: KSValueParameter,
        path: String,
        referenced: MutableList<KSDeclaration>,
    ): RouteParameterModel {
        val name = parameter.name?.asString().orEmpty()
        val type = parameter.type.resolve()
        type.render(referenced)
        val received = "$name: ${type.shortName()}"
        val property = declaration.getDeclaredProperties().firstOrNull { it.simpleName.asString() == name }
        if (!parameter.isVal ||
            property == null
        ) {
            reject(Rule.ROUTE_TARGET, "constructor parameter $received is not a val", parameter)
        }
        referenced += property
        val inPath = name in path.pathNames()
        when {
            inPath && type.isMarkedNullable -> reject(Rule.ROUTE_PATH_PARAMETER, received, parameter)
            !inPath && !type.isMarkedNullable -> reject(Rule.ROUTE_QUERY_PARAMETER, received, parameter)
        }
        val codec = type.makeNotNullable().routeValueCodec() ?: reject(Rule.ROUTE_VALUE_TYPE, received, parameter)
        return RouteParameterModel(name, inPath, codec)
    }
}

private fun RouteModel.received(): String = "@WogeRoute(\"$path\") ${symbol.simpleName.asString()}"

private fun KSClassDeclaration.routePath(): String =
    annotations
        .firstOrNull {
            it.annotationType
                .resolve()
                .declaration.qualifiedName
                ?.asString() == WOGE_ROUTE
        }?.arguments
        ?.firstOrNull { it.name?.asString() == "path" }
        ?.value as? String ?: ""

private fun String.pathNames(): List<String> = PATH_PARAMETER.findAll(this).map { it.groupValues[1] }.toList()

/** The same syntax that `dev.woge.host.PageRoute` checks at runtime. */
private val ROUTE_PATH = Regex("/|(/([A-Za-z0-9._~-]+|\\{[A-Za-z][A-Za-z0-9]*}))+")
private val PATH_PARAMETER = Regex("\\{([A-Za-z][A-Za-z0-9]*)}")
internal const val WOGE_ROUTE = "dev.woge.host.WogeRoute"
