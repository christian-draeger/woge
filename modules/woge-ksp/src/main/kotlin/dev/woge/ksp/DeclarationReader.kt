package dev.woge.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility

/** What the processor needs to write one region descriptor. */
internal data class RegionModel(
    val packageName: String,
    val functionName: String,
    val descriptorName: String,
    val visibility: String,
    val inputType: String,
    val identityName: String,
    val component: ComponentModel?,
    val sources: List<KSFile>,
    val symbol: KSFunctionDeclaration,
)

internal data class ComponentModel(
    val identityName: String,
    val key: KeyModel?,
    val declaration: KSClassDeclaration,
)

/** [argument] converts a value named `key` into a type accepted by `IdentityKey.of`. */
internal data class KeyModel(
    val type: String,
    val argument: String,
)

/**
 * Reads annotated declarations and reports every rule violation at its source location.
 *
 * Each check rejects with [reject]; [readOrReport] turns that rejection into one KSP error.
 */
internal class DeclarationReader(
    private val logger: KSPLogger,
) {
    private val components: MutableMap<String, ComponentModel?> = mutableMapOf()

    fun component(declaration: KSClassDeclaration): ComponentModel? {
        val name = declaration.qualifiedName?.asString() ?: return null
        if (name !in components) components[name] = readOrReport { readComponent(declaration, name) }
        return components[name]
    }

    fun region(function: KSFunctionDeclaration): RegionModel? = readOrReport { readRegion(function) }

    fun reportDuplicateName(region: RegionModel) {
        val received = "${region.symbol.signature()} generates ${region.descriptorName}"
        logger.error(Rule.DESCRIPTOR_NAME.message(received), region.symbol)
    }

    private fun readRegion(function: KSFunctionDeclaration): RegionModel {
        function.requireRegionShape()
        val referenced = mutableListOf<KSDeclaration>(function)
        val inputType =
            function.parameters
                .single()
                .type
                .resolve()
                .render(referenced) ?: reject(Rule.REGION_SHAPE, function.signature(), function)
        val component = function.readOwner()
        component?.let { referenced += it.declaration }
        val visibility = referenced.effectiveVisibility()
        if (visibility == Visibility.PRIVATE) reject(Rule.REGION_VISIBILITY, function.signature(), function)
        val functionName = function.simpleName.asString()
        return RegionModel(
            packageName = function.packageName.asString(),
            functionName = functionName,
            descriptorName = functionName.replaceFirstChar(Char::uppercaseChar).removeSuffix("Region") + "Region",
            visibility = if (visibility == Visibility.INTERNAL) "internal" else "public",
            inputType = inputType,
            identityName = requireIdentityName(function.qualifiedName?.asString(), function),
            component = component,
            sources = listOfNotNull(function.containingFile, component?.declaration?.containingFile).distinct(),
            symbol = function,
        )
    }

    private fun KSFunctionDeclaration.readOwner(): ComponentModel? {
        val owner = componentDeclaration() ?: return null
        if (!owner.hasAnnotation(WOGE_COMPONENT)) {
            reject(Rule.REGION_COMPONENT, "@WogeRegion(component = ${owner.simpleName.asString()}::class)", this)
        }
        return component(owner) ?: throw Rejection.ALREADY_REPORTED
    }

    private fun readComponent(
        declaration: KSClassDeclaration,
        name: String,
    ): ComponentModel {
        requireIdentityName(name, declaration)
        val keys =
            declaration.primaryConstructor
                ?.parameters
                .orEmpty()
                .filter { it.hasAnnotation(WOGE_KEY) }
        if (keys.size > 1) {
            val names = keys.joinToString { it.name?.asString().orEmpty() }
            reject(Rule.COMPONENT_KEYS, "${keys.size} @WogeKey parameters: $names", declaration)
        }
        return ComponentModel(name, keys.singleOrNull()?.let(::readKey), declaration)
    }

    private fun readKey(parameter: KSValueParameter): KeyModel {
        val type = parameter.type.resolve()
        val rendered = type.render(mutableListOf())
        val argument = type.keyArgument()
        if (rendered == null || argument == null) {
            reject(Rule.COMPONENT_KEY_TYPE, "@WogeKey ${parameter.name?.asString()}: ${rendered ?: type}", parameter)
        }
        return KeyModel(rendered, argument)
    }

    private fun KSFunctionDeclaration.componentDeclaration(): KSClassDeclaration? {
        val argument =
            annotations
                .firstOrNull {
                    it.annotationType
                        .resolve()
                        .declaration.qualifiedName
                        ?.asString() == WOGE_REGION
                }?.arguments
                ?.firstOrNull { it.name?.asString() == "component" }
                ?.value as? KSType
        val declaration = argument?.declaration as? KSClassDeclaration
        return declaration?.takeUnless { it.qualifiedName?.asString() == "kotlin.Unit" }
    }

    private inline fun <T : Any> readOrReport(read: () -> T): T? =
        try {
            read()
        } catch (rejection: Rejection) {
            rejection.rule?.let { logger.error(it.message(rejection.received), rejection.symbol) }
            null
        }
}

/** Stops reading one declaration. A `null` [rule] means the problem was already reported. */
private class Rejection(
    val rule: Rule?,
    val received: String,
    val symbol: KSAnnotated?,
) : RuntimeException(rule?.id, null, false, false) {
    companion object {
        val ALREADY_REPORTED = Rejection(null, "", null)
    }
}

private fun reject(
    rule: Rule,
    received: String,
    symbol: KSAnnotated,
): Nothing = throw Rejection(rule, received, symbol)

private fun KSFunctionDeclaration.requireRegionShape() {
    val receiver =
        extensionReceiver
            ?.resolve()
            ?.declaration
            ?.qualifiedName
            ?.asString()
    if (parentDeclaration != null || receiver != HTML_WRITER) reject(Rule.REGION_RECEIVER, signature(), this)
    val input = parameters.singleOrNull()
    val hasOneInput = input != null && !input.isVararg
    val isPlainFunction = typeParameters.isEmpty() && Modifier.SUSPEND !in modifiers
    if (!hasOneInput || !isPlainFunction) reject(Rule.REGION_SHAPE, signature(), this)
}

private fun requireIdentityName(
    name: String?,
    symbol: KSAnnotated,
): String {
    val valid = name != null && name.length <= MAX_IDENTITY_NAME && IDENTITY_NAME.matches(name)
    if (!valid || name == null) reject(Rule.IDENTITY_NAME, name ?: "<anonymous>", symbol)
    return name
}

private fun KSFunctionDeclaration.signature(): String {
    val receiver =
        extensionReceiver
            ?.resolve()
            ?.declaration
            ?.simpleName
            ?.asString()
            ?.let { "$it." }
            .orEmpty()
    val suspend = if (Modifier.SUSPEND in modifiers) "suspend " else ""
    val typeParameters =
        if (typeParameters.isEmpty()) "" else typeParameters.joinToString(", ", "<", "> ") { it.name.asString() }
    val parameters =
        parameters.joinToString(", ") { parameter ->
            val vararg = if (parameter.isVararg) "vararg " else ""
            "$vararg${parameter.name?.asString()}: ${parameter.type.resolve().declaration.simpleName.asString()}"
        }
    val owner = parentDeclaration?.let { " inside ${it.simpleName.asString()}" }.orEmpty()
    return "${suspend}fun $typeParameters$receiver${simpleName.asString()}($parameters)$owner"
}

private fun KSAnnotated.hasAnnotation(name: String): Boolean =
    annotations.any {
        it.annotationType
            .resolve()
            .declaration.qualifiedName
            ?.asString() == name
    }

private val IDENTITY_NAME = Regex("[A-Za-z][A-Za-z0-9_.]*")
private const val MAX_IDENTITY_NAME = 128
internal const val HTML_WRITER = "dev.woge.html.HtmlWriter"
internal const val WOGE_REGION = "dev.woge.host.WogeRegion"
internal const val WOGE_COMPONENT = "dev.woge.host.WogeComponent"
private const val WOGE_KEY = "dev.woge.host.WogeKey"
