package dev.woge.tck

import dev.woge.host.FormElementId
import dev.woge.host.FormErrors
import dev.woge.host.FormField
import dev.woge.host.PageIdentity
import dev.woge.host.PageResult
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.WogeRegion
import dev.woge.host.actionForm
import dev.woge.host.formErrorSummary
import dev.woge.host.formField
import dev.woge.host.formFieldErrors
import dev.woge.host.htmlPage
import dev.woge.host.region
import dev.woge.html.HtmlWriter
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.input
import dev.woge.html.meta
import dev.woge.html.p
import dev.woge.protocol.PageEpoch

@WogeRegion
public fun HtmlWriter.actionCount(count: Int) {
    p { text("Completed mutations: $count") }
}

@WogeRegion
public fun HtmlWriter.actionStatus(message: String) {
    text(message)
}

public val tckValueField: FormField<TckActionCommand> =
    tckActionForm.field(TckActionCommand::value, FormElementId.of("tck-command-value"))
public val tckErrorSummary: FormElementId = FormElementId.of("tck-error-summary")

@WogeRegion
public fun HtmlWriter.actionCommandForm(errors: FormErrors<TckActionCommand>) {
    actionForm(TckSubmitAction, attributes = {
        data("woge-action", "")
        data("woge-status", "tck-action-status")
        data("woge-alert", "tck-action-alert")
        data("woge-error-summary", tckErrorSummary.value)
        data("woge-failure-message", "Action failed; check the result before submitting again.")
    }) {
        formErrorSummary(errors, tckErrorSummary, "Check the command")
        input(attributes = {
            formField(tckValueField, errors)
            attribute("aria-label", "Command value")
            attribute("value", errors.value(tckValueField).orEmpty())
        })
        formFieldErrors(tckValueField, errors)
        button { text("Submit command") }
        button(attributes = {
            attribute("name", "value")
            attribute("value", "again")
        }) { text("Submit ambiguous command") }
    }
}

internal fun tckValidationDocument(errors: FormErrors<TckActionCommand>): PageResult.Document {
    val page = actionPageIdentity()
    return htmlPage(ResponseMetadata(status = ResponseStatus.BAD_REQUEST)) {
        html {
            head {
                meta(attributes = {
                    attribute("name", "woge-page-epoch")
                    attribute("content", "tck-action-epoch")
                })
            }
            body {
                region(ActionCommandFormRegion.target(page), errors, elementName = "section")
                p(attributes = {
                    attribute("id", "tck-action-status")
                    attribute("role", "status")
                }) {}
                p(attributes = {
                    attribute("id", "tck-action-alert")
                    attribute("role", "alert")
                }) {}
            }
        }
    }
}

internal fun actionPageIdentity(): PageIdentity = PageIdentity(PageEpoch.of("tck-action-epoch"), actionIdentitySecret)

private val actionIdentitySecret = RenderIdentitySecret.random()
