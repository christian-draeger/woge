// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.input
import dev.woge.html.textarea

public enum class FormInputType(
    public val html: String,
) {
    TEXT("text"),
    EMAIL("email"),
    TEL("tel"),
    URL("url"),
    NUMBER("number"),
    DATE("date"),
}

public fun <Command : Any> HtmlWriter.formInput(
    control: FormControl<Command>,
    type: FormInputType = FormInputType.TEXT,
    attributes: Attributes.() -> Unit = {},
) {
    input(attributes = {
        controlAttributes(control)
        attribute("type", type.html)
        attribute("value", control.errors.value(control.field).orEmpty())
        attributes()
    })
}

public fun <Command : Any> HtmlWriter.formTextarea(
    control: FormControl<Command>,
    attributes: Attributes.() -> Unit = {},
) {
    val value = control.errors.value(control.field).orEmpty()
    // HTML ignores the first line break immediately after a textarea start tag.
    val nativeValue = if (value.startsWith("\n") || value.startsWith("\r")) "\n$value" else value
    textarea(attributes = {
        controlAttributes(control)
        attributes()
    }, value = nativeValue)
}
