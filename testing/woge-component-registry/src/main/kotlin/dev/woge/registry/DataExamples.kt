package dev.woge.registry

import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.renderHtml
import dev.woge.html.stylesheet
import dev.woge.html.title
import java.io.File

internal val DATA_KITS: Map<String, DataKit> = mapOf("css" to CssDataKit, "tailwind" to TailwindDataKit)

internal val DATA_FILTERS: Map<String, String?> = mapOf("all" to null, "gra" to "gra", "zzz" to "zzz")

internal val DATA_STATES: List<String> = listOf("loading", "stale", "partial-error", "optimistic")

/** Static pages for every query a browser test follows, so sort, filter and paging work without a server. */
internal fun writeDataFixtures(output: File) {
    DATA_KITS.forEach { (variant, kit) ->
        dataQueries(variant).forEach { (file, input) ->
            val view = peopleView(input)
            output.resolve("$file.html").writeText(document(kit, variant) { with(kit) { dashboard(view) } })
            output.resolve("$file-region.html").writeText(renderHtml { with(kit) { panel(view) } })
        }
        DATA_STATES.forEach { state ->
            val view = peopleView(PeopleInput(variant), state, pendingId = "grace".takeIf { state == "optimistic" })
            output.resolve("data-$variant-state-$state-region.html").writeText(renderHtml { with(kit) { panel(view) } })
        }
        output.resolve("complex-$variant.html").writeText(document(kit, variant) { with(kit) { complexForm() } })
        output.resolve("cards-$variant.html").writeText(document(kit, variant) { with(kit) { cards() } })
    }
}

private fun dataQueries(variant: String): List<Pair<String, PeopleInput>> =
    DATA_FILTERS.flatMap { (name, q) ->
        PeopleSort.entries.flatMap { sort ->
            PeopleOrder.entries.flatMap { dir ->
                (1..2).map { page ->
                    "data-$variant-$name-${sort.name.lowercase()}-${dir.name.lowercase()}-$page" to
                        PeopleInput(variant, q, sort, dir, page)
                }
            }
        }
    }

private fun document(
    kit: DataKit,
    variant: String,
    body: HtmlWriter.() -> Unit,
): String =
    renderHtml {
        doctype()
        element("html", attributes = { attribute("lang", "en") }) {
            element("head") {
                voidElement("meta", attributes = { attribute("charset", "utf-8") })
                voidElement("meta", attributes = {
                    attribute("name", "viewport")
                    attribute("content", "width=device-width, initial-scale=1")
                })
                title("Data components ($variant)")
                kit.styles.forEach { href -> stylesheet(applicationUrl(href)) }
            }
            element("body") { element("main", content = body) }
        }
    }
