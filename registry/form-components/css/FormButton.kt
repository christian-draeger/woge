// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.button

public enum class FormButtonType(
    public val html: String,
) {
    SUBMIT("submit"),
    BUTTON("button"),
    RESET("reset"),
}

public data class FormButtonState(
    public val type: FormButtonType = FormButtonType.SUBMIT,
    public val disabled: Boolean = false,
    public val pending: Boolean = false,
)

public fun HtmlWriter.formButton(
    label: String,
    state: FormButtonState = FormButtonState(),
    attributes: Attributes.() -> Unit = {},
) {
    button(attributes = {
        formPart("button")
        attribute("type", state.type.html)
        boolean("disabled", state.disabled)
        data("disabled", state.disabled.toString())
        data("pending", state.pending.toString())
        if (state.pending) aria("busy", "true")
        attributes()
    }) { text(label) }
}
