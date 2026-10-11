package dev.woge.examples.gradient.t07liveupdate

import dev.woge.host.FailureCategory
import dev.woge.host.LiveUseCase
import dev.woge.host.PageIdentity
import dev.woge.host.PageUseCase
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.WogeRegion
import dev.woge.host.WogeRoute
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.liveRefused
import dev.woge.host.liveSubscription
import dev.woge.host.region
import dev.woge.host.regionRefresh
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
import dev.woge.protocol.InteractionSequence
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.TargetRevision
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

@WogeRoute("/announcements")
public data object AnnouncementsInput

/** The safe GET that returns the current announcements region for one rendered page. */
@WogeRoute("/announcements/regions/{epoch}/{target}/{revision}/{interaction}")
public data class AnnouncementsRegionInput(
    val epoch: String,
    val target: String,
    val revision: Long,
    val interaction: Long,
)

@WogeRoute("/announcements/live/{epoch}")
public data class AnnouncementsLiveInput(
    val epoch: String,
)

public class AnnouncementBoard {
    private val identitySecret = RenderIdentitySecret.random()
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
        PageUseCase {
            val epoch = "announcements-${UUID.randomUUID()}"
            val identity = PageIdentity(PageEpoch.of(epoch), identitySecret)
            htmlPage {
                doctype()
                html(attributes = { attribute("lang", "en") }) {
                    head {
                        meta { attribute("charset", "utf-8") }
                        metadata("viewport", "width=device-width, initial-scale=1")
                        metadata("woge-live-url", AnnouncementsLiveRoute.url(AnnouncementsLiveInput(epoch)).value)
                        title("Announcements · Live update")
                        stylesheet(applicationUrl("/assets/live-update/site.css"))
                        moduleScript(applicationUrl("/assets/live-update/app.js"))
                    }
                    body {
                        main {
                            h1 { text("Announcements") }
                            region(AnnouncementListRegion.target(identity), all(), elementName = "section")
                        }
                    }
                }
            }
        }

    /** Region GET used by the live script after an invalidation; it never changes data. */
    public val refresh: PageUseCase<AnnouncementsRegionInput> =
        PageUseCase { request ->
            val input = request.input
            try {
                val target = AnnouncementListRegion.target(PageIdentity(PageEpoch.of(input.epoch), identitySecret))
                if (target.target.region.value != input.target) {
                    failure(FailureCategory.NOT_FOUND, request.context.correlationId)
                } else {
                    regionRefresh(
                        AnnouncementsRoute.url(AnnouncementsInput),
                        target,
                        all(),
                        TargetRevision.of(input.revision),
                        InteractionSequence.of(input.interaction),
                    )
                }
            } catch (_: IllegalArgumentException) {
                failure(FailureCategory.BAD_REQUEST, request.context.correlationId)
            }
        }

    /** The event stream names only the changed region; the browser then calls [refresh]. */
    public val live: LiveUseCase<AnnouncementsLiveInput> =
        LiveUseCase { request ->
            try {
                val identity = PageIdentity(PageEpoch.of(request.input.epoch), identitySecret)
                val target = AnnouncementListRegion.target(identity)
                liveSubscription(listOf(target), changes.map { target })
            } catch (_: IllegalArgumentException) {
                liveRefused(FailureCategory.BAD_REQUEST, request.context.correlationId)
            }
        }
}

@WogeRegion
internal fun HtmlWriter.announcementList(announcements: List<String>) {
    ul { announcements.forEach { li { text(it) } } }
}
