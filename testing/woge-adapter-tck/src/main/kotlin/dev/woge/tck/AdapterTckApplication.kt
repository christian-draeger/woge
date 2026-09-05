package dev.woge.tck

import dev.woge.host.CookieName
import dev.woge.host.DeferredRegion
import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.FailureCategory
import dev.woge.host.HeaderName
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.RequestMethod
import dev.woge.host.ResponseHeaders
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.deferredRegion
import dev.woge.host.failure
import dev.woge.host.httpHeader
import dev.woge.host.redirect
import dev.woge.host.responseCookie
import dev.woge.host.streamingHtmlPage
import dev.woge.html.applicationUrl
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchHtml
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.htmlFrame
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentLinkedQueue

/** Page scenarios understood by the canonical TCK route. */
public enum class AdapterTckPageScenario(
    public val pathSegment: String,
) {
    DOCUMENT("document"),
    REDIRECT("redirect"),
    CONTROLLED_FAILURE("controlled-failure"),
    PRE_STREAM_FAILURE("pre-stream-failure"),
    ;

    public companion object {
        /** Decodes the route segment without involving a host-framework type. */
        public fun fromPath(pathSegment: String): AdapterTckPageScenario =
            entries.firstOrNull { it.pathSegment == pathSegment }
                ?: throw IllegalArgumentException("Unknown adapter TCK page scenario")
    }
}

/** Deferred-stream scenarios understood by the canonical TCK route. */
public enum class AdapterTckDeferredScenario(
    public val pathSegment: String,
) {
    COMPLETION_ORDER("completion-order"),
    CLIENT_ABORT("client-abort"),
    ;

    public companion object {
        /** Decodes the route segment without involving a host-framework type. */
        public fun fromPath(pathSegment: String): AdapterTckDeferredScenario =
            entries.firstOrNull { it.pathSegment == pathSegment }
                ?: throw IllegalArgumentException("Unknown adapter TCK deferred scenario")
    }
}

/** Shared portable application fixture compiled once and bound unchanged by every adapter. */
public class AdapterTckApplication internal constructor() {
    private val state: AdapterTckFixtureState = AdapterTckFixtureState()

    public val pages: PageUseCase<AdapterTckPageScenario> = PageUseCase(state::openPage)
    public val deferredRegions: DeferredRegionsUseCase<AdapterTckDeferredScenario> =
        DeferredRegionsUseCase(state::deferredRegions)

    internal fun fixtureState(): AdapterTckFixtureState = state
}

internal class AdapterTckFixtureState {
    val documentTail: CompletableDeferred<Unit> = CompletableDeferred()
    val slowRegion: CompletableDeferred<PatchHtml> = CompletableDeferred()
    val cancelledRegion: CompletableDeferred<Unit> = CompletableDeferred()
    private val observedContexts: ConcurrentLinkedQueue<RequestContext> = ConcurrentLinkedQueue()

    suspend fun openPage(request: PageRequest<AdapterTckPageScenario>): PageResult {
        observedContexts += request.context
        return when (request.input) {
            AdapterTckPageScenario.DOCUMENT -> document()
            AdapterTckPageScenario.REDIRECT -> redirect(applicationUrl("/woge-tck/redirect-target"))
            AdapterTckPageScenario.CONTROLLED_FAILURE ->
                failure(FailureCategory.NOT_FOUND, request.context.correlationId)
            AdapterTckPageScenario.PRE_STREAM_FAILURE -> error(PRE_STREAM_PRIVATE_DETAIL)
        }
    }

    fun context(method: RequestMethod): RequestContext? = observedContexts.firstOrNull { it.method == method }

    fun deferredRegions(request: PageRequest<AdapterTckDeferredScenario>): Iterable<DeferredRegion> =
        when (request.input) {
            AdapterTckDeferredScenario.COMPLETION_ORDER ->
                listOf(
                    region("slow") { slowRegion.await() },
                    region("fast") { patch("Fast region") },
                )
            AdapterTckDeferredScenario.CLIENT_ABORT ->
                listOf(
                    region("waiting") {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelledRegion.complete(Unit)
                        }
                    },
                    region("ready") { patch("Ready region") },
                )
        }

    private fun document(): PageResult.Document =
        streamingHtmlPage(
            frames =
                flow {
                    emit(htmlFrame { element("main") { text("TCK shell") } })
                    documentTail.await()
                    emit(htmlFrame { element("footer") { text("TCK document tail") } })
                },
            metadata =
                ResponseMetadata(
                    status = ResponseStatus.of(TCK_DOCUMENT_STATUS_CODE),
                    headers = ResponseHeaders.of(httpHeader(TCK_RESPONSE_HEADER, "mapped")),
                    cookies = listOf(responseCookie("woge-tck", "safe")),
                ),
        )

    private fun region(
        id: String,
        content: suspend () -> PatchHtml,
    ): DeferredRegion =
        deferredRegion(
            target = PatchTarget(PageEpoch.of("adapter-tck-page"), RegionTargetId.of(id)),
            loading = { element("p") { text("Loading $id") } },
            onFailure = { patch("Unavailable") },
            content = content,
        )

    private fun patch(text: String): PatchHtml = patchHtml { element("p") { text(text) } }
}

internal fun RequestContext.header(name: String): String? = headers.values(HeaderName.of(name)).firstOrNull()?.value

internal fun RequestContext.cookie(name: String): String? = cookies.first(CookieName.of(name))?.value

internal const val TCK_RESPONSE_HEADER: String = "x-woge-tck"
internal const val PRE_STREAM_PRIVATE_DETAIL: String = "tck-private-pre-stream-detail"
internal const val TCK_DOCUMENT_STATUS_CODE: Int = 202
