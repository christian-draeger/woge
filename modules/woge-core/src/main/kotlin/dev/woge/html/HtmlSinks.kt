package dev.woge.html

/** Default upper bound for characters retained by [StreamingHtmlSink] between downstream writes. */
public const val DEFAULT_HTML_CHUNK_CHARS: Int = 8 * 1024

/** A deterministic in-memory sink intended for tests, small fragments and non-streaming responses. */
public class BufferedHtmlSink(
    initialCapacity: Int = DEFAULT_INITIAL_HTML_CAPACITY,
) : HtmlSink {
    private val buffer: StringBuilder

    init {
        require(initialCapacity >= 0) { "HTML buffer initial capacity must not be negative" }
        buffer = StringBuilder(initialCapacity)
    }

    /** Number of UTF-16 code units currently held by this sink. */
    public val length: Int
        get() = buffer.length

    override fun write(value: String) {
        buffer.append(value)
    }

    /** Returns a stable snapshot of the rendered HTML. */
    public fun content(): String = buffer.toString()
}

/**
 * Coalesces small writer calls into bounded chunks and forwards them synchronously to [downstream].
 *
 * Call [flush] at a meaningful render or transport boundary. This sink never closes or flushes the
 * host response itself. A downstream failure makes the sink terminal and is rethrown unchanged.
 */
public class StreamingHtmlSink(
    private val downstream: HtmlSink,
    public val maxChunkChars: Int = DEFAULT_HTML_CHUNK_CHARS,
) : HtmlSink {
    private val buffer: StringBuilder
    private var failure: Throwable? = null

    init {
        require(maxChunkChars > 0) { "HTML chunk size must be greater than zero" }
        buffer = StringBuilder(maxChunkChars)
    }

    override fun write(value: String) {
        rethrowFailure()
        if (value.isEmpty()) return

        var offset = 0
        while (offset < value.length) {
            if (buffer.length == maxChunkChars) emitBufferedChunk()

            val capacity = maxChunkChars - buffer.length
            var end = minOf(offset + capacity, value.length)
            if (splitsSurrogatePair(value, offset, end)) {
                end = if (buffer.isEmpty() && capacity == 1) end + 1 else end - 1
            }
            if (end == offset) {
                emitBufferedChunk()
                continue
            }

            buffer.append(value, offset, end)
            offset = end
            if (buffer.length >= maxChunkChars) emitBufferedChunk()
        }
    }

    /** Emits the currently buffered characters, if any, without touching host transport lifecycle. */
    public fun flush() {
        rethrowFailure()
        emitBufferedChunk()
    }

    private fun emitBufferedChunk() {
        if (buffer.isEmpty()) return

        val chunk = buffer.toString()
        buffer.setLength(0)
        val outcome = runCatching { downstream.write(chunk) }
        failure = outcome.exceptionOrNull()
        outcome.getOrThrow()
    }

    private fun rethrowFailure() {
        failure?.let { throw it }
    }
}

/**
 * Renders through a bounded [StreamingHtmlSink] and flushes its final chunk on normal completion.
 *
 * If rendering or downstream writing fails, the original exception propagates and no trailing chunk
 * is emitted after the failure.
 */
public fun streamHtml(
    downstream: HtmlSink,
    maxChunkChars: Int = DEFAULT_HTML_CHUNK_CHARS,
    block: HtmlWriter.() -> Unit,
) {
    val sink = StreamingHtmlSink(downstream, maxChunkChars)
    writeHtml(sink, block)
    sink.flush()
}

private fun splitsSurrogatePair(
    value: String,
    offset: Int,
    end: Int,
): Boolean =
    end > offset &&
        end < value.length &&
        value[end - 1].isHighSurrogate() &&
        value[end].isLowSurrogate()

private const val DEFAULT_INITIAL_HTML_CAPACITY: Int = 256
