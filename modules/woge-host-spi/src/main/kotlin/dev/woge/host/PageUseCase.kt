package dev.woge.host

import dev.woge.html.ApplicationUrl
import dev.woge.html.DEFAULT_HTML_CHUNK_CHARS
import dev.woge.html.ExternalUrl
import dev.woge.html.HtmlByteBudget
import dev.woge.html.HtmlSink
import dev.woge.html.HtmlWriter
import dev.woge.html.StreamingHtmlSink
import dev.woge.protocol.HtmlFrame
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.htmlFrame
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import java.nio.charset.StandardCharsets

/** One typed page request after routing and input decoding by a host adapter. */
public class PageRequest<Input : Any>(
    public val input: Input,
    public val context: RequestContext,
) {
    override fun toString(): String = "PageRequest(input=<redacted>, context=$context)"
}

/** Framework-neutral application entry point for one typed page. */
public fun interface PageUseCase<Input : Any> {
    /** Authorizes and opens one cold page result. */
    public suspend fun open(request: PageRequest<Input>): PageResult
}

/** A page outcome whose metadata is final before any document frame is collected. */
public sealed interface PageResult {
    public val metadata: ResponseMetadata

    /** A conditional GET/HEAD matched after authorization; no document frames are rendered. */
    public class NotModified internal constructor(
        override val metadata: ResponseMetadata,
    ) : PageResult {
        init {
            require(metadata.status == ResponseStatus.NOT_MODIFIED && metadata.contentType == null) {
                "Not-modified results require bodyless HTTP 304 metadata"
            }
        }
    }

    /** Fully prepared replacements with their native redirect or validation document. */
    public class RegionUpdates internal constructor(
        public val patches: List<ReplacePatch>,
        public val nativeResult: PageResult,
        public val focusSummary: FormElementId?,
        override val metadata: ResponseMetadata,
        public val patchStreamLimits: PatchStreamLimits = PatchStreamLimits(),
    ) : PageResult

    /** A cold ordered stream of lazily rendered HTML frames. */
    public class Document(
        override val metadata: ResponseMetadata,
        public val frames: Flow<HtmlFrame>,
        public val maxBytes: Long = DEFAULT_PAGE_BYTES,
    ) : PageResult {
        init {
            require(maxBytes > 0) { "HTML page byte budget must be positive" }
            require(metadata.status.allowsBody) {
                "Woge HTML document status ${metadata.status.code} does not permit a response body"
            }
            require(metadata.contentType == ContentType.HTML_UTF_8) {
                "Woge HTML documents must use 'text/html; charset=UTF-8'"
            }
        }
    }

    /** A bodyless redirect with an already policy-validated location. */
    public class Redirect internal constructor(
        override val metadata: ResponseMetadata,
        public val location: RedirectLocation,
    ) : PageResult {
        init {
            require(metadata.status.isRedirect) { "Redirect result requires a 3xx response status" }
            require(metadata.contentType == null) { "Redirect result must not declare a response body type" }
        }
    }

    /** A controlled, bodyless failure containing only client-safe diagnostic metadata. */
    public class Failure internal constructor(
        override val metadata: ResponseMetadata,
        public val failure: PublicFailure,
    ) : PageResult {
        init {
            require(metadata.status == failure.category.status) {
                "Failure response status must match its public category"
            }
            require(metadata.contentType == null) { "Failure result must not declare a response body type" }
        }
    }
}

/** Client-safe failure categories with deterministic default HTTP mappings. */
public enum class FailureCategory(
    public val status: ResponseStatus,
) {
    BAD_REQUEST(ResponseStatus.BAD_REQUEST),
    UNAUTHENTICATED(ResponseStatus.UNAUTHORIZED),
    FORBIDDEN(ResponseStatus.FORBIDDEN),
    NOT_FOUND(ResponseStatus.NOT_FOUND),
    CONFLICT(ResponseStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(ResponseStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(ResponseStatus.UNSUPPORTED_MEDIA_TYPE),
    RATE_LIMITED(ResponseStatus.TOO_MANY_REQUESTS),
    INTERNAL(ResponseStatus.INTERNAL_SERVER_ERROR),
    UNAVAILABLE(ResponseStatus.SERVICE_UNAVAILABLE),
}

/** Public failure metadata. Raw inputs, exception messages and stack traces have no field here. */
public data class PublicFailure(
    public val category: FailureCategory,
    public val correlationId: CorrelationId,
)

/** A redirect location proven to satisfy the application's redirect policy. */
public sealed interface RedirectLocation {
    public val value: String
}

/** A same-application redirect. This is the default redirect capability. */
public class ApplicationRedirectLocation internal constructor(
    public val url: ApplicationUrl,
) : RedirectLocation {
    override val value: String
        get() = url.value
}

/** An external redirect admitted by an explicitly installed application policy. */
public class ExternalRedirectLocation internal constructor(
    public val url: ExternalUrl,
) : RedirectLocation {
    override val value: String
        get() = url.value
}

/** Explicit application allowlist hook required before Woge creates an external redirect. */
public fun interface ExternalRedirectPolicy {
    public fun allows(url: ExternalUrl): Boolean
}

/** Creates a complete one-frame HTML page. */
public fun htmlPage(
    metadata: ResponseMetadata = ResponseMetadata(),
    maxBytes: Long = DEFAULT_PAGE_BYTES,
    content: HtmlWriter.() -> Unit,
): PageResult.Document = PageResult.Document(metadata, flowOf(htmlFrame(content)), maxBytes)

/** Creates an HTML page whose ordered frames are produced lazily during collection. */
public fun streamingHtmlPage(
    frames: Flow<HtmlFrame>,
    metadata: ResponseMetadata = ResponseMetadata(),
    maxBytes: Long = DEFAULT_PAGE_BYTES,
): PageResult.Document = PageResult.Document(metadata, frames, maxBytes)

/** Default cumulative rendered UTF-8 bytes for one HTML response, across every frame. */
public const val DEFAULT_PAGE_BYTES: Long = 16L * 1024 * 1024

/** Creates a bodyless same-application redirect. */
public fun redirect(
    location: ApplicationUrl,
    status: ResponseStatus = ResponseStatus.SEE_OTHER,
    headers: ResponseHeaders = ResponseHeaders.EMPTY,
    cookies: Iterable<ResponseCookie> = emptyList(),
): PageResult.Redirect =
    PageResult.Redirect(
        metadata =
            ResponseMetadata(
                status = status,
                contentType = null,
                headers = headers,
                cookies = cookies,
            ),
        location = ApplicationRedirectLocation(location),
    )

/** Creates an external redirect only after [policy] admits its validated URL. */
public fun externalRedirect(
    location: ExternalUrl,
    policy: ExternalRedirectPolicy,
    status: ResponseStatus = ResponseStatus.SEE_OTHER,
    headers: ResponseHeaders = ResponseHeaders.EMPTY,
    cookies: Iterable<ResponseCookie> = emptyList(),
): PageResult.Redirect {
    require(policy.allows(location)) { "External redirect was rejected by application policy" }
    return PageResult.Redirect(
        metadata =
            ResponseMetadata(
                status = status,
                contentType = null,
                headers = headers,
                cookies = cookies,
            ),
        location = ExternalRedirectLocation(location),
    )
}

/** Creates a controlled failure with no application payload or exception detail. */
public fun failure(
    category: FailureCategory,
    correlationId: CorrelationId,
): PageResult.Failure =
    PageResult.Failure(
        metadata =
            ResponseMetadata(
                status = category.status,
                contentType = null,
            ),
        failure = PublicFailure(category, correlationId),
    )

/**
 * Collects and renders this cold document into [downstream].
 *
 * Each frame is a flush boundary. Output chunks remain bounded, coroutine cancellation is checked
 * for every HTML writer call, and render/flow/downstream failures propagate unchanged. The sink's
 * host response is neither flushed nor closed by this function.
 */
public suspend fun PageResult.Document.writeTo(
    downstream: HtmlSink,
    maxChunkChars: Int = DEFAULT_HTML_CHUNK_CHARS,
) {
    val context = currentCoroutineContext()
    val budget = HtmlByteBudget(maxBytes)
    val streamingSink =
        StreamingHtmlSink(
            HtmlSink { chunk ->
                budget.consume(chunk.toByteArray(StandardCharsets.UTF_8).size)
                downstream.write(chunk)
            },
            maxChunkChars,
        )
    val cancellableSink =
        HtmlSink { value ->
            context.ensureActive()
            streamingSink.write(value)
        }

    frames.collect { frame ->
        context.ensureActive()
        frame.writeTo(cancellableSink)
        context.ensureActive()
        streamingSink.flush()
    }
}
