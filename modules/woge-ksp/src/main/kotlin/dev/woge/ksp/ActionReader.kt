package dev.woge.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.Visibility

internal data class ActionModel(
    val packageName: String,
    val functionName: String,
    val descriptorName: String,
    val commandType: String,
    val id: String,
    val visibility: String,
    val sources: List<KSFile>,
    val symbol: KSFunctionDeclaration,
)

internal class ActionReader(
    private val logger: KSPLogger,
) {
    fun action(function: KSFunctionDeclaration): ActionModel? = logger.readOrReport { read(function) }

    fun reportCollision(
        action: ActionModel,
        others: List<ActionModel>,
    ) {
        val declarations =
            (listOf(action) + others)
                .sortedBy { it.symbol.declarationLocation() }
                .joinToString { "${it.symbol.declarationLocation()} with ID '${it.id}'" }
        logger.error(Rule.ACTION_COLLISION.message(declarations), action.symbol)
    }

    private fun read(function: KSFunctionDeclaration): ActionModel {
        val name = function.simpleName.asString()
        validateShape(function)
        val id =
            function.annotations
                .first {
                    it.annotationType
                        .resolve()
                        .declaration.qualifiedName
                        ?.asString() ==
                        WOGE_ACTION
                }.arguments
                .firstOrNull { it.name?.asString() == "id" }
                ?.value as? String ?: ""
        if (id.length !in 1..MAX_ACTION_ID_LENGTH || !ACTION_ID.matches(id)) reject(Rule.ACTION_ID, id, function)
        val referenced = mutableListOf<KSDeclaration>(function)
        val command = function.parameters[0].type.resolve()
        val commandType = readCommand(command, referenced, function)
        validateBoundary(function)
        val visibility = referenced.effectiveVisibility()
        if (visibility == Visibility.PRIVATE) reject(Rule.ACTION_VISIBILITY, name, function)
        return ActionModel(
            function.packageName.asString(),
            name,
            name.replaceFirstChar(Char::uppercase) + "Action",
            commandType,
            id,
            if (visibility == Visibility.INTERNAL) "internal" else "public",
            referenced.mapNotNull { it.containingFile }.distinct().sortedBy { it.filePath },
            function,
        )
    }

    private fun validateShape(function: KSFunctionDeclaration) {
        val name = function.simpleName.asString()
        if (function.packageName.asString().isEmpty()) reject(Rule.ACTION_SHAPE, name, function)
        if (function.parentDeclaration != null ||
            function.extensionReceiver != null ||
            Modifier.SUSPEND !in function.modifiers
        ) {
            reject(Rule.ACTION_SHAPE, name, function)
        }
        if (function.parameters.size !in 1..2 ||
            function.typeParameters.isNotEmpty() ||
            !Regex("[A-Za-z][A-Za-z0-9_]*").matches(name)
        ) {
            reject(Rule.ACTION_SHAPE, name, function)
        }
        if (function.parameters.any { it.isVararg || it.hasDefault }) reject(Rule.ACTION_SHAPE, name, function)
    }

    private fun validateBoundary(function: KSFunctionDeclaration) {
        val context =
            function.parameters
                .getOrNull(1)
                ?.type
                ?.resolve()
        if (context != null &&
            (
                context.isMarkedNullable ||
                    context.declaration.qualifiedName?.asString() != "dev.woge.host.RequestContext"
            )
        ) {
            reject(Rule.ACTION_CONTEXT, context.shortName(), function)
        }
        val result = function.returnType?.resolve()
        if (result == null ||
            result.isMarkedNullable ||
            result.declaration.qualifiedName?.asString() != "dev.woge.host.PageResult"
        ) {
            reject(Rule.ACTION_RETURN, result?.shortName().orEmpty(), function)
        }
    }

    private fun readCommand(
        type: KSType,
        referenced: MutableList<KSDeclaration>,
        function: KSFunctionDeclaration,
    ): String {
        if (type.declaration.qualifiedName?.asString() == MULTIPART_SUBMISSION) {
            return readMultipartCommand(type, referenced, function)
        }
        val declaration =
            type.declaration as? KSClassDeclaration ?: reject(Rule.ACTION_COMMAND, type.shortName(), function)
        if (type.isMarkedNullable ||
            type.arguments.isNotEmpty() ||
            Modifier.DATA !in declaration.modifiers
        ) {
            reject(Rule.ACTION_COMMAND, type.shortName(), function)
        }
        declaration.primaryConstructor?.parameters.orEmpty().forEach { parameter ->
            val field = parameter.type.resolve()
            field.render(referenced)
            val supported = field.actionStructure(referenced)
            if (!parameter.isVal || !supported) {
                reject(Rule.ACTION_COMMAND, field.shortName(), parameter)
            }
        }
        return type.render(referenced) ?: reject(Rule.ACTION_COMMAND, type.shortName(), function)
    }

    private fun readMultipartCommand(
        type: KSType,
        referenced: MutableList<KSDeclaration>,
        function: KSFunctionDeclaration,
    ): String {
        val command =
            type.arguments
                .singleOrNull()
                ?.type
                ?.resolve() ?: reject(Rule.ACTION_COMMAND, type.shortName(), function)
        if (type.isMarkedNullable ||
            type.arguments.singleOrNull()?.variance != Variance.INVARIANT ||
            command.declaration.qualifiedName?.asString() == MULTIPART_SUBMISSION
        ) {
            reject(Rule.ACTION_COMMAND, type.shortName(), function)
        }
        readCommand(command, referenced, function)
        return type.render(referenced) ?: reject(Rule.ACTION_COMMAND, type.shortName(), function)
    }
}

private fun KSType.actionScalar(): Boolean {
    if (routeValueCodec() != null || declaration.qualifiedName?.asString() in FORM_PRIMITIVES) return true
    val valueClass = declaration as? KSClassDeclaration
    val parameter =
        valueClass
            ?.primaryConstructor
            ?.parameters
            ?.singleOrNull()
            ?.takeIf { Modifier.VALUE in valueClass.modifiers || Modifier.INLINE in valueClass.modifiers }
    val property = valueClass?.getDeclaredProperties()?.firstOrNull { it.simpleName == parameter?.name }
    val wrapped = parameter?.type?.resolve()
    val accessible = property?.getVisibility()?.let { it != Visibility.PRIVATE } == true
    return accessible && wrapped?.takeUnless { it.isMarkedNullable }?.actionScalar() == true
}

private fun KSType.actionStructure(
    referenced: MutableList<KSDeclaration>,
    depth: Int = 0,
): Boolean {
    if (depth > MAX_ACTION_FORM_DEPTH) return false
    render(referenced)
    val nested = declaration as? KSClassDeclaration
    return when {
        makeNotNullable().actionScalar() -> true
        declaration.qualifiedName?.asString() == "kotlin.collections.List" ->
            arguments
                .singleOrNull()
                ?.type
                ?.resolve()
                ?.actionStructure(referenced, depth + 1) == true
        nested != null ->
            Modifier.DATA in nested.modifiers &&
                arguments.isEmpty() &&
                nested.primaryConstructor?.parameters?.all {
                    it.isVal && it.type.resolve().actionStructure(referenced, depth + 1)
                } == true
        else -> false
    }
}

private const val MAX_ACTION_FORM_DEPTH = 7
private const val MAX_ACTION_ID_LENGTH = 128
private val ACTION_ID = Regex("[a-z][a-z0-9-]*")
internal const val WOGE_ACTION = "dev.woge.host.WogeAction"
private val FORM_PRIMITIVES = setOf("kotlin.Byte", "kotlin.Short", "kotlin.Float", "kotlin.Double", "kotlin.Char")
private const val MULTIPART_SUBMISSION = "dev.woge.host.MultipartSubmission"
