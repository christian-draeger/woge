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
    classes(
        "text-[color:var(--woge-ink,#1f2933)] focus-visible:outline-2 focus-visible:outline-offset-2",
        "focus-visible:outline-[var(--woge-form-focus,currentColor)]",
        "disabled:opacity-60 data-[readonly=true]:border-dashed data-[pending=true]:cursor-progress",
        "data-[invalid=true]:border-[color:var(--woge-danger,#b42318)]",
        when (part) {
            "layout", "field" -> "grid gap-[var(--woge-form-gap,0.75rem)]"
            "label" -> "font-semibold"
            "control" ->
                "font-[inherit] max-w-full py-2 px-3 border border-[color:var(--woge-line,#d9e2ec)] " +
                    "rounded-[var(--woge-form-radius,0.375rem)] bg-[var(--woge-surface,#ffffff)]"
            "description" -> "my-0 text-[color:var(--woge-muted,#52606d)]"
            "error" -> "text-[color:var(--woge-danger,#b42318)]"
            "summary" -> "border-s-4 px-4 text-[color:var(--woge-danger,#b42318)]"
            "success" -> "text-[color:var(--woge-ok,#1b7f3b)]"
            "button" ->
                "font-[inherit] py-2 px-4 border rounded-[var(--woge-form-radius,0.375rem)] " +
                    "bg-[var(--woge-surface,#ffffff)]"
            else -> "accent-[var(--woge-form-focus,currentColor)]"
        },
    )
}
