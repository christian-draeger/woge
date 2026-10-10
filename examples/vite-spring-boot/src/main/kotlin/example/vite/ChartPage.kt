package example.vite

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.p
import dev.woge.html.title
import dev.woge.html.ul
import dev.woge.vite.ViteAssets
import dev.woge.vite.ViteEntry
import dev.woge.vite.viteEntry

@WogeRoute("/")
public data class ChartInput(
    public val unused: String? = null,
)

/**
 * The server renders the data as a normal list, so the page works without JavaScript.
 * `main.ts` (built by Vite) turns the list into a small bar chart when the button is pressed.
 */
public class ChartPage(
    private val vite: ViteAssets,
) : PageUseCase<ChartInput> {
    private val visits = listOf("Mon" to 12, "Tue" to 30, "Wed" to 18, "Thu" to 26, "Fri" to 9)

    override suspend fun open(request: PageRequest<ChartInput>): PageResult =
        htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    title("Visits")
                    viteEntry(vite, ViteEntry("main.ts"))
                }
                body {
                    main {
                        h1 { text("Visits this week") }
                        ul(attributes = { attribute("id", "visits") }) {
                            visits.forEach { (day, count) ->
                                li(attributes = { attribute("data-count", count.toString()) }) { text("$day: $count") }
                            }
                        }
                        button(attributes = {
                            attribute("type", "button")
                            attribute("id", "show-chart")
                        }) { text("Show as chart") }
                        p(attributes = { attribute("id", "status") }) {}
                    }
                }
            }
        }
}
