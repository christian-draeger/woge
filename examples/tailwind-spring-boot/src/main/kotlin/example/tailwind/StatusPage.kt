package example.tailwind

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.html.AssetUrls
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
import dev.woge.html.p
import dev.woge.html.span
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.html.moduleScript
import dev.woge.html.nav
import dev.woge.html.section
import dev.woge.html.ul
import dev.woge.ui.UiId
import dev.woge.ui.dialogCloseButton
import dev.woge.ui.dialogLink
import dev.woge.ui.disclosure
import dev.woge.ui.liveRegion
import dev.woge.ui.modalDialog
import dev.woge.ui.popoverButton
import dev.woge.ui.popoverPanel

/** `INCIDENT` shows the incident note as a normal page: the fallback of the dialog without JavaScript. */
public enum class StatusView { INCIDENT }

@WogeRoute("/")
public data class StatusInput(
    public val view: StatusView? = null,
)

public enum class Tone { OK, WARNING, DOWN }

/**
 * Tailwind finds class names by reading this file, so every class is written out in full.
 * A `when` picks a complete name; `"bg-$tone-100"` would be invisible to Tailwind and fails the build.
 */
internal fun badgeClasses(tone: Tone): String =
    when (tone) {
        Tone.OK -> "bg-ok-soft text-ok"
        Tone.WARNING -> "bg-warning-soft text-warning"
        Tone.DOWN -> "bg-danger-soft text-danger"
    }

public class StatusPage(
    private val assets: AssetUrls,
) : PageUseCase<StatusInput> {
    private val services = listOf("Website" to Tone.OK, "Search" to Tone.WARNING, "Uploads" to Tone.DOWN)

    override suspend fun open(request: PageRequest<StatusInput>): PageResult =
        htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    title("Service status")
                    stylesheet(assets.url(applicationUrl("/tailwind.css")))
                    stylesheet(assets.url(applicationUrl("/site.css")))
                    moduleScript(assets.url(applicationUrl("/site.js")))
                }
                body(attributes = { classes("bg-surface text-ink font-sans") }) {
                    main(attributes = { classes("mx-auto max-w-xl p-6 grid gap-4") }) {
                        h1(attributes = { classes("text-3xl font-bold tracking-tight") }) { text("Service status") }
                        p(attributes = { classes("text-muted") }) { text("Styled with Tailwind, rendered on the server.") }
                        ul(attributes = { classes("service-list grid gap-2") }) {
                            services.forEach { (name, tone) ->
                                li(attributes = { classes("flex justify-between rounded-lg border border-line p-3") }) {
                                    text(name)
                                    span(attributes = { classes("rounded-full px-2 motion-safe:transition", badgeClasses(tone)) }) {
                                        text(tone.name.lowercase())
                                    }
                                }
                            }
                        }
                        if (request.input.view == StatusView.INCIDENT) {
                            section(attributes = { classes("rounded-lg border border-line p-3") }) { incidentNote() }
                        }
                        nav(attributes = {
                            classes("flex gap-3 items-center")
                            aria("label", "Status tools")
                        }) {
                            popoverButton(legend, attributes = { classes("rounded-lg border border-line px-3 py-1") }) {
                                text("Legend")
                            }
                            popoverPanel(legend, attributes = {
                                classes("m-auto rounded-lg border border-line bg-surface p-4 text-ink shadow-lg")
                            }) {
                                p { text("ok works, warning is slow, down does not work.") }
                            }
                            dialogLink(incident, StatusRoute.url(StatusInput(StatusView.INCIDENT)), attributes = {
                                classes("underline")
                            }) { text("Uploads incident") }
                        }
                        disclosure(
                            summary = { text("How often is this page updated?") },
                            attributes = { classes("rounded-lg border border-line p-3") },
                            summaryAttributes = { classes("cursor-pointer font-semibold") },
                        ) {
                            p(attributes = { classes("text-muted") }) { text("Every time you load it.") }
                        }
                        p(attributes = {
                            classes("text-muted")
                            liveRegion()
                        }) {}
                        modalDialog(incident, title = "Uploads incident", attributes = {
                            classes("m-auto max-w-md rounded-lg border border-line bg-surface p-6 text-ink backdrop:bg-black/50")
                        }, titleAttributes = { classes("text-xl font-bold") }) {
                            incidentNote()
                            dialogCloseButton(attributes = { classes("mt-4 rounded-lg border border-line px-3 py-1") }) {
                                text("Close")
                            }
                        }
                    }
                }
            }
        }

    private fun HtmlWriter.incidentNote() {
        p { text("Uploads are paused while storage is moved. Files you already uploaded are safe.") }
    }

    private companion object {
        val legend = UiId("legend")
        val incident = UiId("incident")
    }
}
