package dev.woge.development

/**
 * Marks Woge's internal development-tooling contract.
 *
 * The contract is shared by Woge-owned tooling adapters, but it is not a stable application API and
 * is deliberately not published as a production dependency.
 */
@RequiresOptIn(
    message = "Woge development tooling is experimental and must not be used by production application code.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class ExperimentalWogeDevelopmentApi
