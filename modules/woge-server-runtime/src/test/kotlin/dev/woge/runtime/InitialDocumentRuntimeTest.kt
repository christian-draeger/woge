package dev.woge.runtime

import dev.woge.host.PageResult
import dev.woge.host.PatchStreamLimits
import dev.woge.host.deferredHtmlPage
import dev.woge.host.deferredRegion
import dev.woge.host.writeTo
import dev.woge.html.BufferedHtmlSink
import dev.woge.html.HtmlByteLimitException
import dev.woge.html.HtmlSink
import dev.woge.html.UnsafeWogeHtmlApi
import dev.woge.html.unsafeHtml
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchHtml
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.patchHtml
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class InitialDocumentRuntimeTest {
    @Test
    fun `all selection modes preserve useful shell and defer work until collection`() =
        runBlocking {
            for (mode in NativeDpuMode.entries) {
                for (cookie in listOf(null, "woge_dpu=0", "woge_dpu=1", "woge_dpu=1; woge_dpu=1")) {
                    var started = false
                    val result =
                        page {
                            started = true
                            html("Ready")
                        }
                    val prepared = runtime(mode).prepare(result, cookie) as PageResult.Document
                    assertFalse(started)
                    val output = BufferedHtmlSink()
                    prepared.writeTo(output)
                    val markup = output.content()
                    val native =
                        mode == NativeDpuMode.FORCE_NATIVE || mode == NativeDpuMode.AUTO && cookie == "woge_dpu=1"
                    assertEquals(native, markup.contains("<?start"))
                    assertEquals(native, markup.contains("<template for="))
                    assertTrue(markup.startsWith("<!doctype html><html><head>"))
                    assertTrue(markup.endsWith("</body></html>"))
                    assertTrue(markup.contains("Loading"))
                    assertTrue(started)
                    assertEquals(
                        "no-store",
                        prepared.metadata.headers
                            .single { it.name.value == "cache-control" }
                            .value.value,
                    )
                    assertEquals(
                        "Cookie",
                        prepared.metadata.headers
                            .single { it.name.value == "vary" }
                            .value.value,
                    )
                }
            }
        }

    @Test
    fun `shell flushes before gated work and cancelling transport cancels work`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val shell = CompletableDeferred<Unit>()
            val prepared =
                runtime(NativeDpuMode.FORCE_NATIVE).prepare(
                    page {
                        started.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled.complete(Unit)
                        }
                    },
                    null,
                ) as PageResult.Document
            val job =
                launch {
                    prepared.writeTo(HtmlSink { if (it.contains("Shell")) shell.complete(Unit) })
                }
            withTimeout(5000) {
                shell.await()
                started.await()
            }
            job.cancelAndJoin()
            assertTrue(cancelled.isCompleted)
        }

    @OptIn(UnsafeWogeHtmlApi::class)
    @Test
    fun `late rejected replacement is framed and never native even in forced mode`() =
        runBlocking {
            val output = BufferedHtmlSink()
            val result = page { patchHtml { raw(unsafeHtml("<p>Safe but explicitly raw</p>")) } }
            (runtime(NativeDpuMode.FORCE_NATIVE).prepare(result, null) as PageResult.Document).writeTo(output)
            val markup = output.content()
            assertTrue(markup.contains("<?start"))
            assertFalse(markup.contains("<template for="))
            val encoded =
                Regex("<template data-woge-dpu-frame=\"chunk\">([^<]+)</template>")
                    .findAll(markup)
                    .map { Base64.getDecoder().decode(it.groupValues[1]) }
                    .toList()
            assertTrue(encoded.any { it.toString(Charsets.UTF_8).contains("Safe but explicitly raw") })
        }

    @Test
    fun `region admission and page byte limits survive native transport`() {
        val result = page { html("Ready") }
        assertThrows(HtmlByteLimitException::class.java) {
            runBlocking {
                val limited = PageResult.Document(result.metadata, result.frames, 8, result.deferredDocument)
                (runtime(NativeDpuMode.FORCE_NATIVE).prepare(limited, null) as PageResult.Document)
                    .writeTo(BufferedHtmlSink())
            }
        }
        val tooMany =
            deferredHtmlPage(
                regions = listOf(region("one") { html("1") }, region("two") { html("2") }),
                shell = {},
            )
        assertThrows(DeferredRegionLimitException::class.java) {
            InitialDocumentRuntime(InitialDocumentPolicy(deferred = DeferredRegionPolicy(maxRegions = 1)))
                .prepare(tooMany, null)
        }
    }

    @Test
    fun `native updates consume the same patch count and byte budgets`() {
        val result =
            deferredHtmlPage(
                listOf(region("one") { html("1") }, region("two") { html("2") }),
                shell = {},
            )
        val limited =
            InitialDocumentRuntime(
                InitialDocumentPolicy(NativeDpuMode.FORCE_NATIVE, patches = PatchStreamLimits(maxPatches = 1)),
            )
        assertThrows(dev.woge.host.ResourceLimitException::class.java) {
            runBlocking {
                (limited.prepare(result, null) as PageResult.Document).writeTo(BufferedHtmlSink())
            }
        }
        val byteLimited =
            InitialDocumentRuntime(
                InitialDocumentPolicy(NativeDpuMode.FORCE_NATIVE, patches = PatchStreamLimits(maxBytes = 8)),
            )
        assertThrows(dev.woge.host.ResourceLimitException::class.java) {
            runBlocking {
                (
                    byteLimited.prepare(
                        page { html("Ready") },
                        null,
                    ) as PageResult.Document
                ).writeTo(BufferedHtmlSink())
            }
        }
    }

    private fun runtime(mode: NativeDpuMode): InitialDocumentRuntime =
        InitialDocumentRuntime(InitialDocumentPolicy(mode))

    private fun page(content: suspend () -> PatchHtml): PageResult.Document =
        deferredHtmlPage(listOf(region("region", content)), shell = { element("h1") { text("Shell") } })

    private fun region(
        id: String,
        content: suspend () -> PatchHtml,
    ): dev.woge.host.DeferredRegion =
        deferredRegion(
            PatchTarget(PageEpoch.of("page"), RegionTargetId.of(id)),
            loading = { element("p") { text("Loading") } },
            onFailure = { html("Unavailable") },
            content = content,
        )

    private fun html(value: String): PatchHtml = patchHtml { element("p") { text(value) } }
}
