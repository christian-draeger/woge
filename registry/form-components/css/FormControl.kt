// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.host.FormElementId
import dev.woge.host.FormErrors
import dev.woge.host.FormField
import dev.woge.host.formField
import dev.woge.html.Attributes

public data class FormControlState(
    public val required: Boolean = false,
    public val disabled: Boolean = false,
    public val readOnly: Boolean = false,
)

public class FormControl<Command : Any>(
    public val field: FormField<Command>,
    public val errors: FormErrors<Command>,
    public val description: String = "",
    public val state: FormControlState = FormControlState(),
) {
    public val descriptionId: FormElementId = FormElementId.of("${field.id.value}-description")
}

internal fun <Command : Any> Attributes.controlAttributes(
    control: FormControl<Command>,
    textControl: Boolean = true,
) {
    require(textControl || !control.state.readOnly) { "HTML readonly is only supported on text controls" }
    formField(
        control.field,
        control.errors,
        if (control.description.isEmpty()) emptyList() else listOf(control.descriptionId),
    )
    boolean("required", control.state.required)
    boolean("disabled", control.state.disabled)
    boolean("readonly", control.state.readOnly)
    data(
        "invalid",
        control.errors
            .messages(control.field)
            .isNotEmpty()
            .toString(),
    )
    data("required", control.state.required.toString())
    data("disabled", control.state.disabled.toString())
    data("readonly", control.state.readOnly.toString())
    formPart("control")
}

internal fun Attributes.formPart(part: String) {
    data("woge-scope", "woge-form-components")
    data("part", part)
    classes("woge-form-$part")
}
