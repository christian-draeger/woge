package dev.woge.html

/** Receives serialized HTML fragments in write order without implying network frame boundaries. */
public fun interface HtmlSink {
    /** Writes one serialized fragment or propagates the downstream failure unchanged. */
    public fun write(value: String)
}
