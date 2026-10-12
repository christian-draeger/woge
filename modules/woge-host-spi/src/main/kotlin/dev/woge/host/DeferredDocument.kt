package dev.woge.host

import dev.woge.html.HtmlWriter
import dev.woge.protocol.HtmlFrame
import dev.woge.protocol.htmlFrame
import dev.woge.protocol.initialDocumentEnd
import dev.woge.protocol.initialDocumentStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A typed initial document with independent body-level regions.
 *
 * Install the optional DPU browser module in [head]. Its framed HTML envelope is also the fallback
 * when the browser or an individual region is ineligible for native updates. Loading must provide
 * an ordinary link to complete HTML for visitors without JavaScript.
 */
public class DeferredDocument internal constructor(
    public val head: HtmlFrame,
    public val shell: HtmlFrame,
    public val regions: Iterable<DeferredRegion>,
    public val tail: HtmlFrame,
) {
    /** Useful HTML-only rendering when no streaming host has been installed. */
    public fun loadingFrames(): Flow<HtmlFrame> =
        flow {
            emit(initialDocumentStart(head))
            emit(shell)
            regions.forEach { region -> emit(htmlFrame { regionPlaceholder(region) }) }
            emit(tail)
            emit(initialDocumentEnd())
        }
}

/**
 * Declares an opt-in original-document stream using normal HTML DSL blocks.
 *
 * The head and visible shell flush before deferred work starts. Regions are independent top-level
 * body elements; use a normal complete document for layouts requiring regions nested inside tables
 * or other parser-sensitive containers. No application HTML strings or parser rewriting are used.
 */
@Suppress("LongParameterList")
public fun deferredHtmlPage(
    regions: Iterable<DeferredRegion>,
    head: HtmlWriter.() -> Unit = {},
    shell: HtmlWriter.() -> Unit,
    tail: HtmlWriter.() -> Unit = {},
    metadata: ResponseMetadata = ResponseMetadata(),
    maxBytes: Long = DEFAULT_PAGE_BYTES,
): PageResult.Document {
    val document = DeferredDocument(htmlFrame(head), htmlFrame(shell), regions, htmlFrame(tail))
    return PageResult.Document(metadata, document.loadingFrames(), maxBytes, document)
}
