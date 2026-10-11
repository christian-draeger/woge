// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.host.FormElementId
import dev.woge.host.formField
import dev.woge.html.HtmlWriter
import dev.woge.html.fieldset
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.legend
import dev.woge.html.option
import dev.woge.html.select

public data class FormChoice(
    public val value: String,
    public val label: String,
    public val id: FormElementId,
    public val disabled: Boolean = false,
)

public fun <Command : Any> HtmlWriter.formCheckbox(
    control: FormControl<Command>,
    label: String,
    value: String = "true",
    switch: Boolean = false,
) {
    input(attributes = {
        controlAttributes(control, textControl = false)
        attribute("type", "checkbox")
        attribute("value", value)
        boolean("checked", value in control.errors.values.all(control.field.name))
        if (switch) attribute("role", "switch")
    })
    formLabel(control, label)
    formDescription(control)
    formError(control)
}

public fun <Command : Any> HtmlWriter.formSwitch(
    control: FormControl<Command>,
    label: String,
    value: String = "true",
) {
    formCheckbox(control, label, value, switch = true)
}

public fun <Command : Any> HtmlWriter.formSelect(
    control: FormControl<Command>,
    choices: List<FormChoice>,
    multiple: Boolean = false,
) {
    select(attributes = {
        controlAttributes(control, textControl = false)
        boolean("multiple", multiple)
    }) {
        choices.forEach { choice ->
            option(attributes = {
                attribute("value", choice.value)
                boolean("selected", choice.value in control.errors.values.all(control.field.name))
                boolean("disabled", choice.disabled)
            }) { text(choice.label) }
        }
    }
}

public fun <Command : Any> HtmlWriter.formRadioGroup(
    control: FormControl<Command>,
    title: String,
    choices: List<FormChoice>,
) {
    require(!control.state.readOnly) { "HTML radio groups do not support readonly" }
    require(choices.map { it.id }.distinct().size == choices.size) { "Radio choice IDs must be unique" }
    require(choices.none { it.id == control.field.id }) { "Radio choice IDs must differ from the group ID" }
    fieldset(attributes = {
        controlAttributes(control, textControl = false)
        boolean("required", false)
        attribute("tabindex", "-1")
    }) {
        legend { text(title) }
        choices.forEach { choice -> radioChoice(control, choice) }
        formDescription(control)
        formError(control)
    }
}

private fun <Command : Any> HtmlWriter.radioChoice(
    control: FormControl<Command>,
    choice: FormChoice,
) {
    input(attributes = {
        attribute("type", "radio")
        formField(
            control.field,
            control.errors,
            if (control.description.isEmpty()) emptyList() else listOf(control.descriptionId),
            choice.id,
        )
        attribute("value", choice.value)
        boolean("checked", choice.value == control.errors.value(control.field))
        boolean("required", control.state.required)
        boolean("disabled", control.state.disabled || choice.disabled)
        formPart("choice")
    })
    label(attributes = { attribute("for", choice.id.value) }) { text(choice.label) }
}
