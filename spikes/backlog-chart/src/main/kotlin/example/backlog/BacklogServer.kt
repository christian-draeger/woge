package example.backlog

import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.WogeAction
import dev.woge.host.WogeRegion
import dev.woge.host.actionForm
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.region
import dev.woge.html.HtmlWriter
import dev.woge.html.IslandModule
import dev.woge.html.IslandProps
import dev.woge.html.IslandRegistry
import dev.woge.html.IslandType
import dev.woge.html.applicationUrl
import dev.woge.html.island
import dev.woge.html.islandLoader
import dev.woge.html.islandManifest
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.ktor.KtorPageInput
import dev.woge.ktor.WogeKtorHandlers
import dev.woge.ktor.ktorInput
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.time.LocalDate

private const val DOCUMENT_PATH = "/backlog"
private const val DAYS_PER_SNAPSHOT = 14

internal data class Day(val date: LocalDate, val packing: Int, val dispatch: Int)

internal enum class Snapshot(val wire: String, val label: String, start: LocalDate, seed: Int) {
    EARLY("early", "June 1–14", LocalDate.of(2026, 6, 1), 2),
    LATE("late", "June 15–28", LocalDate.of(2026, 6, 15), 6),
    ;

    val days = List(DAYS_PER_SNAPSHOT) { index ->
        Day(start.plusDays(index.toLong()), 17 + (index * seed) % 19, 12 + (index * 3 + seed) % 15)
    }
}

private val chart =
    IslandType<Snapshot>("backlog-chart", propsVersion = 1) { snapshot ->
        IslandProps(
            mapOf(
                "days" to snapshot.days.joinToString(",") { it.date.toString() },
                "packing" to snapshot.days.joinToString(",") { it.packing.toString() },
                "dispatch" to snapshot.days.joinToString(",") { it.dispatch.toString() },
            ),
        )
    }

private val chartAssets by lazy {
    val version = requireNotNull(Snapshot::class.java.getResourceAsStream("/static/assets/chart/backlog.version"))
        .bufferedReader().use { it.readText() }
    IslandRegistry(IslandModule(chart, "/assets/chart/backlog.js", version))
}

@Serializable
internal data class SelectSnapshot(val window: String)

private fun snapshot(window: String): Snapshot? = Snapshot.entries.firstOrNull { it.wire == window }

@WogeAction("select-backlog-snapshot")
internal suspend fun selectSnapshot(command: SelectSnapshot, context: RequestContext): PageResult {
    val selected = snapshot(command.window) ?: return failure(FailureCategory.BAD_REQUEST, context.correlationId)
    return context.render.updates(applicationUrl("$DOCUMENT_PATH?window=${selected.wire}")) {
        replace(BacklogPanelRegion, selected)
        replace(BacklogStatusRegion, selected.label)
    }
}

private fun document(context: RequestContext, snapshot: Snapshot, reset: Boolean): PageResult =
    htmlPage {
        doctype()
        element("html", { attribute("lang", "en") }) {
            element("head") {
                voidElement("meta") { attribute("charset", "UTF-8") }
                metadata("viewport", "width=device-width, initial-scale=1")
                title("Warehouse backlog")
                context.render.metadata(this)
                stylesheet(applicationUrl("/assets/chart/site.css"))
                islandManifest(chartAssets)
                islandLoader()
                moduleScript(applicationUrl("/assets/woge/index.js"))
            }
            element("body") {
                element("main") {
                    element("h1") { text("Warehouse backlog") }
                    actionForm(SelectSnapshotAction, attributes = {
                        data("woge-action", "")
                        data("woge-status", "backlog-status")
                        data("woge-alert", "backlog-alert")
                        data("woge-failure-message", "The backlog could not be loaded. Use the date range to try again.")
                    }) {
                        element("label", { attribute("for", "backlog-window") }) { text("Date range") }
                        element("select", {
                            attribute("id", "backlog-window")
                            attribute("name", "window")
                        }) {
                            Snapshot.entries.forEach { choice ->
                                element("option", {
                                    attribute("value", choice.wire)
                                    boolean("selected", choice == snapshot)
                                }) { text(choice.label) }
                            }
                        }
                        element("button", { attribute("type", "submit") }) { text("Show backlog") }
                    }
                    region(
                        context.render.target(BacklogStatusRegion),
                        snapshot.label,
                        elementName = "p",
                        attributes = {
                            attribute("id", "backlog-status")
                            attribute("role", "status")
                        },
                    )
                    element("p", {
                        attribute("id", "backlog-alert")
                        attribute("role", "alert")
                    })
                    region(
                        context.render.target(BacklogPanelRegion),
                        snapshot,
                        elementName = "section",
                        attributes = {
                            attribute("id", "backlog-panel")
                            if (reset) data("woge-state", "reset")
                        },
                    )
                }
            }
        }
    }

@WogeRegion
internal fun HtmlWriter.backlogStatus(label: String) {
    text("Showing backlog for $label.")
}

@WogeRegion
internal fun HtmlWriter.backlogPanel(snapshot: Snapshot) {
    element("table", { attribute("id", "daily-backlog") }) {
        element("caption") { text("Open orders by day, ${snapshot.label}") }
        element("thead") {
            element("tr") {
                listOf("Day", "Packing", "Dispatch").forEach { label ->
                    element("th", { attribute("scope", "col") }) { text(label) }
                }
            }
        }
        element("tbody") {
            snapshot.days.forEach { day ->
                element("tr") {
                    element("th", { attribute("scope", "row") }) { text(day.date.toString()) }
                    element("td") { text(day.packing.toString()) }
                    element("td") { text(day.dispatch.toString()) }
                }
            }
        }
    }
    island(chart, snapshot, key = "warehouse-backlog") {
        element("h2") { text("Backlog trend") }
        element("p") {
            element("a", { url("href", applicationUrl("#daily-backlog")) }) { text("Read the daily totals") }
        }
        element("div", {
            boolean("hidden")
            data("chart-controls", "")
        }) {
            element("button", {
                attribute("type", "button")
                attribute("aria-pressed", "true")
                data("packing", "")
            }) { text("Show packing orders") }
            element("button", {
                attribute("type", "button")
                attribute("aria-pressed", "false")
                data("latest", "")
            }) { text("Show last four days") }
            element("canvas", {
                attribute("width", "640")
                attribute("height", "260")
                attribute("role", "img")
                attribute("aria-label", "Packing and dispatch backlog by day")
                attribute("aria-describedby", "backlog-chart-description")
            })
            element("p", { attribute("id", "backlog-chart-description") }) {
                text("Packing has a solid line and dispatch a dashed line. Daily counts are in the table.")
            }
        }
    }
}

private data class BacklogInput(val window: String, val reset: Boolean)

private fun Application.backlog() {
    intercept(ApplicationCallPipeline.Setup) {
        call.response.headers.append(
            "Content-Security-Policy",
            "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; " +
                "base-uri 'none'; form-action 'self'; frame-ancestors 'none'",
        )
    }
    val handlers = WogeKtorHandlers()
    val page = handlers.page(
        PageUseCase<BacklogInput> { request ->
            val selected = snapshot(request.input.window)
                ?: return@PageUseCase failure(FailureCategory.BAD_REQUEST, request.context.correlationId)
            document(request.context, selected, request.input.reset)
        },
        KtorPageInput { call ->
            BacklogInput(call.request.queryParameters["window"] ?: Snapshot.EARLY.wire, call.request.queryParameters["reset"] == "1")
        },
    )
    val select = handlers.action(SelectSnapshotAction, FormDecoder(SelectSnapshot.serializer()).ktorInput())
    routing {
        get(DOCUMENT_PATH) { page.handle(call) }
        post(SelectSnapshotAction.path) { select.handle(call) }
        staticResources("/assets", "static/assets")
    }
}

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, host = "127.0.0.1", port = port, module = Application::backlog).start(wait = true)
}
