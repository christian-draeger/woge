package dev.woge.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
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

    fun reportCollision(action: ActionModel) {
        logger.error(Rule.ACTION_COLLISION.message("${action.functionName} with ID '${action.id}'"), action.symbol)
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
        if (function.parameters.size != 2 ||
            function.typeParameters.isNotEmpty() ||
            !Regex("[A-Za-z][A-Za-z0-9_]*").matches(name)
        ) {
            reject(Rule.ACTION_SHAPE, name, function)
        }
        if (function.parameters.any { it.isVararg || it.hasDefault }) reject(Rule.ACTION_SHAPE, name, function)
    }

    private fun validateBoundary(function: KSFunctionDeclaration) {
        val context = function.parameters[1].type.resolve()
        if (context.isMarkedNullable ||
            context.declaration.qualifiedName?.asString() != "dev.woge.host.RequestContext"
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
            val scalar =
                if (field.declaration.qualifiedName?.asString() == "kotlin.collections.List") {
                    field.arguments
                        .singleOrNull()
                        ?.type
                        ?.resolve()
                } else {
                    field
                }
            if (!parameter.isVal || scalar?.makeNotNullable()?.routeValueCodec() == null) {
                reject(Rule.ACTION_COMMAND, field.shortName(), parameter)
            }
        }
        return type.render(referenced) ?: reject(Rule.ACTION_COMMAND, type.shortName(), function)
    }
}

private const val MAX_ACTION_ID_LENGTH = 128
private val ACTION_ID = Regex("[a-z][a-z0-9-]*")
internal const val WOGE_ACTION = "dev.woge.host.WogeAction"
