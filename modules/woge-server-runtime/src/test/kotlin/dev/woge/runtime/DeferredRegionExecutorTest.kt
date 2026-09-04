package dev.woge.runtime

import dev.woge.host.DeferredRegion
import dev.woge.host.DeferredRegionFailure
import dev.woge.host.deferredRegion
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchHtml
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DeferredRegionExecutorTest {
    @Test
    fun `completed regions are emitted in completion order`() =
        runTest {
            val slowContent = CompletableDeferred<PatchHtml>()
            val fastContent = CompletableDeferred<PatchHtml>()
            val collection =
                async {
                    DeferredRegionExecutor()
                        .execute(
                            listOf(
                                region("slow") { slowContent.await() },
                                region("fast") { fastContent.await() },
                            ),
                        ).toList()
                }

            runCurrent()
            fastContent.complete(html("fast result"))
            runCurrent()
            slowContent.complete(html("slow result"))

            val updates = collection.await()
            assertEquals(listOf("fast", "slow"), updates.map { it.region.target.region.value })
            assertEquals(listOf("fast result", "slow result"), updates.map { it.html.textContent() })
            assertTrue(updates.all { it is DeferredRegionUpdate.Resolved })
        }

    @Test
    fun `configured concurrency bounds active region work`() =
        runTest {
            val active = AtomicInteger()
            val maximumActive = AtomicInteger()
            val release = CompletableDeferred<Unit>()
            val regions =
                (1..4).map { index ->
                    region("region-$index") {
                        val activeNow = active.incrementAndGet()
                        maximumActive.updateAndGet { current -> maxOf(current, activeNow) }
                        try {
                            release.await()
                            html("region $index")
                        } finally {
                            active.decrementAndGet()
                        }
                    }
                }
            val collection =
                async {
                    DeferredRegionExecutor(DeferredRegionPolicy(maxConcurrency = 2)).execute(regions).toList()
                }

            runCurrent()
            assertEquals(2, maximumActive.get())
            release.complete(Unit)

            assertEquals(4, collection.await().size)
            assertEquals(2, maximumActive.get())
        }

    @Test
    fun `timeout emits controlled failure content without cancelling siblings`() =
        runTest {
            val updates =
                DeferredRegionExecutor(DeferredRegionPolicy(regionTimeout = 1.seconds))
                    .execute(
                        listOf(
                            region("waiting") {
                                awaitCancellation()
                            },
                            region("ready") { html("ready") },
                        ),
                    ).toList()

            val failed = updates.filterIsInstance<DeferredRegionUpdate.Failed>().single()
            assertEquals(DeferredRegionFailure.TIMED_OUT, failed.failure)
            assertEquals("timed out", failed.html.textContent())
            assertEquals(
                "ready",
                updates
                    .filterIsInstance<DeferredRegionUpdate.Resolved>()
                    .single()
                    .html
                    .textContent(),
            )
        }

    @Test
    fun `application failure is isolated and retains its cause for host diagnostics`() =
        runTest {
            val cause = IllegalStateException("database detail must not enter fallback HTML")
            val updates =
                DeferredRegionExecutor()
                    .execute(
                        listOf(
                            region("failed") { throw cause },
                            region("ready") { html("ready") },
                        ),
                    ).toList()

            val failed = updates.filterIsInstance<DeferredRegionUpdate.Failed>().single()
            assertEquals(DeferredRegionFailure.FAILED, failed.failure)
            assertEquals(cause::class, failed.cause?.let { it::class })
            assertEquals(cause.message, failed.cause?.message)
            assertEquals("failed", failed.html.textContent())
            assertFalse(failed.html.value.contains(cause.message.orEmpty()))
            assertEquals(1, updates.count { it is DeferredRegionUpdate.Resolved })
        }

    @Test
    fun `failure renderer exceptions stop the execution`() {
        val fallbackFailure = IllegalArgumentException("invalid fallback")
        val region =
            deferredRegion(
                target = PatchTarget(PageEpoch.of("page-1"), RegionTargetId.of("failed")),
                loading = { text("loading") },
                onFailure = { throw fallbackFailure },
                content = { throw IllegalStateException("content failed") },
            )

        val thrown =
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { DeferredRegionExecutor().execute(listOf(region)).toList() }
            }

        assertEquals(fallbackFailure.message, thrown.message)
    }

    @Test
    fun `collector cancellation cancels active region children`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val collector =
                launch {
                    DeferredRegionExecutor()
                        .execute(
                            listOf(
                                region("waiting") {
                                    started.complete(Unit)
                                    try {
                                        awaitCancellation()
                                    } finally {
                                        cancelled.complete(Unit)
                                    }
                                },
                            ),
                        ).toList()
                }

            started.await()
            collector.cancelAndJoin()

            assertTrue(cancelled.isCompleted)
        }

    @Test
    fun `one execution rejects duplicate targets and mixed page epochs before work starts`() {
        var started = false
        val first =
            region("summary") {
                started = true
                html("first")
            }
        val duplicate =
            region("summary") {
                started = true
                html("duplicate")
            }
        val otherPage =
            region("other", pageEpoch = "page-2") {
                started = true
                html("other")
            }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { DeferredRegionExecutor().execute(listOf(first, duplicate)).toList() }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { DeferredRegionExecutor().execute(listOf(first, otherPage)).toList() }
        }
        assertFalse(started)
    }

    @Test
    fun `policy requires usable concurrency and timeout limits`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeferredRegionPolicy(maxConcurrency = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeferredRegionPolicy(regionTimeout = Duration.ZERO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeferredRegionPolicy(regionTimeout = Duration.INFINITE)
        }
    }
}

private fun region(
    id: String,
    pageEpoch: String = "page-1",
    content: suspend () -> PatchHtml,
): DeferredRegion =
    deferredRegion(
        target = PatchTarget(PageEpoch.of(pageEpoch), RegionTargetId.of(id)),
        loading = { element("p") { text("loading") } },
        onFailure = { failure -> html(if (failure == DeferredRegionFailure.TIMED_OUT) "timed out" else "failed") },
        content = content,
    )

private fun html(text: String): PatchHtml = patchHtml { element("p") { text(text) } }

private fun PatchHtml.textContent(): String = value.removePrefix("<p>").removeSuffix("</p>")
