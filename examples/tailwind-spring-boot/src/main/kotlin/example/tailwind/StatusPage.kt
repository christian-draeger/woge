package example.tailwind

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.html.AssetUrls
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
import dev.woge.html.ul

@WogeRoute("/")
public data object StatusInput

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
                    }
                }
            }
        }
}
