package dev.woge.tck

import com.sun.management.ThreadMXBean
import dev.woge.host.PageResult
import dev.woge.host.htmlPage
import dev.woge.html.HtmlWriter
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentLinkedQueue

internal class PerformanceRenderFixture {
    private val allocations = ConcurrentLinkedQueue<Long>()
    private val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    init {
        check(bean.isThreadAllocatedMemorySupported) { "Performance gate requires HotSpot thread allocation counters" }
        bean.isThreadAllocatedMemoryEnabled = true
    }

    fun page(): PageResult =
        htmlPage {
            val thread = Thread.currentThread().threadId()
            val before = bean.getThreadAllocatedBytes(thread)
            renderDashboard()
            allocations += bean.getThreadAllocatedBytes(thread) - before
        }

    fun drain(): List<Long> = buildList { while (allocations.isNotEmpty()) add(allocations.remove()) }
}

private fun HtmlWriter.renderDashboard() {
    element("main") {
        element("h1") { text("Project operations") }
        repeat(FIXTURE_REGIONS) { region ->
            element("section", { attribute("data-woge-region", "service-$region") }) {
                element("h2") { text("Service $region") }
                element("table") {
                    element("tbody") {
                        repeat(FIXTURE_ROWS_PER_REGION) { row ->
                            element("tr") {
                                element("td") { text("Deployment $row <ready> & healthy") }
                                element("td") { element("button") { text("Inspect") } }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val FIXTURE_REGIONS = 6
private const val FIXTURE_ROWS_PER_REGION = 17
