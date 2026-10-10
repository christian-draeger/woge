package dev.woge.examples.m1

import dev.woge.host.CorrelationId
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestTrace
import dev.woge.html.HtmlSink
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class PageEpochTest {
    @Test
    fun `each render creates a new epoch while patches retain the requesting document epoch`() =
        runBlocking {
            val page = ProjectPage()
            val request =
                PageRequest(
                    ProjectInput("woge"),
                    RequestContext(
                        RequestMethod.GET,
                        RequestTrace(RequestId.of("page"), CorrelationId.of("page")),
                    ),
                )

            suspend fun epoch(): UUID {
                val result = page.open(request) as PageResult.Document
                val output = StringBuilder()
                result.frames.collect { it.writeTo(HtmlSink(output::append)) }
                return UUID.fromString(PAGE_EPOCH.find(output)!!.groupValues[1])
            }
            val first = epoch()
            assertNotEquals(first, epoch())
            val decoder = PatchStreamV1.decoder()
            val events = decoder.feed(encodedProjectPatch(ProjectPatchesInput("woge", first)))
            decoder.finish()
            val patch = events.filterIsInstance<PatchStreamEvent.PatchFrame>().single().patch
            assertEquals(first.toString(), patch.target.pageEpoch.value)
        }

    private companion object {
        private val PAGE_EPOCH = Regex("""name="woge-page-epoch" content="([^"]+)"""")
    }
}
