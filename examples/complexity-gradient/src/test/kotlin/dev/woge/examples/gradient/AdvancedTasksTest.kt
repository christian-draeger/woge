package dev.woge.examples.gradient

import dev.woge.examples.gradient.t06deferredregion.ForecastInput
import dev.woge.examples.gradient.t06deferredregion.ForecastPage
import dev.woge.examples.gradient.t06deferredregion.ForecastPatchesInput
import dev.woge.examples.gradient.t07liveupdate.AnnouncementBoard
import dev.woge.examples.gradient.t07liveupdate.AnnouncementsInput
import dev.woge.examples.gradient.t07liveupdate.AnnouncementsLiveInput
import dev.woge.examples.gradient.t08customjavascript.ComposeInput
import dev.woge.examples.gradient.t08customjavascript.ComposePage
import dev.woge.examples.gradient.t09island.SketchInput
import dev.woge.examples.gradient.t09island.SketchPage
import dev.woge.host.LiveResult
import dev.woge.host.PageRequest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AdvancedTasksTest {
    @Test
    fun `deferred page shows loading html and streams the region`() =
        runTest {
            val page = ForecastPage()
            val html = page.open(PageRequest(ForecastInput, getContext)).html()
            assertTrue(html.contains("Loading the forecast"))
            val region = page.regions(PageRequest(ForecastPatchesInput(UUID.randomUUID()), getContext)).single()
            assertTrue(region.renderContent().value.contains("Saturday"))
        }

    @Test
    fun `live route returns a subscription and the page announces the live url`() =
        runTest {
            val board = AnnouncementBoard()
            val html = board.page.open(PageRequest(AnnouncementsInput, getContext)).html()
            assertTrue(html.contains("woge-live-url"))
            val result = board.live.subscribe(PageRequest(AnnouncementsLiveInput("page-1"), getContext))
            assertInstanceOf(LiveResult.Subscription::class.java, result)
        }

    @Test
    fun `custom javascript page works without the script`() =
        runTest {
            val html = ComposePage().open(PageRequest(ComposeInput("Hello"), getContext)).html()
            assertTrue(html.contains("""type="module""""))
            assertTrue(html.contains("<form"))
            assertTrue(html.contains("Preview: Hello"))
        }

    @Test
    fun `island page marks a keyed island and keeps server text`() =
        runTest {
            val html = SketchPage().open(PageRequest(SketchInput, getContext)).html()
            assertTrue(html.contains("data-woge-island"))
            assertTrue(html.contains("data-woge-state-key=\"tally\""))
            assertTrue(html.contains("works without JavaScript"))
        }
}
