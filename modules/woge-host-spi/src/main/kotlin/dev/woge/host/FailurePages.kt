package dev.woge.host

import dev.woge.protocol.HtmlFrame
import kotlinx.coroutines.flow.flowOf

/** Application-owned HTML for controlled page failures; null keeps the response bodyless. */
public fun interface FailurePages {
    /** Receives only client-safe metadata, never raw inputs or exception details. */
    public fun render(failure: PublicFailure): HtmlFrame?

    public companion object {
        public val NONE: FailurePages = FailurePages { null }
    }
}

/** Adds optional failure HTML without changing the failure's HTTP status, headers or cookies. */
public fun PageResult.withFailurePages(pages: FailurePages): PageResult =
    when (this) {
        is PageResult.Failure ->
            pages.render(failure)?.let { frame ->
                PageResult.Document(
                    metadata =
                        ResponseMetadata(
                            status = metadata.status,
                            headers = metadata.headers,
                            cookies = metadata.cookies,
                        ),
                    frames = flowOf(frame),
                )
            } ?: this

        else -> this
    }
