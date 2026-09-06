package dev.woge.ktor

import dev.woge.host.DeferredRegionFailure
import dev.woge.host.deferredRegion
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.patchHtml
import dev.woge.runtime.DeferredRegionExecutor
import dev.woge.runtime.encodeDeferredPatchStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class KtorResponseLifecycleTest {
    @Test
    fun `downstream write failure cancels structured region children`() {
        val waitingStarted = CompletableDeferred<Unit>()
        val waitingCancelled = CompletableDeferred<Unit>()
        val regions =
            listOf(
                region("waiting") {
                    try {
                        waitingStarted.complete(Unit)
                        awaitCancellation()
                    } finally {
                        waitingCancelled.complete(Unit)
                    }
                },
                region("ready") { patchHtml { text("Ready") } },
            )
        val chunks =
            DeferredRegionExecutor()
                .execute(regions)
                .encodeDeferredPatchStream { PatchId.of("lifecycle-${it.region.target.region.value}") }

        assertThrows(IOException::class.java) {
            runBlocking {
                chunks.writeAndFlushKtorChunks {
                    waitingStarted.await()
                    throw IOException("simulated downstream failure")
                }
            }
        }
        runBlocking { withTimeout(5.seconds) { waitingCancelled.await() } }
    }
}

private fun region(
    id: String,
    content: suspend () -> dev.woge.protocol.PatchHtml,
) = deferredRegion(
    target = PatchTarget(PageEpoch.of("ktor-lifecycle"), RegionTargetId.of(id)),
    loading = { text("Loading") },
    onFailure = { _: DeferredRegionFailure -> patchHtml { text("Unavailable") } },
    content = content,
)
