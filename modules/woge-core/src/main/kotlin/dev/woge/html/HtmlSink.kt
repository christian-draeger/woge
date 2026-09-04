package dev.woge.html

/**
 * Receives serialized HTML fragments in write order without implying network frame boundaries.
 *
 * Calls are synchronous and may contain arbitrarily small pieces. Implementations must propagate
 * downstream failures and cancellation signals instead of converting them to partial HTML.
 */
public fun interface HtmlSink {
    /** Writes one serialized fragment or propagates the downstream failure unchanged. */
    public fun write(value: String)
}
