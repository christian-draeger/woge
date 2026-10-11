// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.host.FormDecoder
import dev.woge.host.FormElementId
import dev.woge.host.FormError
import dev.woge.host.FormErrors
import dev.woge.host.FormValues
import dev.woge.html.renderHtml
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ComplexInputComponentsTest {
    private val decoder = FormDecoder(Command.serializer())
    private val city = decoder.field(Command::city, FormElementId.of("city"))
    private val day = decoder.field(Command::day, FormElementId.of("day"))
    private val tags = decoder.field(Command::tags, FormElementId.of("tags"))
    private val tagChoices =
        listOf(
            FormChoice("kotlin", "كوتلن", FormElementId.of("tag-kotlin")),
            FormChoice("html", "HTML <&>", FormElementId.of("tag-html")),
            FormChoice("css", "CSS", FormElementId.of("tag-css"), disabled = true),
        )
    private val empty = FormErrors<Command>(FormValues.EMPTY, emptyList())

    @Test
    fun `combobox is a native input with a bounded datalist and keeps typed text`() {
        val errors = FormErrors<Command>(FormValues.of(mapOf("city" to listOf("Köln <&>"))), emptyList())
        val html =
            renderHtml {
                formCombobox(
                    FormControl(city, errors),
                    listOf(ComboboxSuggestion("Köln"), ComboboxSuggestion("Bonn", "Bonn (NRW)")),
                )
            }
        listOf(
            "list=\"city-options\"",
            "autocomplete=\"off\"",
            "value=\"Köln &lt;&amp;&gt;\"",
            "<datalist id=\"city-options\">",
            "<option value=\"Köln\"></option>",
            ">Bonn (NRW)</option>",
        ).forEach { assertTrue(html.contains(it), it) }
        assertFalse(html.contains("role=\"combobox\"") || html.contains("<script") || html.contains("aria-expanded"))
        val tooMany = List(MAX_COMBOBOX_SUGGESTIONS + 1) { ComboboxSuggestion("v$it") }
        assertThrows(
            IllegalArgumentException::class.java,
        ) { renderHtml { formCombobox(FormControl(city, empty), tooMany) } }
    }

    @Test
    fun `date input uses the native control with ISO bounds and a text fallback pattern`() {
        val html =
            renderHtml {
                formDateInput(FormControl(day, empty), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
            }
        listOf(
            "type=\"date\"",
            "min=\"2026-01-01\"",
            "max=\"2026-12-31\"",
            "placeholder=\"yyyy-mm-dd\"",
            "pattern=\"[0-9]{4}-[0-9]{2}-[0-9]{2}\"",
        ).forEach { assertTrue(html.contains(it), it) }
        assertThrows(IllegalArgumentException::class.java) {
            renderHtml { formDateInput(FormControl(day, empty), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)) }
        }
    }

    @Test
    fun `checkbox group repeats one name with labelled native checkboxes and linked errors`() {
        val errors =
            FormErrors(
                FormValues.of(mapOf("tags" to listOf("html", "css"))),
                listOf(FormError(tags, "Pick <one>")),
            )
        val html =
            renderHtml { formCheckboxGroup(FormControl(tags, errors, "Help"), "Tags", tagChoices) }
        listOf(
            "<fieldset id=\"tags\" tabindex=\"-1\"",
            "aria-describedby=\"tags-description tags-error\"",
            "<legend>Tags</legend>",
            "name=\"tags\"",
            "id=\"tag-html\"",
            "value=\"html\"",
            "for=\"tag-kotlin\"",
            ">كوتلن</label>",
            "HTML &lt;&amp;&gt;",
            "Pick &lt;one&gt;",
            "aria-errormessage=\"tags-error\"",
        ).forEach { assertTrue(html.contains(it), it) }
        assertEquals(2, Regex(" checked(?=[ >])").findAll(html).count())
        assertEquals(1, Regex(" disabled(?=[ >])").findAll(html).count())
        assertEquals(3, Regex("type=\"checkbox\"").findAll(html).count())
        assertFalse(html.contains(" required") || html.contains("<script") || html.contains("role="))
    }

    @Test
    fun `unsupported checkbox group shapes fail before markup`() {
        val readOnly = FormControl(tags, empty, state = FormControlState(readOnly = true))
        assertThrows(
            IllegalArgumentException::class.java,
        ) { renderHtml { formCheckboxGroup(readOnly, "Tags", tagChoices) } }
        assertThrows(IllegalArgumentException::class.java) {
            renderHtml { formCheckboxGroup(FormControl(tags, empty), "Tags", tagChoices + tagChoices.first()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            renderHtml { formCheckboxGroup(FormControl(tags, empty), " ", tagChoices) }
        }
    }

    @Serializable
    private data class Command(
        val city: String = "",
        val day: String = "",
        val tags: List<String> = emptyList(),
    )
}
