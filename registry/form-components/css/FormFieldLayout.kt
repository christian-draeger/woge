// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.host.ActionDescriptor
import dev.woge.host.FormElementId
import dev.woge.host.FormErrors
import dev.woge.host.actionForm
import dev.woge.host.formErrorSummary
import dev.woge.host.formFieldErrors
import dev.woge.html.Attributes
import dev.woge.html.HtmlWriter
import dev.woge.html.div
import dev.woge.html.label
import dev.woge.html.p
import dev.woge.ui.LiveRegion
import dev.woge.ui.liveRegion

public fun <Command : Any> HtmlWriter.formFieldRow(
    control: FormControl<Command>,
    label: String,
    content: HtmlWriter.() -> Unit,
) {
    div(attributes = { formPart("field") }) {
        formLabel(control, label)
        content()
        formDescription(control)
        formError(control)
    }
}

public fun <Command : Any> HtmlWriter.formLabel(
    control: FormControl<Command>,
    text: String,
) {
    label(attributes = {
        attribute("for", control.field.id.value)
        formPart("label")
    }) { text(text) }
}

public fun <Command : Any> HtmlWriter.formDescription(control: FormControl<Command>) {
    if (control.description.isNotEmpty()) {
        p(attributes = {
            attribute("id", control.descriptionId.value)
            formPart("description")
        }) { text(control.description) }
    }
}

public fun <Command : Any> HtmlWriter.formError(control: FormControl<Command>) {
    if (control.errors.messages(control.field).isNotEmpty()) {
        div(attributes = { formPart("error") }) { formFieldErrors(control.field, control.errors) }
    }
}

public fun HtmlWriter.formLayout(
    action: ActionDescriptor<*>,
    pending: Boolean = false,
    attributes: Attributes.() -> Unit = {},
    content: HtmlWriter.() -> Unit,
) {
    actionForm(action, attributes = {
        formPart("layout")
        data("pending", pending.toString())
        if (pending) aria("busy", "true")
        attributes()
    }, content = content)
}

public fun <Command : Any> HtmlWriter.formSummary(
    errors: FormErrors<Command>,
    id: FormElementId,
    title: String,
    autofocus: Boolean = true,
) {
    formErrorSummary(errors, id, title, attributes = {
        formPart("summary")
        boolean("autofocus", autofocus)
    })
}

public fun HtmlWriter.formSuccess(message: String) {
    p(attributes = {
        formPart("success")
        data("success", "true")
        liveRegion(LiveRegion.STATUS)
    }) { text(message) }
}
