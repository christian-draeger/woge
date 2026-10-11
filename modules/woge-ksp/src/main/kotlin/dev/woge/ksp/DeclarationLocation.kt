package dev.woge.ksp

import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSDeclaration

internal fun KSDeclaration.declarationLocation(): String {
    val source = location as? FileLocation
    val name = qualifiedName?.asString() ?: simpleName.asString()
    return if (source == null) name else "$name (${source.filePath}:${source.lineNumber})"
}
