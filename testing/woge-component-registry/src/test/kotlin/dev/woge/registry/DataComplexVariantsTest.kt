package dev.woge.registry

import dev.woge.html.renderHtml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DataComplexVariantsTest {
    private val classes = Regex(" class=\"[^\"]*\"")
    private val views =
        listOf(
            peopleView(PeopleInput("x")),
            peopleView(PeopleInput("x", "gra", PeopleSort.ROLE, PeopleOrder.DESC, 2)),
            peopleView(PeopleInput("x", "zzz")),
        ) + DATA_STATES.map { peopleView(PeopleInput("x"), it, "grace".takeIf { _ -> it == "optimistic" }) }

    private fun panel(
        kit: DataKit,
        view: PeopleView,
    ) = renderHtml { with(kit) { panel(view) } }.replace(classes, "")

    @Test
    fun `css and tailwind data panels differ only in presentation classes for every query and state`() {
        views.forEach { view ->
            assertEquals(panel(CssDataKit, view), panel(TailwindDataKit, view))
        }
        assertEquals(
            renderHtml { with(CssDataKit) { complexForm() } }.replace(classes, ""),
            renderHtml { with(TailwindDataKit) { complexForm() } }.replace(classes, ""),
        )
        assertEquals(
            renderHtml { with(CssDataKit) { cards() } }.replace(classes, ""),
            renderHtml { with(TailwindDataKit) { cards() } }.replace(classes, ""),
        )
    }

    @Test
    fun `sort filter and page links come from the typed route and drop the page when the order changes`() {
        val html = panel(CssDataKit, peopleView(PeopleInput("css", "gra", page = 2)))
        assertTrue(html.contains("href=\"/data/css?q=gra&amp;sort=role&amp;dir=asc\""), html)
        assertFalse(html.contains("page=2&amp;sort"), html)
        assertEquals(PeopleRoute.url(PeopleInput("css", sort = PeopleSort.ROLE)).value, "/data/css?sort=role")
    }

    @Test
    fun `a large collection ships one window of rows and clamps hostile page numbers`() {
        val second = peopleView(PeopleInput("css", page = 2))
        assertEquals(listOf("katherine", "linus"), second.rows.map { it.id })
        assertEquals(PEOPLE_PAGE_SIZE, peopleView(PeopleInput("css", page = 1)).rows.size)
        assertEquals(2, peopleView(PeopleInput("css", page = 99)).window.page)
        assertEquals(1, peopleView(PeopleInput("css", page = -4)).window.page)
        assertTrue(peopleView(PeopleInput("css", "zzz")).rows.isEmpty())
    }
}
