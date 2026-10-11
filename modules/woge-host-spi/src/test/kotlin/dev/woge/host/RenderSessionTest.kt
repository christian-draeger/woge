package dev.woge.host

import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.renderHtml
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RenderSessionTest {
    private val sessions = RenderSessions(RenderIdentitySecret.of(ByteArray(32)))
    private val region =
        object : PageRegion<String>("example.summary") {
            override fun render(
                writer: HtmlWriter,
                input: String,
            ) {
                writer.text(input)
            }
        }

    private fun context() =
        RequestContext(
            RequestMethod.GET,
            RequestTrace(RequestId.of("request"), CorrelationId.of("correlation")),
        )

    @Test
    fun `fresh documents have different epochs and resumed targets are stable`() {
        val first = sessions.create()
        val target = first.target(region).target
        assertNotEquals(first.epoch, sessions.create().epoch)
        assertEquals(target, first.resume(first.epoch).target(region).target)
        assertNotEquals(
            target,
            RenderSessions()
                .create()
                .resume(first.epoch)
                .target(region)
                .target,
        )
    }

    @Test
    fun `ordering headers produce typed replacements without command fields`() {
        val page = sessions.create()
        val target = page.target(region).target
        val session =
            sessions.forRequest(
                RequestHeaders.of(
                    httpHeader("Woge-Page-Epoch", page.epoch),
                    httpHeader("Woge-Region-Revisions", "${target.region.value}:12"),
                    httpHeader("Woge-Interaction", "7"),
                ),
            )
        val patch = session.updates(applicationUrl("/page")) { replace(region, "<updated>") }.patches.single()
        assertEquals(target, patch.target)
        assertEquals(12L, patch.revision.base.value)
        assertEquals(13L, patch.revision.next.value)
        assertEquals(7L, patch.interactionSequence.value)
        assertEquals("&lt;updated&gt;", patch.html.value)
    }

    @Test
    fun `malformed ordering is rejected before application execution`() =
        runBlocking {
            val context = context()
            val malformed =
                RequestContext(
                    context.method,
                    context.trace,
                    headers =
                        RequestHeaders.of(
                            httpHeader("Woge-Page-Epoch", "page"),
                            httpHeader("Woge-Region-Revisions", "invalid"),
                        ),
                )
            val useCase = sessions.page(PageUseCase<Unit> { error("Application must not execute") })
            assertEquals(ResponseStatus.BAD_REQUEST, useCase.open(PageRequest(Unit, malformed)).metadata.status)
            assertThrows(RenderSessionException::class.java) {
                sessions.forRequest(
                    RequestHeaders.of(httpHeader("Woge-Page-Epoch", "a"), httpHeader("Woge-Page-Epoch", "b")),
                )
            }
        }

    @Test
    fun `duplicate and exhausted revisions fail before mutation`() =
        runBlocking {
            val target =
                sessions
                    .create()
                    .target(region)
                    .target.region.value
            for (entries in listOf("$target:0,$target:1", "$target:${Long.MAX_VALUE}")) {
                val context = context()
                val malformed =
                    RequestContext(
                        context.method,
                        context.trace,
                        headers =
                            RequestHeaders.of(
                                httpHeader("Woge-Page-Epoch", "page"),
                                httpHeader("Woge-Region-Revisions", entries),
                            ),
                    )
                val useCase = sessions.page(PageUseCase<Unit> { error("Mutation must not execute") })
                assertEquals(ResponseStatus.BAD_REQUEST, useCase.open(PageRequest(Unit, malformed)).metadata.status)
            }
        }

    @Test
    fun `live page authorizes document refresh and reconnect before loading`() =
        runBlocking {
            var authorizations = 0
            val live =
                LiveRegionPage(
                    PageUseCase<Unit> { error("Refused document must not execute") },
                    region,
                    load = { error("Refused refresh must not load") },
                    changes = { emptyFlow() },
                    authorize = {
                        authorizations++
                        failure(FailureCategory.FORBIDDEN, it.context.correlationId)
                    },
                )
            val request = PageRequest(Unit, context())
            assertEquals(ResponseStatus.FORBIDDEN, live.document(request).metadata.status)
            assertEquals(ResponseStatus.FORBIDDEN, live.refresh(request, TestRoute, emptyMap()).metadata.status)
            assertEquals(
                ResponseStatus.FORBIDDEN,
                (live.subscribe(request, "page") as LiveResult.Refused).failure.metadata.status,
            )
            assertEquals(3, authorizations)
        }

    @Test
    fun `live markup declares one page URL and escapes typed content`() {
        val session = sessions.create()
        val html = renderHtml { liveRegion(session, region, "<initial>", applicationUrl("/page")) }
        org.junit.jupiter.api.Assertions
            .assertTrue(html.contains("data-woge-live-url=\"/page\""))
        org.junit.jupiter.api.Assertions
            .assertTrue(html.contains("&lt;initial&gt;"))
    }
}

private object TestRoute : PageRoute<Unit>("/page") {
    override fun url(input: Unit) = applicationUrl("/page")

    override fun decode(parameters: RouteParameters) = Unit
}
