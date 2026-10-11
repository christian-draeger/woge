package dev.woge.host

import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.TargetRevision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One page URL serving HTML, safe region refresh GETs and SSE invalidations.
 * [authorize] runs for all three modes, including every SSE reconnect. It must check the same access
 * rules as the document. Neither render epochs nor region IDs grant access.
 */
public class LiveRegionPage<Input : Any, Value>(
    public val page: PageUseCase<Input>,
    private val region: PageRegion<Value>,
    private val load: suspend (PageRequest<Input>) -> Value,
    private val changes: suspend (PageRequest<Input>) -> Flow<Unit>,
    private val authorize: suspend (PageRequest<Input>) -> PageResult.Failure? = { null },
) {
    public suspend fun document(request: PageRequest<Input>): PageResult = authorize(request) ?: page.open(request)

    /** Adapter entry point for a safe, side-effect-free refresh of the declared region only. */
    public suspend fun refresh(
        request: PageRequest<Input>,
        route: PageRoute<Input>,
        parameters: Map<String, String>,
    ): PageResult {
        authorize(request)?.let { return it }
        val ordering = ordering(request.context.render, parameters)
        return when {
            ordering == null -> failure(FailureCategory.BAD_REQUEST, request.context.correlationId)
            ordering.first.target.region.value != parameters["_woge_target"] ->
                failure(FailureCategory.NOT_FOUND, request.context.correlationId)
            else ->
                regionRefresh(
                    route.url(request.input),
                    ordering.first,
                    load(request),
                    ordering.second,
                    ordering.third,
                )
        }
    }

    private fun ordering(
        session: RenderSession,
        parameters: Map<String, String>,
    ): Triple<RegionTarget<Value>, TargetRevision, InteractionSequence>? =
        try {
            val target = session.resume(parameters.getValue("_woge_epoch")).target(region)
            val revision = TargetRevision.of(parameters.getValue("_woge_revision").toLong())
            require(revision.value < Long.MAX_VALUE)
            Triple(target, revision, InteractionSequence.of(parameters.getValue("_woge_interaction").toLong()))
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: NoSuchElementException) {
            null
        }

    /** Adapter entry point; uses the host's bounded live admission policy. */
    public suspend fun subscribe(
        request: PageRequest<Input>,
        epoch: String,
    ): LiveResult {
        authorize(request)?.let { return LiveResult.Refused(it) }
        val target =
            try {
                request.context.render
                    .resume(epoch)
                    .target(region)
            } catch (_: IllegalArgumentException) {
                null
            }
        return if (target == null) {
            liveRefused(FailureCategory.BAD_REQUEST, request.context.correlationId)
        } else {
            liveSubscription(listOf(target), changes(request).map { target })
        }
    }
}
