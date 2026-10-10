package dev.woge.host

/** Stable budget names for safe diagnostics, never payloads or request values. */
public enum class ResourceLimit {
    DEFERRED_TASK_COUNT,
    PAGE_BYTES,
    PATCH_STREAM_BYTES,
    PATCH_COUNT,
}

/** Identifies a rejected budget and its configured threshold, without recording submitted content. */
public data class ResourceLimitExceeded(
    public val limit: ResourceLimit,
    public val threshold: Long,
) {
    init {
        require(threshold > 0) { "Resource limit threshold must be positive" }
    }
}
