// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.host.formField
import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.datalist
import dev.woge.html.div
import dev.woge.html.fieldset
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.legend
import dev.woge.html.option
import java.time.LocalDate

/** A hint shown by the browser's own suggestion list. The user can still type any value. */
public class ComboboxSuggestion(
    public val value: String,
    public val label: String? = null,
)

/** More suggestions than this belong on a search results page, not in the HTML of a form. */
public const val MAX_COMBOBOX_SUGGESTIONS: Int = 50

/**
 * Native `input` + `datalist`. Suggestions are hints only: validate the submitted text on the server.
 * Pass the best few matches; the list is part of the page, so it cannot hold a large collection.
 */
public fun <Command : Any> HtmlWriter.formCombobox(
    control: FormControl<Command>,
    suggestions: List<ComboboxSuggestion>,
) {
    require(suggestions.size <= MAX_COMBOBOX_SUGGESTIONS) { "At most $MAX_COMBOBOX_SUGGESTIONS suggestions" }
    val listId = "${control.field.id.value}-options"
    formInput(control) {
        attribute("list", listId)
        attribute("autocomplete", "off")
    }
    datalist(attributes = { attribute("id", listId) }) {
        suggestions.forEach { suggestion ->
            option(attributes = { attribute("value", suggestion.value) }) { suggestion.label?.let { text(it) } }
        }
    }
}

/**
 * Native `input type="date"`. The browser shows the user's own date format; the value sent to the
 * server is always `yyyy-MM-dd`. Browsers without a date picker show a text box with that pattern.
 */
public fun <Command : Any> HtmlWriter.formDateInput(
    control: FormControl<Command>,
    min: LocalDate? = null,
    max: LocalDate? = null,
) {
    require(min == null || max == null || min <= max) { "Earliest date must not be after latest date" }
    formInput(control, FormInputType.DATE) {
        min?.let { attribute("min", it.toString()) }
        max?.let { attribute("max", it.toString()) }
        attribute("placeholder", "yyyy-mm-dd")
        attribute("pattern", "[0-9]{4}-[0-9]{2}-[0-9]{2}")
    }
}

/**
 * Several native checkboxes with one name. A browser submits the checked values as repeated
 * parameters. A checkbox group is the default multi-choice control; use `formSelect(multiple = true)`
 * only for a compact list that users will rarely change.
 */
public fun <Command : Any> HtmlWriter.formCheckboxGroup(
    control: FormControl<Command>,
    title: String,
    choices: List<FormChoice>,
) {
    require(title.isNotBlank()) { "Checkbox group needs a legend" }
    require(!control.state.readOnly) { "HTML checkboxes do not support readonly" }
    require(choices.map { it.id }.distinct().size == choices.size) { "Checkbox choice IDs must be unique" }
    require(choices.none { it.id == control.field.id }) { "Checkbox choice IDs must differ from the group ID" }
    val selected = control.errors.values.all(control.field.name)
    val invalid = control.errors.messages(control.field).isNotEmpty()
    fieldset(attributes = {
        attribute("id", control.field.id.value)
        attribute("tabindex", "-1")
        boolean("disabled", control.state.disabled)
        data("invalid", invalid.toString())
        val described =
            listOfNotNull(
                control.descriptionId.takeIf { control.description.isNotEmpty() },
                control.field.errorId.takeIf { invalid },
            )
        if (described.isNotEmpty()) attribute("aria-describedby", described.joinToString(" ") { it.value })
        complexPart("group")
    }) {
        legend { text(title) }
        choices.forEach { choice ->
            div(attributes = { complexPart("choice") }) {
                input(attributes = {
                    attribute("type", "checkbox")
                    formField(control.field, control.errors, emptyList(), choice.id)
                    attribute("value", choice.value)
                    boolean("checked", choice.value in selected)
                    boolean("disabled", control.state.disabled || choice.disabled)
                })
                label(attributes = { attribute("for", choice.id.value) }) { text(choice.label) }
            }
        }
        formDescription(control)
        formError(control)
    }
}

internal fun Attributes.complexPart(part: String) {
    data("woge-scope", "woge-complex-input-components")
    data("part", part)
    classes(
        "text-[color:var(--woge-ink,#1f2933)]",
        when (part) {
            "group" ->
                "grid gap-2 m-0 border border-[color:var(--woge-line,#d9e2ec)] py-3 px-4 " +
                    "data-[invalid=true]:border-[color:var(--woge-danger,#b42318)]"
            else -> "flex items-center gap-2 min-h-11 [&_input]:size-5 [&_input]:accent-[var(--woge-focus,#005fcc)]"
        },
    )
}
