package dev.woge.host

import dev.woge.html.ApplicationUrl
import dev.woge.protocol.ByteSink
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.TargetRevision
import dev.woge.protocol.TargetRevisionStep
import java.util.Collections

/** Ordered, typed replacements. All rendering and payload validation finish before a result exists. */
public class ActionRegionUpdates internal constructor(
    private val interaction: InteractionSequence,
) {
    private val patches = mutableListOf<ReplacePatch>()
    private val targets = mutableSetOf<String>()
    private var epoch: PageEpoch? = null
    private var finished = false
    private var incomplete = false

    public fun <Input> replace(
        target: RegionTarget<Input>,
        input: Input,
        revision: TargetRevision = TargetRevision.INITIAL,
    ) {
        check(!finished) { "Action updates are already finished" }
        check(!incomplete) { "A previous action update failed" }
        incomplete = true
        require(patches.size < MAX_ACTION_UPDATES) { "Action update count exceeds $MAX_ACTION_UPDATES" }
        require(epoch == null || epoch == target.target.pageEpoch) { "Action updates must belong to one page epoch" }
        require(targets.add(target.target.region.value)) { "An action may replace each region only once" }
        epoch = target.target.pageEpoch
        val patch =
            ReplacePatch(
                PatchId.of("action-${patches.size + 1}"),
                target.target,
                interaction,
                TargetRevisionStep.after(revision),
                target.render(input),
            )
        PatchStreamV1.encoder(ByteSink {}).write(patch)
        patches.add(patch)
        incomplete = false
    }

    internal fun finish(
        fallback: ApplicationUrl,
        metadata: ResponseMetadata,
    ): PageResult.RegionUpdates {
        check(!finished) { "Action updates are already finished" }
        check(!incomplete) { "Action update preparation failed" }
        require(patches.isNotEmpty()) { "An action must update at least one region" }
        finished = true
        return PageResult.RegionUpdates(Collections.unmodifiableList(patches.toList()), fallback, metadata)
    }
}

/** Native submissions redirect to [fallback]; explicit enhanced action requests receive typed patches. */
public fun actionRegionUpdates(
    fallback: ApplicationUrl,
    interaction: InteractionSequence = InteractionSequence.INITIAL,
    headers: ResponseHeaders = ResponseHeaders.EMPTY,
    cookies: Iterable<ResponseCookie> = emptyList(),
    updates: ActionRegionUpdates.() -> Unit,
): PageResult.RegionUpdates =
    ActionRegionUpdates(interaction).apply(updates).finish(
        fallback,
        ResponseMetadata(contentType = null, headers = headers, cookies = cookies),
    )

public fun PageResult.RegionUpdates.nativeRedirect(): PageResult.Redirect =
    redirect(fallback, headers = metadata.headers, cookies = metadata.cookies)

private const val MAX_ACTION_UPDATES = 128
