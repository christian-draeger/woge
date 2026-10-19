package dev.woge.host

import dev.woge.html.ApplicationUrl
import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.metadata
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.TargetRevision
import java.util.UUID

/** Host-owned state for one rendered document or enhanced request. Not an authorization token. */
public class RenderSession internal constructor(
    private val secret: RenderIdentitySecret,
    public val epoch: String = UUID.randomUUID().toString(),
    private val revisions: Map<String, Long> = emptyMap(),
    private val interaction: Long = 0,
) {
    private val identity = PageIdentity(PageEpoch.of(epoch), secret)

    /** Resumes a deferred document, using the same host secret. */
    public fun resume(epoch: String): RenderSession = RenderSession(secret, epoch)

    /** Gets a typed target without exposing protocol identity types. */
    public fun <Input> target(region: PageRegion<Input>): RegionTarget<Input> = region.target(identity)

    public fun <Key, Input> target(
        region: KeyedRegion<Key, Input>,
        key: Key,
    ): RegionTarget<Input> = region.target(identity, key)

    /** Writes the document epoch in the HTML head. */
    public fun metadata(writer: HtmlWriter) {
        writer.metadata("woge-page-epoch", epoch)
        writer.metadata("woge-render-session", "1")
    }

    /** Native posts redirect; enhanced posts replace the declared regions at their current revisions. */
    public fun updates(
        fallback: ApplicationUrl,
        updates: RenderUpdates.() -> Unit,
    ): PageResult.RegionUpdates =
        actionRegionUpdates(fallback, InteractionSequence.of(interaction)) {
            RenderUpdates(this, this@RenderSession, revisions).apply(updates)
        }

    /**
     * Native validation returns [nativePage]; enhancement replaces typed regions and focuses [summary].
     * Uses this request's ordering without sharing the native document's identity registration.
     */
    public fun validation(
        nativePage: PageResult.Document,
        summary: FormElementId,
        patchStreamLimits: PatchStreamLimits = PatchStreamLimits(),
        updates: RenderUpdates.() -> Unit,
    ): PageResult.RegionUpdates {
        require(nativePage.metadata.status == ResponseStatus.BAD_REQUEST) { "A validation page must use status 400" }
        val session = RenderSession(secret, epoch, revisions, interaction)
        val prepared = ActionRegionUpdates(InteractionSequence.of(interaction), patchStreamLimits)
        RenderUpdates(prepared, session, revisions).apply(updates)
        return PageResult.RegionUpdates(
            prepared.finish(),
            nativePage,
            summary,
            ResponseMetadata(
                status = ResponseStatus.BAD_REQUEST,
                contentType = null,
                headers = nativePage.metadata.headers,
                cookies = nativePage.metadata.cookies,
            ),
            patchStreamLimits,
        )
    }
}

/** Typed replacements using the ordering state supplied by the shipped client. */
public class RenderUpdates internal constructor(
    private val updates: ActionRegionUpdates,
    private val session: RenderSession,
    private val revisions: Map<String, Long>,
) {
    public fun <Input> replace(
        region: PageRegion<Input>,
        input: Input,
    ) {
        val target = session.target(region)
        updates.replace(target, input, TargetRevision.of(revisions[target.target.region.value] ?: 0))
    }

    public fun <Key, Input> replace(
        region: KeyedRegion<Key, Input>,
        key: Key,
        input: Input,
    ) {
        val target = session.target(region, key)
        updates.replace(target, input, TargetRevision.of(revisions[target.target.region.value] ?: 0))
    }
}

/** Uses a host-configured secret, or a random process-local secret for development. */
public class RenderSessions(
    private val secret: RenderIdentitySecret = PROCESS_RENDER_SECRET,
) {
    public fun create(): RenderSession = RenderSession(secret)

    /** Adapter ingress. Invalid ordering data is rejected without echoing it in diagnostics. */
    public fun forRequest(headers: RequestHeaders): RenderSession {
        val epoch = headers.singleRenderHeader("Woge-Page-Epoch") ?: return create()
        return try {
            val entries = headers.singleRenderHeader("Woge-Region-Revisions").orEmpty()
            require(entries.length <= MAX_ORDERING_HEADER)
            val fields = if (entries.isEmpty()) emptyList() else entries.split(',')
            require(fields.size <= LiveLimits.MAX_TARGETS)
            val revisions =
                fields.associate { entry ->
                    val parts = entry.split(':')
                    require(parts.size == 2)
                    val target =
                        RegionTargetId.of(parts[0]).value
                    val revision = TargetRevision.of(parts[1].toLong()).value
                    require(revision < Long.MAX_VALUE)
                    target to revision
                }
            require(revisions.size == fields.size)
            RenderSession(
                secret,
                PageEpoch.of(epoch).value,
                revisions,
                InteractionSequence.of(headers.singleRenderHeader("Woge-Interaction")?.toLong() ?: 0).value,
            )
        } catch (_: IllegalArgumentException) {
            throw RenderSessionException()
        }
    }
}

/** Malformed enhanced-request ordering state. */
public class RenderSessionException internal constructor() : IllegalArgumentException("Invalid render session")

/** Adapter-owned session installation; established security and mutation facts are preserved. */
public fun RequestContext.withRenderSession(session: RenderSession): RequestContext =
    RequestContext(method, trace, language, headers, cookies, security, mutationIdentity, session)

/** Renders one live region. The shipped client uses the same page URL for SSE and safe refresh GETs. */
@Suppress("LongParameterList")
public fun <Input> HtmlWriter.liveRegion(
    session: RenderSession,
    region: PageRegion<Input>,
    input: Input,
    pageUrl: ApplicationUrl,
    elementName: String = "div",
    attributes: Attributes.() -> Unit = {},
) {
    region(session.target(region), input, elementName, attributes = {
        data("woge-live-url", pageUrl.value)
        data("woge-live-epoch", session.epoch)
        attributes()
    })
}

private fun RequestHeaders.singleRenderHeader(name: String): String? {
    val values = values(HeaderName.of(name))
    if (values.size > 1) throw RenderSessionException()
    return values.singleOrNull()?.value
}

internal val PROCESS_RENDER_SECRET: RenderIdentitySecret = RenderIdentitySecret.random()
private const val MAX_ORDERING_HEADER = 8192

/** Installs host render state around portable application execution. */
public fun <Input : Any> RenderSessions.page(useCase: PageUseCase<Input>): PageUseCase<Input> =
    PageUseCase { request ->
        try {
            useCase.open(
                PageRequest(request.input, request.context.withRenderSession(forRequest(request.context.headers))),
            )
        } catch (_: RenderSessionException) {
            failure(FailureCategory.BAD_REQUEST, request.context.correlationId)
        }
    }
