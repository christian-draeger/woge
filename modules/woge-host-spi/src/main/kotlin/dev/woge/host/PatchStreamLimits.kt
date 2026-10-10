package dev.woge.host

/** One patch response's total wire bytes and semantic patch count. Fixed frame ceilings still apply. */
public data class PatchStreamLimits(
    public val maxBytes: Long = DEFAULT_MAX_BYTES,
    public val maxPatches: Int = DEFAULT_MAX_PATCHES,
) {
    init {
        require(maxBytes > 0) { "Patch stream byte limit must be positive" }
        require(maxPatches > 0) { "Patch stream count limit must be positive" }
    }

    public companion object {
        public const val DEFAULT_MAX_BYTES: Long = 16L * 1024 * 1024
        public const val DEFAULT_MAX_PATCHES: Int = 128
    }
}

/** Safe resource-exhaustion diagnostic; neither inputs nor rendered content appear in this error. */
public class ResourceLimitException(
    public val exceededLimit: ResourceLimitExceeded,
) : IllegalStateException(
        "WOGE_RESOURCE_LIMIT_EXCEEDED: ${exceededLimit.limit} threshold=${exceededLimit.threshold}",
    )

/** Incremental accounting shared by prepared action responses and streaming transport encoders. */
public class PatchStreamBudget(
    private val limits: PatchStreamLimits = PatchStreamLimits(),
) {
    private var bytes: Long = 0
    private var patches: Int = 0
    private var failure: ResourceLimitException? = null

    /** Admit a patch before rendering or encoding it. */
    public fun admitPatch() {
        failure?.let { throw it }
        if (patches == limits.maxPatches) reject(ResourceLimit.PATCH_COUNT, limits.maxPatches.toLong())
        patches += 1
    }

    /** Account for framing and payload bytes before retaining or writing them. */
    public fun consumeBytes(byteCount: Int) {
        failure?.let { throw it }
        require(byteCount >= 0) { "Encoded byte count must not be negative" }
        if (byteCount.toLong() > limits.maxBytes - bytes) reject(ResourceLimit.PATCH_STREAM_BYTES, limits.maxBytes)
        bytes += byteCount
    }

    private fun reject(
        limit: ResourceLimit,
        threshold: Long,
    ): Nothing {
        val exceeded = ResourceLimitException(ResourceLimitExceeded(limit, threshold))
        failure = exceeded
        throw exceeded
    }
}
