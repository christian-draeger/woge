package dev.woge.development

/** Stable machine-readable category for one development diagnostic. */
@ExperimentalWogeDevelopmentApi
@JvmInline
public value class DevelopmentDiagnosticCode private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): DevelopmentDiagnosticCode {
            require(DIAGNOSTIC_CODE.matches(value)) {
                "Development diagnostic code must be uppercase ASCII segments separated by '-'"
            }
            return DevelopmentDiagnosticCode(value)
        }
    }
}

/** A short already-redacted message intended for a developer or coding tool. */
@ExperimentalWogeDevelopmentApi
@JvmInline
public value class DevelopmentDiagnosticSummary private constructor(
    public val value: String,
) {
    override fun toString(): String = "DevelopmentDiagnosticSummary(<redacted>)"

    public companion object {
        public fun of(value: String): DevelopmentDiagnosticSummary {
            require(value.isNotBlank() && value.length <= MAX_DIAGNOSTIC_SUMMARY_LENGTH) {
                "Development diagnostic summary must contain 1 to $MAX_DIAGNOSTIC_SUMMARY_LENGTH characters"
            }
            require(value.none(Char::isISOControl)) {
                "Development diagnostic summary must be one line without control characters"
            }
            return DevelopmentDiagnosticSummary(value)
        }
    }
}

@ExperimentalWogeDevelopmentApi
public enum class DevelopmentDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

/** Optional exact source position. Line and column values are one-based. */
@ExperimentalWogeDevelopmentApi
public data class DevelopmentSourceLocation(
    public val path: DevelopmentSourcePath,
    public val line: Int,
    public val column: Int,
) {
    init {
        require(line > 0) { "Diagnostic line must be positive" }
        require(column > 0) { "Diagnostic column must be positive" }
    }
}

/** Structured, display-safe diagnostic. Raw compiler output and exceptions are intentionally absent. */
@ExperimentalWogeDevelopmentApi
public data class DevelopmentDiagnostic(
    public val code: DevelopmentDiagnosticCode,
    public val severity: DevelopmentDiagnosticSeverity,
    public val summary: DevelopmentDiagnosticSummary,
    public val location: DevelopmentSourceLocation? = null,
) {
    override fun toString(): String =
        "DevelopmentDiagnostic(code=${code.value}, severity=$severity, location=$location, summary=<redacted>)"
}

private val DIAGNOSTIC_CODE: Regex = Regex("^[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*$")
private const val MAX_DIAGNOSTIC_SUMMARY_LENGTH: Int = 1_000
