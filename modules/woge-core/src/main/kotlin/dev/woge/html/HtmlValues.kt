package dev.woge.html

/** Marks the nested receivers used while writing HTML. */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
public annotation class WogeHtmlDsl

/**
 * Marks APIs that bypass normal HTML or URL safety boundaries.
 *
 * Opting in records that the caller, rather than Woge, audited the value for its exact browser
 * context. It does not sanitize or make untrusted input safe.
 */
@RequiresOptIn(
    message = "This value bypasses normal Woge HTML safety. Audit its source and browser context.",
    level = RequiresOptIn.Level.ERROR,
)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class UnsafeWogeHtmlApi

/** HTML markup that will be emitted without text escaping. */
@JvmInline
public value class UnsafeHtml internal constructor(
    internal val value: String,
)

/**
 * Marks [value] as deliberately unescaped HTML after an application-specific audit or sanitization.
 */
@UnsafeWogeHtmlApi
public fun unsafeHtml(value: String): UnsafeHtml = UnsafeHtml(value)
