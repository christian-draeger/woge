package dev.woge.runtime

import dev.woge.host.RequestTrace
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.WogeOutcome
import dev.woge.protocol.ByteSink
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import dev.woge.protocol.TargetRevisionStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream

/** One complete encoder flush boundary for a host adapter. */
public class EncodedPatchChunk internal constructor(
    /** Fresh bytes owned by this chunk. Consumers must not mutate them. */
    public val bytes: ByteArray,
    /** Whether these bytes contain the terminal frame. */
    public val terminal: Boolean,
) {
    init {
        require(bytes.isNotEmpty()) { "An encoded patch chunk must not be empty" }
    }

    override fun toString(): String = "EncodedPatchChunk(bytes=${bytes.size}, terminal=$terminal)"
}

/** Maps a page-load deferred update to its single contiguous target-revision step. */
public fun DeferredRegionUpdate.toReplacePatch(patchId: PatchId): ReplacePatch =
    ReplacePatch(
        patchId = patchId,
        target = region.target,
        interactionSequence = InteractionSequence.INITIAL,
        revision = TargetRevisionStep.after(region.initialRevision),
        html = html,
    )

/**
 * Encodes the stream preamble, then each deferred update, as independently flushable chunks.
 *
 * The preamble chunk is emitted before any region is awaited so hosts commit status and headers early.
 * A terminal chunk follows the last update. Upstream, patch-ID, or encoder failures are
 * propagated without manufacturing a successful terminal frame.
 */
@Suppress("TooGenericExceptionCaught")
public fun Flow<DeferredRegionUpdate>.encodeDeferredPatchStream(
    observer: WogeObserver = WogeObserver.NONE,
    requestTrace: RequestTrace? = null,
    patchId: (DeferredRegionUpdate) -> PatchId,
): Flow<EncodedPatchChunk> =
    flow {
        val pending = ByteArrayOutputStream()
        val encoder = PatchStreamV1.encoder(ByteSink(pending::write))
        encoder.start()
        emit(EncodedPatchChunk(pending.toByteArray(), terminal = false))
        pending.reset()

        collect { update ->
            val id = patchId(update)
            val observation =
                observer.startOperation(
                    WogeOperation.PATCH_ENCODE,
                    WogeObservationContext(
                        requestTrace = requestTrace,
                        target = update.region.target,
                        patchId = id,
                    ),
                )
            try {
                encoder.write(update.toReplacePatch(id))
                emit(EncodedPatchChunk(pending.toByteArray(), terminal = false))
                pending.reset()
                observation.finish(WogeOutcome.SUCCEEDED)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                observation.finish(WogeOutcome.CANCELLED)
                throw cancelled
            } catch (failure: Throwable) {
                observation.finish(WogeOutcome.FAILED)
                throw failure
            }
        }

        encoder.complete()
        emit(EncodedPatchChunk(pending.toByteArray(), terminal = true))
    }
