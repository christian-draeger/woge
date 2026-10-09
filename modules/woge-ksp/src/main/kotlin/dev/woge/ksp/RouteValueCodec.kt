package dev.woge.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility

/**
 * How the generated route reads and writes one value type.
 *
 * [parse] is an expression over the raw string `it` that yields the value or `null`.
 * [format] turns an expression of the value type into a string expression.
 */
internal class RouteValueCodec(
    val parse: String,
    val format: (String) -> String,
)

/** The codec for a non-null route value type, or `null` if routes do not support the type. */
internal fun KSType.routeValueCodec(): RouteValueCodec? {
    val declaration = declaration as? KSClassDeclaration ?: return null
    return scalarCodec(declaration) ?: declaration.valueClassCodec()
}

private fun scalarCodec(declaration: KSClassDeclaration): RouteValueCodec? {
    val name = declaration.qualifiedName?.asString() ?: return null
    val asText: (String) -> String = { "$it.toString()" }
    return when {
        declaration.classKind == ClassKind.ENUM_CLASS ->
            RouteValueCodec("$ROUTE_VALUES.enum<$name>(it)") { "$ROUTE_VALUES.format($it)" }
        name == "kotlin.String" -> RouteValueCodec("it") { it }
        name == "kotlin.Int" -> RouteValueCodec("it.toIntOrNull()", asText)
        name == "kotlin.Long" -> RouteValueCodec("it.toLongOrNull()", asText)
        name == "kotlin.Boolean" -> RouteValueCodec("it.toBooleanStrictOrNull()", asText)
        name == "java.util.UUID" -> RouteValueCodec("$ROUTE_VALUES.uuid(it)", asText)
        else -> null
    }
}

/** A value class such as `value class TaskId(val value: Long)` uses the codec of the wrapped type. */
private fun KSClassDeclaration.valueClassCodec(): RouteValueCodec? {
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
    val inner = wrapped?.let(::scalarCodec)
    val name = qualifiedName?.asString()
    if (property == null || inner == null || name == null) return null
    val parse = if (inner.parse == "it") "$name(it)" else "${inner.parse}?.let { value -> $name(value) }"
    return RouteValueCodec(parse) { inner.format("$it.${property.simpleName.asString()}") }
}

private const val ROUTE_VALUES = "dev.woge.host.RouteValues"
