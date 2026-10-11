package dev.woge.examples.gradient.t07liveupdate

import dev.woge.host.LiveRegionPage
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRegion
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.host.liveRegion
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.html.ul
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

@WogeRoute("/announcements")
public data object AnnouncementsInput

public class AnnouncementBoard {
    private val announcements = mutableListOf("Welcome to the garden club")
    private val changes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Called by the rest of the application whenever something new is announced. */
    public fun publish(text: String) {
        synchronized(announcements) { announcements += text }
        changes.tryEmit(Unit)
    }

    private fun all(): List<String> = synchronized(announcements) { announcements.toList() }

    public val page: PageUseCase<AnnouncementsInput> =
        PageUseCase { request ->
            val page = request.context.render
            htmlPage {
                doctype()
                html(attributes = { attribute("lang", "en") }) {
                    head {
                        meta { attribute("charset", "utf-8") }
                        metadata("viewport", "width=device-width, initial-scale=1")
                        page.metadata(this)
                        title("Announcements · Live update")
                        stylesheet(applicationUrl("/assets/live-update/site.css"))
                        moduleScript(applicationUrl("/assets/woge/index.js"))
                    }
                    body {
                        main {
                            h1 { text("Announcements") }
                            liveRegion(
                                page,
                                AnnouncementListRegion,
                                all(),
                                AnnouncementsRoute.url(AnnouncementsInput),
                                elementName = "section",
                            )
                        }
                    }
                }
            }
        }

    public val live: LiveRegionPage<AnnouncementsInput, List<String>> =
        LiveRegionPage(page, AnnouncementListRegion, load = { all() }, changes = { changes })
}

@WogeRegion
internal fun HtmlWriter.announcementList(announcements: List<String>) {
    ul { announcements.forEach { li { text(it) } } }
}
