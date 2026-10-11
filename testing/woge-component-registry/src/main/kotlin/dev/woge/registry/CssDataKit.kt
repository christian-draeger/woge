package dev.woge.registry

import dev.woge.host.ActionDescriptor
import dev.woge.host.ActionId
import dev.woge.host.FormDecoder
import dev.woge.host.FormElementId
import dev.woge.host.FormErrors
import dev.woge.host.FormValues
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.RouteValues
import dev.woge.host.htmlPage
import dev.woge.html.HtmlWriter
import dev.woge.html.label
import dev.woge.html.p
import dev.woge.registry.css.BadgeTone
import dev.woge.registry.css.ComboboxSuggestion
import dev.woge.registry.css.DataColumn
import dev.woge.registry.css.DataItem
import dev.woge.registry.css.DataLink
import dev.woge.registry.css.DataRow
import dev.woge.registry.css.DataState
import dev.woge.registry.css.FormChoice
import dev.woge.registry.css.FormControl
import dev.woge.registry.css.NavigationLink
import dev.woge.registry.css.RowSelection
import dev.woge.registry.css.SortDirection
import dev.woge.registry.css.avatarComponent
import dev.woge.registry.css.badgeComponent
import dev.woge.registry.css.cardComponent
import dev.woge.registry.css.cardGrid
import dev.woge.registry.css.dataPanel
import dev.woge.registry.css.dataRegion
import dev.woge.registry.css.dataTable
import dev.woge.registry.css.emptyState
import dev.woge.registry.css.filterForm
import dev.woge.registry.css.formCheckboxGroup
import dev.woge.registry.css.formCombobox
import dev.woge.registry.css.formDateInput
import dev.woge.registry.css.formInput
import dev.woge.registry.css.formLayout
import dev.woge.registry.css.formSelect
import dev.woge.registry.css.pagination
import dev.woge.ui.UiId
import kotlinx.serialization.Serializable
import java.time.LocalDate

internal object CssDataKit : DataKit {
    override val styles = listOf("/component-form.css", "/component-data.css", "/component-complex.css")

    override fun HtmlWriter.dashboard(view: PeopleView) {
        dataRegion(UiId("people"), "People") { panel(view) }
    }

    override fun HtmlWriter.panel(view: PeopleView) {
        val here = PeopleRoute.url(view.input)
        val notice = notice(view.state)
        val retry = DataLink("Refresh", here).takeIf { view.state in listOf("stale", "partial-error") }
        dataPanel(DataState.entries.first { it.html == view.state }, notice, retry) {
            filterForm(
                "Filter people",
                PeopleRoute.url(PeopleInput(view.input.variant)),
                "Filter",
                mapOf("sort" to RouteValues.format(view.sort), "dir" to RouteValues.format(view.dir)),
                DataLink("Clear", PeopleRoute.url(PeopleInput(view.input.variant))),
            ) {
                val field = filterDecoder.field(Filter::q, FormElementId.of("q"))
                val errors = FormErrors<Filter>(FormValues.of(mapOf("q" to listOfNotNull(view.input.q))), emptyList())
                label(attributes = { attribute("for", "q") }) { text("Name") }
                formInput(FormControl(field, errors))
            }
            if (view.rows.isEmpty()) {
                emptyState("No people found", "Try a shorter name.")
            } else {
                formLayout(archive) { people(view) }
            }
        }
    }

    private fun HtmlWriter.people(view: PeopleView) {
        val field = selectionDecoder.field(Selection::ids, FormElementId.of("ids"))

        fun column(
            sort: PeopleSort,
            label: String,
        ) = DataColumn(
            sort.name.lowercase(),
            label,
            PeopleRoute.url(sortedBy(view, sort)),
            if (view.sort !=
                sort
            ) {
                null
            } else if (view.dir == PeopleOrder.ASC) {
                SortDirection.ASCENDING
            } else {
                SortDirection.DESCENDING
            },
        )
        dataTable(
            UiId("people-table"),
            "People",
            listOf(column(PeopleSort.NAME, "Name"), column(PeopleSort.ROLE, "Role")),
            view.rows.map { person ->
                val cells = listOf<HtmlWriter.() -> Unit>({ text(person.name) }, { role(view, person) })
                DataRow(person.id, person.name, cells, pending = person.id == view.pendingId)
            },
            RowSelection(field.name, "Select") { name -> "Select $name" },
        )
        p { text("Showing ${view.rows.size} of ${view.window.total} people.") }
        pagination(
            "Pages",
            view.window.pages().map { page ->
                val href = PeopleRoute.url(view.input.copy(page = page))
                NavigationLink("$page", href, current = page == view.window.page)
            },
        )
    }

    private fun HtmlWriter.role(
        view: PeopleView,
        person: Person,
    ) {
        if (view.state == "partial-error") text("Unavailable") else badgeComponent(person.role, BadgeTone.NEUTRAL)
    }

    override fun HtmlWriter.complexForm() {
        val decoder = FormDecoder(Trip.serializer())
        val values = FormValues.of(mapOf("tags" to listOf("html"), "roles" to listOf("admin")))
        val errors = FormErrors<Trip>(values, emptyList())
        formLayout(book) {
            label(attributes = { attribute("for", "city") }) { text("City") }
            formCombobox(
                FormControl(decoder.field(Trip::city, FormElementId.of("city")), errors),
                listOf(ComboboxSuggestion("Köln"), ComboboxSuggestion("Bonn", "Bonn (NRW)")),
            )
            label(attributes = { attribute("for", "day") }) { text("Date") }
            formDateInput(
                FormControl(decoder.field(Trip::day, FormElementId.of("day")), errors),
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-12-31"),
            )
            formCheckboxGroup(
                FormControl(decoder.field(Trip::tags, FormElementId.of("tags")), errors),
                "Topics",
                listOf(
                    FormChoice("html", "HTML", FormElementId.of("tag-html")),
                    FormChoice("css", "CSS", FormElementId.of("tag-css")),
                    FormChoice("kotlin", "كوتلن", FormElementId.of("tag-kotlin")),
                ),
            )
            label(attributes = { attribute("for", "roles") }) { text("Roles") }
            formSelect(
                FormControl(decoder.field(Trip::roles, FormElementId.of("roles")), errors),
                listOf(
                    FormChoice("admin", "Admin", FormElementId.of("role-admin")),
                    FormChoice("user", "User", FormElementId.of("role-user")),
                ),
                multiple = true,
            )
        }
    }

    override fun HtmlWriter.cards() {
        cardGrid(
            "Team",
            PEOPLE.map { person ->
                DataItem(person.id) {
                    cardComponent(person.name) {
                        avatarComponent(person.name)
                        badgeComponent(person.role)
                    }
                }
            },
        )
        emptyState("Nothing here", "Add a person to get started.")
    }

    private fun sortedBy(
        view: PeopleView,
        sort: PeopleSort,
    ): PeopleInput {
        val flip = view.sort == sort && view.dir == PeopleOrder.ASC
        return view.input.copy(sort = sort, dir = if (flip) PeopleOrder.DESC else PeopleOrder.ASC, page = null)
    }

    private val filterDecoder = FormDecoder(Filter.serializer())
    private val selectionDecoder = FormDecoder(Selection.serializer())
    private val archive =
        object : ActionDescriptor<Selection>(ActionId.of("archive-people")) {
            override suspend fun execute(request: PageRequest<Selection>): PageResult = htmlPage { text("Archived") }
        }
    private val book =
        object : ActionDescriptor<Trip>(ActionId.of("book-trip")) {
            override suspend fun execute(request: PageRequest<Trip>): PageResult = htmlPage { text("Booked") }
        }

    @Serializable
    private data class Filter(
        val q: String = "",
    )

    @Serializable
    private data class Selection(
        val ids: List<String> = emptyList(),
    )

    @Serializable
    private data class Trip(
        val city: String = "",
        val day: String = "",
        val tags: List<String> = emptyList(),
        val roles: List<String> = emptyList(),
    )
}
