package dev.woge.runtime

import dev.woge.host.DeferredDocument
import dev.woge.host.DeferredRegion
import dev.woge.host.PageResult
import dev.woge.host.PatchStreamLimits
import dev.woge.host.RequestTrace
import dev.woge.host.ResponseHeaders
import dev.woge.host.ResponseMetadata
import dev.woge.host.WogeObserver
import dev.woge.host.httpHeader
import dev.woge.host.regionPlaceholder
import dev.woge.protocol.HtmlFrame
import dev.woge.protocol.InitialDocumentRange
import dev.woge.protocol.htmlFrame
import dev.woge.protocol.initialDocumentEnd
import dev.woge.protocol.initialDocumentStart
import dev.woge.protocol.regionLoadingFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Opt-in initial-document policy. Forced modes remain experimental test overrides. */
public data class InitialDocumentPolicy(
    public val mode: NativeDpuMode = NativeDpuMode.OFF,
    public val deferred: DeferredRegionPolicy = DeferredRegionPolicy(),
    public val patches: PatchStreamLimits = PatchStreamLimits(),
)

/** Reuses page handlers and their scoped HTML budgets, cancellation and response observation. */
public class InitialDocumentRuntime(
    private val policy: InitialDocumentPolicy = InitialDocumentPolicy(),
    private val observer: WogeObserver = WogeObserver.NONE,
) {
    private val executor = DeferredRegionExecutor(policy.deferred, observer)

    /** Admits the region set before commitment. Cookie contents are never retained in diagnostics. */
    public fun prepare(
        result: PageResult,
        cookieHeader: String?,
        requestTrace: RequestTrace? = null,
    ): PageResult {
        if (result !is PageResult.Document || result.deferredDocument == null) return result
        val document = requireNotNull(result.deferredDocument)
        val regions = executor.prepare(document.regions, requestTrace)
        val native =
            when (policy.mode) {
                NativeDpuMode.OFF, NativeDpuMode.FORCE_FALLBACK -> false
                NativeDpuMode.AUTO -> NativeDpuEncoder.hasCapabilityCookie(cookieHeader)
                NativeDpuMode.FORCE_NATIVE -> true
            }
        return PageResult.Document(
            documentMetadata(result.metadata),
            frames(document, regions, native, requestTrace),
            result.maxBytes,
        )
    }

    private fun frames(
        document: DeferredDocument,
        regions: List<DeferredRegion>,
        native: Boolean,
        trace: RequestTrace?,
    ): Flow<HtmlFrame> =
        flow {
            emit(initialDocumentStart(document.head))
            emit(document.shell)
            val ranges = mutableMapOf<DeferredRegion, InitialDocumentRange>()
            regions.forEach { region ->
                val loading = if (native) region.loadingHtml() else null
                val range =
                    if (loading != null) {
                        InitialDocumentRange.create(region.target, region.initialRevision, loading)
                    } else {
                        null
                    }
                if (range == null) {
                    emit(
                        if (loading == null) {
                            htmlFrame { regionPlaceholder(region) }
                        } else {
                            regionLoadingFrame(region.target, region.initialRevision, loading)
                        },
                    )
                } else {
                    ranges[region] = range
                    emit(range.startFrame(requireNotNull(loading)))
                }
            }
            emit(document.tail)
            InitialDocumentUpdates(policy.patches, observer, trace)
                .frames(executor.execute(regions, trace), ranges)
                .collect { emit(it) }
            emit(initialDocumentEnd())
        }
}

private fun documentMetadata(metadata: ResponseMetadata): ResponseMetadata {
    val retained = metadata.headers.filter { it.name.value !in setOf("cache-control", "vary", "etag", "last-modified") }
    val vary = metadata.headers.filter { it.name.value == "vary" }.flatMap { it.value.value.split(',') }
    val fields = (vary.map(String::trim) + "Cookie").distinctBy { it.lowercase(java.util.Locale.ROOT) }
    return ResponseMetadata(
        metadata.status,
        metadata.contentType,
        ResponseHeaders.of(
            retained + httpHeader("Cache-Control", "no-store") + httpHeader("Vary", fields.joinToString(", ")),
        ),
        metadata.cookies,
    )
}
