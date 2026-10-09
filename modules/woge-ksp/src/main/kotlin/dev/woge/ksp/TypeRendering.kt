package dev.woge.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.Visibility

/*
 * Turns resolved KSP types into the source text the generated descriptor needs.
 */

internal fun KSType.keyArgument(): String? {
    val declaration = (declaration as? KSClassDeclaration)?.takeUnless { isMarkedNullable } ?: return null
    return scalarKeyArgument(declaration, "key") ?: declaration.valueClassKeyArgument()
}

/** A value class key such as `value class ProjectId(val value: String)` becomes `key.value`. */
private fun KSClassDeclaration.valueClassKeyArgument(): String? {
    val isValueClass = Modifier.VALUE in modifiers || Modifier.INLINE in modifiers
    val parameter = primaryConstructor?.parameters?.singleOrNull()?.takeIf { isValueClass }
    val property =
        getDeclaredProperties()
            .firstOrNull { it.simpleName == parameter?.name }
            ?.takeUnless { it.getVisibility() == Visibility.PRIVATE }
    val wrapped =
        parameter
            ?.type
            ?.resolve()
            ?.takeUnless { it.isMarkedNullable }
            ?.declaration as? KSClassDeclaration
    if (property == null || wrapped == null) return null
    return scalarKeyArgument(wrapped, "key.${property.simpleName.asString()}")
}

private fun scalarKeyArgument(
    declaration: KSClassDeclaration,
    value: String,
): String? =
    when (declaration.qualifiedName?.asString()) {
        "kotlin.String", "kotlin.Long", "java.util.UUID" -> value
        "kotlin.Int" -> "$value.toLong()"
        else -> null
    }

/** Renders a fully qualified Kotlin type and collects the declarations it uses. */
internal fun KSType.render(declarations: MutableList<KSDeclaration>): String? {
    val name = declaration.qualifiedName?.asString()?.takeUnless { isError } ?: return null
    declarations += declaration
    val rendered = arguments.map { it.render(declarations) }
    val typeArguments = if (rendered.isEmpty()) "" else rendered.joinToString(", ", "<", ">")
    return (name + typeArguments + if (isMarkedNullable) "?" else "").takeUnless { null in rendered }
}

/** Renders a type with simple names, as people write it in diagnostics: `List<String>?`. */
internal fun KSType.shortName(): String {
    val typeArguments =
        arguments
            .map { it.type?.resolve()?.shortName() ?: "*" }
            .takeUnless { it.isEmpty() }
            ?.joinToString(", ", "<", ">")
            .orEmpty()
    return declaration.simpleName.asString() + typeArguments + if (isMarkedNullable) "?" else ""
}

private fun KSTypeArgument.render(declarations: MutableList<KSDeclaration>): String? {
    val rendered = type?.resolve()?.render(declarations)
    return when {
        variance == Variance.STAR -> "*"
        rendered == null -> null
        variance == Variance.COVARIANT -> "out $rendered"
        variance == Variance.CONTRAVARIANT -> "in $rendered"
        else -> rendered
    }
}

internal fun List<KSDeclaration>.effectiveVisibility(): Visibility {
    val visibilities =
        flatMap { declaration ->
            generateSequence(declaration) { it.parentDeclaration }.map { it.getVisibility() }
        }
    return when {
        visibilities.any { it in HIDDEN } -> Visibility.PRIVATE
        visibilities.any { it == Visibility.INTERNAL } -> Visibility.INTERNAL
        else -> Visibility.PUBLIC
    }
}

private val HIDDEN = setOf(Visibility.PRIVATE, Visibility.LOCAL, Visibility.PROTECTED)
