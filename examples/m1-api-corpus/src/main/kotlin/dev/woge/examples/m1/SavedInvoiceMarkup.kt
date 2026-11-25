package dev.woge.examples.m1

import dev.woge.host.ActionDescriptor
import dev.woge.host.FormElementId
import dev.woge.host.FormError
import dev.woge.host.FormErrors
import dev.woge.host.FormField
import dev.woge.host.FormValues
import dev.woge.host.MultipartSubmission
import dev.woge.host.PageResult
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.formErrorSummary
import dev.woge.host.formField
import dev.woge.host.formFieldErrors
import dev.woge.host.htmlPage
import dev.woge.host.multipartActionForm
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.fieldset
import dev.woge.html.h1
import dev.woge.html.h2
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.legend
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.moduleScript
import dev.woge.html.nav
import dev.woge.html.p
import dev.woge.html.title
import dev.woge.html.ul
import dev.woge.ui.UPLOAD_MODULE_PATH
import dev.woge.ui.UiId
import dev.woge.ui.UploadMessages
import dev.woge.ui.enhancedUpload
import dev.woge.ui.uploadProgress
import java.text.MessageFormat

private val savedSummary = FormElementId.of("saved-invoice-errors")
private val savedProgress = UiId("saved-invoice-upload")

internal data class SavedInvoiceView(
    val language: InvoiceLanguage,
    val records: List<SavedInvoice>,
    val page: Int,
    val save: ActionDescriptor<MultipartSubmission<SavedInvoiceFields>>,
    val current: SavedInvoice? = null,
    val submitted: FormValues? = null,
    val errors: List<FormError<SavedInvoiceFields>> = emptyList(),
    val status: ResponseStatus = ResponseStatus.OK,
) {
    fun message(key: String) = language.messages.getString(key)

    fun formatted(
        key: String,
        value: String,
    ): String = MessageFormat(message(key), language.locale).format(arrayOf(value))

    fun text(
        field: FormField<SavedInvoiceFields>,
        existing: String?,
    ): String = if (submitted == null) existing.orEmpty() else submitted.first(field.name).orEmpty()
}

internal fun savedInvoiceDocument(view: SavedInvoiceView): PageResult.Document =
    htmlPage(ResponseMetadata(status = view.status)) {
        doctype()
        html(attributes = {
            attribute("lang", view.language.tag)
            attribute("dir", view.language.direction)
        }) {
            head {
                meta { attribute("charset", "utf-8") }
                title(view.message("savedTitle"))
                moduleScript(applicationUrl(UPLOAD_MODULE_PATH))
            }
            body {
                main {
                    h1 { text(view.message("savedTitle")) }
                    renderStoredInvoices(view)
                    renderInvoiceEditor(view)
                }
            }
        }
    }

private fun HtmlWriter.renderStoredInvoices(view: SavedInvoiceView) {
    h2 { text(view.message("savedList")) }
    ul {
        view.records.take(INVOICE_PAGE_SIZE).forEach { invoice ->
            li {
                a(attributes = { url("href", SavedInvoiceRoute.url(SavedInvoiceInput(invoice = invoice.id))) }) {
                    text(view.language.accepted(invoice.draft.values))
                }
            }
        }
    }
    nav(attributes = { attribute("aria-label", view.message("savedList")) }) {
        p(attributes = { attribute("id", "saved-page") }) {
            text(view.formatted("savedPage", view.language.localizedNumber(view.page)))
        }
        if (view.page > 1) {
            a(attributes = { url("href", SavedInvoiceRoute.url(SavedInvoiceInput(page = view.page - 1))) }) {
                text(view.message("savedPrevious"))
            }
        }
        if (view.records.size > INVOICE_PAGE_SIZE && view.page < Int.MAX_VALUE) {
            a(attributes = { url("href", SavedInvoiceRoute.url(SavedInvoiceInput(page = view.page + 1))) }) {
                text(view.message("savedNext"))
            }
        }
    }
}

private fun HtmlWriter.renderInvoiceEditor(view: SavedInvoiceView) {
    val current = view.current
    val language = view.language
    val errors = FormErrors(view.submitted ?: FormValues.EMPTY, view.errors)
    h2 { text(view.message(if (current == null) "savedCreate" else "savedEdit")) }
    if (current != null) {
        p(attributes = { attribute("id", "stored-receipt") }) {
            text(view.formatted("savedReceiptStored", language.localizedNumber(current.receiptBytes)))
        }
        a(attributes = { url("href", SavedInvoiceRoute.url(SavedInvoiceInput(invoice = current.id))) }) {
            text(view.message("savedReload"))
        }
    }
    multipartActionForm(view.save, attributes = { enhancedUpload(savedProgress) }) {
        formErrorSummary(errors, savedSummary, view.message("summary"))
        hidden(savedLanguage, language.tag)
        val id = if (view.submitted == null) current?.id?.toString() else view.submitted.first(savedId)
        id?.takeIf(String::isNotEmpty)?.let { hidden(savedId, it) }
        hidden(savedRevision, view.submitted?.first(savedRevision) ?: (current?.revision ?: 0).toString())
        val stored = current?.draft?.values
        invoiceInput(
            savedAmount,
            view.text(savedAmount, stored?.amount?.let(language::formatAmount)),
            view.message("amount"),
            errors,
        )
        invoiceInput(
            savedService,
            view.text(savedService, stored?.serviceDay?.toString()),
            view.message("serviceDay"),
            errors,
            "date",
        )
        invoiceInput(
            savedDue,
            view.text(savedDue, stored?.dueDay?.let(language.date::format)),
            view.message("dueDay"),
            errors,
        )
        val payment = stored?.payment?.atZone(paymentZone)?.let(paymentTimeFormat::format)
        invoiceInput(
            savedPayment,
            view.text(savedPayment, payment),
            view.message("paymentTime"),
            errors,
            direction = "ltr",
        )
        renderAllocations(view, errors, servicesFields, current?.draft?.services)
        renderAllocations(view, errors, materialsFields, current?.draft?.materials)
        button { text(view.message("savedSubmit")) }
        renderSavedUploadProgress(view)
    }
}

private fun HtmlWriter.renderSavedUploadProgress(view: SavedInvoiceView) {
    uploadProgress(
        savedProgress,
        view.message("savedUploadProgress"),
        view.message("savedUploadCancel"),
        UploadMessages(
            view.message("savedUploading"),
            view.message("savedUploadCancelled"),
            view.message("savedUploadFailed"),
            view.message("savedUploaded"),
        ),
    )
}

private fun HtmlWriter.hidden(
    name: String,
    value: String,
) {
    input(attributes = {
        attribute("type", "hidden")
        attribute("name", name)
        attribute("value", value)
    })
}

private fun HtmlWriter.renderAllocations(
    view: SavedInvoiceView,
    errors: FormErrors<SavedInvoiceFields>,
    controls: InvoiceAllocationControls,
    stored: InvoiceAllocations?,
) {
    fieldset {
        legend { text(view.message(controls.labelKey)) }
        hidden(controls.keyName, controls.key.toString())
        val values =
            if (view.submitted == null) {
                listOf(stored?.first, stored?.second).map { it?.let(view.language::formatAmount).orEmpty() }
            } else {
                view.submitted.all(controls.amounts.name)
            }
        invoiceInput(
            controls.amounts,
            values.getOrElse(0) { "" },
            view.message("savedFirst"),
            errors,
            showErrors = false,
        )
        invoiceInput(
            controls.amounts,
            values.getOrElse(1) { "" },
            view.message("savedSecond"),
            errors,
            control = controls.second,
        )
        if (controls === servicesFields) {
            label(attributes = { attribute("for", receiptField.id.value) }) { text(view.message("savedReceipt")) }
            input(attributes = {
                formField(receiptField, errors, listOf(FormElementId.of("receipt-help")))
                attribute("type", "file")
                attribute("accept", "text/plain")
            })
            p(attributes = { attribute("id", "receipt-help") }) { text(view.message("savedReceiptHelp")) }
            formFieldErrors(receiptField, errors)
        }
    }
}

@Suppress("LongParameterList")
private fun HtmlWriter.invoiceInput(
    field: FormField<SavedInvoiceFields>,
    value: String,
    caption: String,
    errors: FormErrors<SavedInvoiceFields>,
    type: String = "text",
    direction: String? = null,
    control: FormElementId = field.id,
    showErrors: Boolean = true,
) {
    label(attributes = { attribute("for", control.value) }) { text(caption) }
    input(attributes = {
        formField(field, errors, emptyList(), control)
        attribute("type", type)
        if (type == "date") attribute("dir", "ltr") else direction?.let { attribute("dir", it) }
        attribute("value", value)
    })
    if (showErrors) formFieldErrors(field, errors)
}
