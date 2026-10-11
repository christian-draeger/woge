package dev.woge.tck

import dev.woge.host.DeferredRegion
import dev.woge.host.PageRegion
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.RenderSessions
import dev.woge.host.RequestContext
import dev.woge.host.RequestMethod
import dev.woge.host.ResponseStatus
import dev.woge.host.deferredRegion
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.regionHtml
import dev.woge.host.regionPlaceholder
import dev.woge.host.withRenderSession
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.noscript
import dev.woge.html.p
import dev.woge.html.title
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch

/** One application-owned HTML GET, sharing its access check and read-only loader with deferred work. */
internal class DeferredFallbackFixture {
    private val sessions = RenderSessions()
    private val forecast = listOf("Saturday: sunny", "Sunday: light rain")
    private val region =
        object : PageRegion<String>("tck.deferred-fallback") {
            override fun render(
                writer: HtmlWriter,
                input: String,
            ) = writer.p { text(input) }
        }

    suspend fun page(request: PageRequest<AdapterTckPageScenario>): PageResult {
        if (!authorized(request.context)) {
            return failure(dev.woge.host.FailureCategory.FORBIDDEN, request.context.correlationId)
        }
        val context = request.context.withRenderSession(sessions.forRequest(request.context.headers))
        val complete = request.input == AdapterTckPageScenario.DEFERRED_COMPLETE
        val value = if (complete) load() else null
        return htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    title("Deferred forecast")
                    context.render.metadata(this)
                }
                body {
                    h1 { text("Forecast") }
                    if (value == null) {
                        regionPlaceholder(declaration(context), elementName = "section")
                    } else {
                        region.render(this, value)
                    }
                }
            }
        }
    }

    fun regions(context: RequestContext): List<DeferredRegion> =
        if (authorized(context)) {
            listOf(declaration(context.withRenderSession(sessions.forRequest(context.headers))))
        } else {
            emptyList()
        }

    private fun declaration(context: RequestContext): DeferredRegion =
        deferredRegion(
            target = context.render.target(region),
            loading = {
                p { text("Loading forecast…") }
                noscript {
                    a(attributes = { url("href", applicationUrl(COMPLETE_PATH)) }) {
                        text("View the complete forecast")
                    }
                }
            },
            onFailure = { regionHtml { p { text("Forecast unavailable") } } },
            content = { load() },
        )

    private fun authorized(context: RequestContext): Boolean = context.header("X-Tck-Subject") == "tck-user"

    private suspend fun load(): String = forecast.joinToString("; ")
}

internal suspend fun AdapterTckHttpClient.verifyDeferredFallback(expect: (Boolean, String, String) -> Unit) {
    val contract = "deferred-native-fallback"
    val authenticated = mapOf("X-Tck-Subject" to "tck-user")
    val shell = open(RequestMethod.GET, SHELL_PATH, authenticated)
    val html = shell.body().use { it.readAllBytes().toString(Charsets.UTF_8) }
    expect(shell.statusCode() == ResponseStatus.OK.code, contract, "shell did not render")
    expect(
        html.contains("<noscript><a href=\"$COMPLETE_PATH\">View the complete forecast</a></noscript>"),
        contract,
        "placeholder lacks native navigation",
    )
    val epoch = Regex("name=\"woge-page-epoch\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
    val target = Regex("data-woge-region=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
    expect(epoch != null && target != null, contract, "shell lacks render identity")
    val ordering = mapOf("Woge-Page-Epoch" to epoch.orEmpty(), "Woge-Region-Revisions" to "$target:0")
    val stream = open(RequestMethod.GET, PATCH_PATH, authenticated + ordering)
    val decoder = PatchStreamV1.decoder()
    val events = stream.body().use { decoder.feed(it.readAllBytes()).also { decoder.finish() } }
    val patches = events.filterIsInstance<PatchStreamEvent.PatchFrame>().map { it.patch as ReplacePatch }
    expect(
        stream.statusCode() == ResponseStatus.OK.code &&
            patches.size == 1 &&
            patches
                .single()
                .target.region.value == target,
        contract,
        "enhanced request did not address its document region",
    )
    expect(
        patches
            .single()
            .html.value
            .contains("Saturday: sunny"),
        contract,
        "enhanced content missing",
    )
    verifyNativeDeferredDocument(authenticated, expect)
    verifyDeferredAccess(ordering, "_woge_epoch=$epoch&_woge_target=$target", expect)
}

private fun AdapterTckHttpClient.verifyNativeDeferredDocument(
    authenticated: Map<String, String>,
    expect: (Boolean, String, String) -> Unit,
) {
    val contract = "deferred-native-fallback"
    var previous: String? = null
    repeat(2) {
        val native = open(RequestMethod.GET, COMPLETE_PATH, authenticated)
        val content = native.body().use { it.readAllBytes().toString(Charsets.UTF_8) }
        expect(
            native.statusCode() == ResponseStatus.OK.code &&
                native
                    .headers()
                    .firstValue(
                        "content-type",
                    ).orElse("")
                    .startsWith("text/html"),
            contract,
            "fallback is not a navigable HTML document",
        )
        expect(
            content.contains("Saturday: sunny") && content.contains("Sunday: light rain"),
            contract,
            "result missing",
        )
        expect(
            !content.contains("Loading") && !content.contains("<script"),
            contract,
            "fallback still needs enhancement",
        )
        val result = Regex("<p>.*?</p>").find(content)?.value
        expect(previous == null || previous == result, contract, "repeated safe GET changed the result")
        previous = result
    }
}

private fun AdapterTckHttpClient.verifyDeferredAccess(
    ordering: Map<String, String>,
    query: String,
    expect: (Boolean, String, String) -> Unit,
) {
    val contract = "deferred-native-fallback"
    for (path in listOf(SHELL_PATH, COMPLETE_PATH)) {
        for (headers in listOf(emptyMap(), mapOf("X-Tck-Subject" to "other-user") + ordering)) {
            val denied = open(RequestMethod.GET, "$path?$query", headers)
            val content = denied.body().use { it.readAllBytes().toString(Charsets.UTF_8) }
            val forbidden = denied.statusCode() == ResponseStatus.FORBIDDEN.code
            expect(
                forbidden && !content.contains("Saturday"),
                contract,
                "HTML access check bypassed",
            )
        }
    }
    for (headers in listOf(ordering, mapOf("X-Tck-Subject" to "other-user") + ordering)) {
        val deniedStream = open(RequestMethod.GET, PATCH_PATH, headers)
        val deniedDecoder = PatchStreamV1.decoder()
        val deniedEvents =
            deniedStream.body().use { deniedDecoder.feed(it.readAllBytes()).also { deniedDecoder.finish() } }
        expect(deniedEvents.none { it is PatchStreamEvent.PatchFrame }, contract, "render identity granted data access")
    }
}

private const val SHELL_PATH = "/woge-tck/pages/deferred-shell"
private const val COMPLETE_PATH = "/woge-tck/pages/deferred-complete"
private const val PATCH_PATH = "/woge-tck/deferred/native-fallback"
