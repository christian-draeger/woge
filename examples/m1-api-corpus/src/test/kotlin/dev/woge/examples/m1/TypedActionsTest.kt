package dev.woge.examples.m1

import dev.woge.host.CorrelationId
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestTrace
import dev.woge.host.ResponseStatus
import dev.woge.host.htmlPage
import dev.woge.host.writeTo
import dev.woge.html.BufferedHtmlSink
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TypedActionsTest {
    @Test
    fun `compiled generated action executes and renders an ordinary native form`() =
        runBlocking {
            val context =
                RequestContext(
                    RequestMethod.POST,
                    RequestTrace(RequestId.of("request"), CorrelationId.of("trace")),
                )
            assertEquals(
                ResponseStatus.SEE_OTHER,
                executeProjectAction(OpenProject("woge"), context).metadata.status,
            )
            assertEquals(
                ResponseStatus.BAD_REQUEST,
                executeProjectAction(OpenProject(" "), context).metadata.status,
            )
            assertSame(OpenProjectAction, wogeActions.find(OpenProjectAction.id))
            val sink = BufferedHtmlSink()
            htmlPage { projectForm() }.writeTo(sink)
            assertEquals(
                """<form method="post" action="/woge-actions/open-project">""" +
                    """<button name="project" value="woge">Open project</button></form>""",
                sink.content(),
            )
        }
}
