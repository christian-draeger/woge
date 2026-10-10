package dev.woge.html

/** Exhausted UTF-8 rendering budget. The diagnostic never includes rendered content. */
public class HtmlByteLimitException(
    public val threshold: Long,
) : IllegalStateException("WOGE_RESOURCE_LIMIT_EXCEEDED: HTML_BYTES threshold=$threshold") {
    init {
        require(threshold > 0) { "HTML byte threshold must be positive" }
    }
}

/** One response or fragment's cumulative UTF-8 byte allowance, shared across all of its chunks. */
public class HtmlByteBudget(
    public val maxBytes: Long,
) {
    private var usedBytes: Long = 0
    private var failure: HtmlByteLimitException? = null

    init {
        require(maxBytes > 0) { "HTML byte budget must be positive" }
    }

    /** Check before retaining or writing a bounded encoded chunk. Exact-threshold output is allowed. */
    public fun consume(byteCount: Int) {
        failure?.let { throw it }
        require(byteCount >= 0) { "Encoded byte count must not be negative" }
        if (byteCount.toLong() > maxBytes - usedBytes) {
            val exceeded = HtmlByteLimitException(maxBytes)
            failure = exceeded
            throw exceeded
        }
        usedBytes += byteCount
    }
}
