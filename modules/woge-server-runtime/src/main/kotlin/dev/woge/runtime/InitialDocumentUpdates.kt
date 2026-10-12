package dev.woge.runtime

import dev.woge.host.DeferredRegion
import dev.woge.host.PatchStreamBudget
import dev.woge.host.PatchStreamLimits
import dev.woge.host.RequestTrace
import dev.woge.host.ResourceLimitException
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.WogeOutcome
import dev.woge.protocol.ByteSink
import dev.woge.protocol.HtmlFrame
import dev.woge.protocol.InitialDocumentRange
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.htmlFrame
import dev.woge.protocol.isInitialDocumentHtmlEligible
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

/** Only one bounded region is materialized at a time; late rejection stays on the framed decoder. */
internal class InitialDocumentUpdates(
    private val limits: PatchStreamLimits,
    private val observer: WogeObserver,
    private val trace: RequestTrace?,
) {
    fun frames(
        updates: Flow<DeferredRegionUpdate>,
        ranges: Map<DeferredRegion, InitialDocumentRange>,
    ): Flow<HtmlFrame> =
        flow {
            val budget = PatchStreamBudget(limits)
            val pending = ByteArrayOutputStream()
            val encoder =
                PatchStreamV1.encoder(
                    ByteSink { bytes ->
                        account(budget) { consumeBytes(bytes.size) }
                        pending.write(bytes)
                    },
                )
            encoder.start()
            emit(envelope(pending))
            updates.collect { update ->
                val patch = update.toReplacePatch(PatchId.of("dpu-${UUID.randomUUID()}"))
                observer.observeOperation(
                    WogeOperation.PATCH_ENCODE,
                    WogeObservationContext(requestTrace = trace, target = patch.target, patchId = patch.patchId),
                ) {
                    account(budget) { admitPatch() }
                    val range = ranges[update.region]
                    if (range != null && isInitialDocumentHtmlEligible(update.html)) {
                        // Count actual native wire bytes against the same response patch budget.
                        val frame = range.replaceFrame(patch)
                        frame.writeTo(
                            dev.woge.html.HtmlSink { value ->
                                account(budget) { consumeBytes(value.toByteArray(Charsets.UTF_8).size) }
                            },
                        )
                        emit(frame)
                    } else {
                        encoder.write(patch)
                        emit(envelope(pending))
                    }
                }
            }
            encoder.complete()
            emit(envelope(pending, terminal = true))
        }

    private fun account(
        budget: PatchStreamBudget,
        operation: PatchStreamBudget.() -> Unit,
    ) {
        try {
            budget.operation()
        } catch (exceeded: ResourceLimitException) {
            observer
                .startOperation(
                    WogeOperation.PATCH_ENCODE,
                    WogeObservationContext(requestTrace = trace, exceededLimit = exceeded.exceededLimit),
                ).finish(WogeOutcome.REJECTED)
            throw exceeded
        }
    }
}

private fun envelope(
    pending: ByteArrayOutputStream,
    terminal: Boolean = false,
): HtmlFrame {
    val encoded = Base64.getEncoder().encodeToString(pending.toByteArray())
    pending.reset()
    return htmlFrame {
        element("template", attributes = {
            data("woge-dpu-frame", if (terminal) "end" else "chunk")
        }) { text(encoded) }
        element("span", attributes = {
            boolean("hidden")
            data("woge-dpu-ready", "")
        })
    }
}
