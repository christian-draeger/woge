package dev.woge.runtime

import dev.woge.host.ResourceLimit
import dev.woge.host.ResourceLimitExceeded
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.WogeOutcome
import dev.woge.html.HtmlByteBudget
import dev.woge.html.HtmlByteLimitException
import dev.woge.html.HtmlSink
import dev.woge.html.StreamingHtmlSink
import dev.woge.protocol.HtmlFrame
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.charset.StandardCharsets

/** Shared per-frame rendering; pass one cumulative budget for all frames of a response. */
public suspend fun HtmlFrame.renderByteChunks(
    budget: HtmlByteBudget,
    observer: WogeObserver = WogeObserver.NONE,
    observationContext: WogeObservationContext = WogeObservationContext(),
): List<ByteArray> {
    val chunks = mutableListOf<ByteArray>()
    writeByteChunks(budget, observer, observationContext) { chunks.add(it) }
    return chunks
}

/** Renders to bounded encoded chunks; downstream owns its response flush and close lifecycle. */
public suspend fun HtmlFrame.writeByteChunks(
    budget: HtmlByteBudget,
    observer: WogeObserver = WogeObserver.NONE,
    observationContext: WogeObservationContext = WogeObservationContext(),
    downstream: (ByteArray) -> Unit,
) {
    val context = currentCoroutineContext()
    val sink =
        StreamingHtmlSink(
            HtmlSink { value ->
                context.ensureActive()
                val encoded = value.toByteArray(StandardCharsets.UTF_8)
                try {
                    budget.consume(encoded.size)
                } catch (exceeded: HtmlByteLimitException) {
                    observer
                        .startOperation(
                            WogeOperation.SHELL_RENDER,
                            observationContext.copy(
                                exceededLimit = ResourceLimitExceeded(ResourceLimit.PAGE_BYTES, exceeded.threshold),
                            ),
                        ).finish(WogeOutcome.REJECTED)
                    throw exceeded
                }
                downstream(encoded)
            },
        )
    context.ensureActive()
    writeTo(
        HtmlSink { value ->
            context.ensureActive()
            sink.write(value)
        },
    )
    context.ensureActive()
    sink.flush()
}
