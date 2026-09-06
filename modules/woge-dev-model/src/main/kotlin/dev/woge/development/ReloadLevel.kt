package dev.woge.development

/**
 * Correctness-ordered ways to expose a successful change.
 *
 * Each following value is a more expensive but more generally correct fallback. Tooling may select
 * a cheaper value only after proving it safe for the observed change.
 */
@ExperimentalWogeDevelopmentApi
public enum class ReloadLevel {
    HOT_ASSET,
    HOT_FRONTEND_MODULE,
    DOCUMENT_REFRESH,
    SERVER_RESTART,
    COLD_RESTART,
    ;

    public fun fallback(): ReloadLevel? = entries.getOrNull(ordinal + 1)

    public fun satisfies(required: ReloadLevel): Boolean = ordinal >= required.ordinal

    public companion object {
        public fun safest(
            first: ReloadLevel,
            second: ReloadLevel,
        ): ReloadLevel = if (first.satisfies(second)) first else second
    }
}
