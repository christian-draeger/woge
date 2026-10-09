package dev.woge.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated

/** Stops reading one declaration. A `null` [rule] means the problem was already reported. */
internal class Rejection(
    val rule: Rule?,
    val received: String,
    val symbol: KSAnnotated?,
) : RuntimeException(rule?.id, null, false, false) {
    companion object {
        val ALREADY_REPORTED = Rejection(null, "", null)
    }
}

/** Stops reading the current declaration and reports [rule] at [symbol]. */
internal fun reject(
    rule: Rule,
    received: String,
    symbol: KSAnnotated,
): Nothing = throw Rejection(rule, received, symbol)

/** Runs [read] and turns a [Rejection] into one located KSP error. */
internal inline fun <T : Any> KSPLogger.readOrReport(read: () -> T): T? =
    try {
        read()
    } catch (rejection: Rejection) {
        rejection.rule?.let { error(it.message(rejection.received), rejection.symbol) }
        null
    }

internal fun KSAnnotated.hasAnnotation(name: String): Boolean =
    annotations.any {
        it.annotationType
            .resolve()
            .declaration.qualifiedName
            ?.asString() == name
    }
