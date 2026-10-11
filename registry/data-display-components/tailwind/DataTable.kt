// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.HtmlUrl
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.caption
import dev.woge.html.div
import dev.woge.html.input
import dev.woge.html.span
import dev.woge.html.table
import dev.woge.html.tbody
import dev.woge.html.td
import dev.woge.html.th
import dev.woge.html.thead
import dev.woge.html.tr
import dev.woge.ui.UiId

public enum class SortDirection(
    public val html: String,
) {
    ASCENDING("ascending"),
    DESCENDING("descending"),
}

/**
 * A header cell. A sortable column has a [sortHref], the link that sorts by it next. Set [sorted] on
 * the one column the server sorted by; the server stays in charge of the order.
 */
public class DataColumn(
    public val key: String,
    public val label: String,
    public val sortHref: HtmlUrl? = null,
    public val sorted: SortDirection? = null,
    public val numeric: Boolean = false,
)

/**
 * One record. The first cell becomes the row header. A [pending] row is waiting for the server to
 * confirm an optimistic change.
 */
public class DataRow(
    public val key: String,
    public val label: String,
    public val cells: List<HtmlWriter.() -> Unit>,
    public val selected: Boolean = false,
    public val pending: Boolean = false,
)

/** A native checkbox per row, submitted by the surrounding form under [name]. */
public class RowSelection(
    public val name: String,
    public val columnLabel: String,
    public val rowLabel: (String) -> String,
)

/** Wrap this in the form that owns the selection. The scroll region keeps real table semantics. */
public fun HtmlWriter.dataTable(
    id: UiId,
    caption: String,
    columns: List<DataColumn>,
    rows: List<DataRow>,
    selection: RowSelection? = null,
) {
    require(caption.isNotBlank()) { "Table needs a caption" }
    require(columns.isNotEmpty()) { "Table needs columns" }
    require(columns.count { it.sorted != null } <= 1) { "Only one column can be sorted" }
    require(columns.all { it.sorted == null || it.sortHref != null }) { "A sorted column must be sortable" }
    require(rows.map { it.key }.distinct().size == rows.size) { "Row keys must be unique" }
    require(rows.all { it.cells.size == columns.size }) { "Every row needs one cell per column" }
    div(attributes = {
        attribute("role", "region")
        aria("label", caption)
        attribute("tabindex", "0")
        dataPart("scroll")
    }) {
        table(attributes = {
            attribute("id", id.value)
            dataPart("table")
        }) {
            caption { text(caption) }
            thead { tr { headerCells(id, columns, selection) } }
            tbody { rows.forEach { row -> dataRow(id, row, columns, selection) } }
        }
    }
}

private fun HtmlWriter.headerCells(
    id: UiId,
    columns: List<DataColumn>,
    selection: RowSelection?,
) {
    if (selection != null) {
        th(attributes = { attribute("scope", "col") }) {
            span(attributes = { dataPart("visually-hidden") }) { text(selection.columnLabel) }
        }
    }
    columns.forEach { column ->
        th(attributes = {
            attribute("scope", "col")
            column.sorted?.let { aria("sort", it.html) }
            if (column.numeric) data("numeric", "true")
        }) {
            if (column.sortHref == null) {
                text(column.label)
            } else {
                a(attributes = {
                    url("href", column.sortHref)
                    data("woge-state-key", "${id.value}-sort-${dataKey(column.key)}")
                    dataPart("sort")
                }) { text(column.label) }
            }
        }
    }
}

private fun HtmlWriter.dataRow(
    id: UiId,
    row: DataRow,
    columns: List<DataColumn>,
    selection: RowSelection?,
) {
    tr(attributes = {
        data("row", dataKey(row.key))
        data("selected", row.selected.toString())
        data("pending", row.pending.toString())
        if (row.pending) aria("busy", "true")
    }) {
        if (selection != null) {
            td {
                input(attributes = {
                    attribute("type", "checkbox")
                    attribute("name", selection.name)
                    attribute("value", row.key)
                    attribute("id", "${id.value}-select-${dataKey(row.key)}")
                    data("woge-state-key", "${id.value}-select-${dataKey(row.key)}")
                    aria("label", selection.rowLabel(row.label))
                    boolean("checked", row.selected)
                    dataPart("select")
                })
            }
        }
        row.cells.forEachIndexed { index, cell ->
            val numeric = columns[index].numeric
            if (index == 0) {
                th(attributes = {
                    attribute("scope", "row")
                    if (numeric) data("numeric", "true")
                }) { cell() }
            } else {
                td(attributes = { if (numeric) data("numeric", "true") }) { cell() }
            }
        }
    }
}
