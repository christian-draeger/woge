// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.applicationUrl
import dev.woge.html.renderHtml
import dev.woge.ui.UiId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DataDisplayComponentsTest {
    private val columns =
        listOf(
            DataColumn(
                "name",
                "الاسم <&>",
                applicationUrl("/people?sort=name&dir=descending"),
                SortDirection.ASCENDING,
            ),
            DataColumn("score", "Score", numeric = true),
        )
    private val rows =
        listOf(
            DataRow("ada", "Ada <&>", listOf({ text("Ada <&>") }, { text("42") })),
            DataRow("bob", "Bob", listOf({ text("Bob") }, { text("7") }), selected = true, pending = true),
        )

    @Test
    fun `cards badges avatars and empty states use plain text and native elements`() {
        val html =
            renderHtml {
                cardComponent("Project <&>") { text("Body") }
                badgeComponent("Late", BadgeTone.DANGER)
                avatarComponent("Ada Lovelace")
                avatarComponent("Ada", applicationUrl("/ada.png"))
                avatarComponent("Ada", decorative = true)
                emptyState("Nothing here", "Try another search.") { text("Clear") }
            }
        listOf(
            "<article",
            "<h3",
            "Project &lt;&amp;&gt;",
            "data-tone=\"danger\"",
            ">Late</span>",
            "role=\"img\" aria-label=\"Ada Lovelace\"",
            ">AL</span>",
            "alt=\"Ada\"",
            "aria-hidden=\"true\"",
            "<h2>Nothing here</h2>",
            "<p>Try another search.</p>",
        ).forEach { assertTrue(html.contains(it), it) }
        assertFalse(html.contains("role=\"status\"") || html.contains("role=\"alert\"") || html.contains("<script"))
        assertThrows(IllegalArgumentException::class.java) { renderHtml { cardComponent("", content = {}) } }
        assertThrows(IllegalArgumentException::class.java) { renderHtml { cardComponent("A", 1) {} } }
    }

    @Test
    fun `table keeps native semantics with one sorted column and keyed rows`() {
        val selection = RowSelection("ids", "Select") { "Select $it" }
        val html = renderHtml { dataTable(UiId("people"), "People", columns, rows, selection) }
        listOf(
            "role=\"region\"",
            "tabindex=\"0\"",
            "<caption>People</caption>",
            "<th scope=\"col\" aria-sort=\"ascending\">",
            "href=\"/people?sort=name&amp;dir=descending\"",
            "data-woge-state-key=\"people-sort-name\"",
            "<th scope=\"row\">Ada &lt;&amp;&gt;</th>",
            "data-row=\"ada\"",
            "data-row=\"bob\"",
            "data-woge-state-key=\"people-select-bob\"",
            "aria-label=\"Select Ada &lt;&amp;&gt;\"",
            "name=\"ids\" value=\"bob\"",
            " checked",
            "aria-busy=\"true\"",
            "data-numeric=\"true\"",
        ).forEach { assertTrue(html.contains(it), it) }
        assertEquals(1, Regex(" aria-sort=\"[a-z]+\"").findAll(html).count())
        assertFalse(html.contains("aria-selected") || html.contains("<script"))
    }

    @Test
    fun `table rejects ambiguous structure`() {
        fun table(
            cols: List<DataColumn> = columns,
            data: List<DataRow> = rows,
        ) = renderHtml { dataTable(UiId("people"), "People", cols, data) }
        assertThrows(IllegalArgumentException::class.java) { table(data = rows + rows.first()) }
        assertThrows(IllegalArgumentException::class.java) { table(data = listOf(DataRow("a", "A", emptyList()))) }
        assertThrows(IllegalArgumentException::class.java) { table(columns + columns.first()) }
        assertThrows(IllegalArgumentException::class.java) {
            table(listOf(DataColumn("a", "A", sorted = SortDirection.ASCENDING)), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            table(data = listOf(DataRow("bad key", "A", listOf({}, {}))))
        }
    }

    @Test
    fun `every data state has explicit visible semantics`() {
        val link = DataLink("Retry", applicationUrl("/people"))
        val ready = renderHtml { dataPanel { text("Rows") } }
        assertTrue(ready.contains("data-state=\"ready\"") && !ready.contains("aria-busy"))
        for (state in DataState.entries.filter { it != DataState.READY }) {
            val html = renderHtml { dataPanel(state, "Notice <&>") { text("Rows") } }
            assertTrue(html.contains("data-state=\"${state.html}\"") && html.contains("Notice &lt;&amp;&gt;"))
            assertEquals(state == DataState.LOADING, html.contains("aria-busy=\"true\""))
            assertFalse(html.contains("role=") || html.contains("aria-live"))
        }
        assertTrue(renderHtml { dataPanel(DataState.STALE, "Old", link) {} }.contains("href=\"/people\""))
        assertThrows(IllegalArgumentException::class.java) { renderHtml { dataPanel(DataState.STALE) {} } }
        assertThrows(IllegalArgumentException::class.java) { renderHtml { dataPanel(notice = "Hi") {} } }
        assertThrows(IllegalArgumentException::class.java) {
            renderHtml { dataPanel(DataState.LOADING, "Loading", link) {} }
        }
    }

    @Test
    fun `region root is stable and filters are native GET forms`() {
        val region = renderHtml { dataRegion(UiId("people"), "People") { text("x") } }
        assertTrue(region.contains("data-woge-region=\"people\" data-woge-revision=\"0\""))
        assertTrue(region.contains("tabindex=\"-1\"") && region.contains("aria-label=\"People\""))
        val form =
            renderHtml {
                filterForm(
                    "Filter people",
                    applicationUrl("/people"),
                    "Apply",
                    mapOf("sort" to "name"),
                    DataLink("Reset", applicationUrl("/people")),
                ) { text("Fields") }
            }
        listOf(
            "method=\"get\"",
            "action=\"/people\"",
            "role=\"search\"",
            "type=\"hidden\" name=\"sort\" value=\"name\"",
            "<button type=\"submit\">Apply</button>",
            ">Reset</a>",
        ).forEach { assertTrue(form.contains(it), it) }
        assertFalse(form.contains("page"))
    }

    @Test
    fun `page windows bound every request and clamp URL values`() {
        val window = PageWindow.of(page = 99, size = 1_000, total = 250)
        assertEquals(PageWindow.MAX_SIZE, window.size)
        assertEquals(3, window.page)
        assertEquals(200L, window.offset)
        assertEquals(listOf(1, 2, 3), window.pages())
        assertEquals(listOf(1, 4, 5, 6, 7, 8, 20), PageWindow.of(6, 10, 200).pages())
        assertEquals(1, PageWindow.of(null, 0, 0).lastPage)
        assertEquals(1, PageWindow.of(-5, null, 10).page)
        assertThrows(IllegalArgumentException::class.java) { PageWindow.of(1, 10, -1) }
    }
}
