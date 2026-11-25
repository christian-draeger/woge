package dev.woge.examples.invoice

import dev.woge.host.ActionExecutor
import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.FormElementId
import dev.woge.host.FormError
import dev.woge.host.FormErrors
import dev.woge.host.FormField
import dev.woge.host.FormSubmission
import dev.woge.host.FormValidation
import dev.woge.host.FormValues
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.WogeAction
import dev.woge.host.WogeRegion
import dev.woge.host.actionForm
import dev.woge.host.failure
import dev.woge.host.formErrorSummary
import dev.woge.host.formField
import dev.woge.host.formFieldErrors
import dev.woge.host.htmlPage
import dev.woge.host.region
import dev.woge.host.withFormValidation
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.moduleScript
import dev.woge.html.p
import dev.woge.html.title
import kotlinx.serialization.Serializable

/** The command preserves the wire text. The action turns that text into application values. */
@Serializable
public data class LocalizedInvoiceFields(
    val amount: String,
    val serviceDay: String,
    val dueDay: String,
    val paymentTime: String,
    val formLanguage: String,
) {
    internal fun submittedText(): FormValues =
        FormValues.of(
            mapOf(
                "amount" to listOf(amount),
                "serviceDay" to listOf(serviceDay),
                "dueDay" to listOf(dueDay),
                "paymentTime" to listOf(paymentTime),
                "formLanguage" to listOf(formLanguage),
            ),
        )
}

public val localizedInvoiceDecoder: FormDecoder<LocalizedInvoiceFields> =
    FormDecoder(LocalizedInvoiceFields.serializer())

private val amountField =
    localizedInvoiceDecoder.field(
        LocalizedInvoiceFields::amount,
        FormElementId.of("invoice-amount"),
    )
private val serviceDayField =
    localizedInvoiceDecoder.field(LocalizedInvoiceFields::serviceDay, FormElementId.of("invoice-service-day"))
private val dueDayField =
    localizedInvoiceDecoder.field(
        LocalizedInvoiceFields::dueDay,
        FormElementId.of("invoice-due-day"),
    )
private val paymentTimeField =
    localizedInvoiceDecoder.field(LocalizedInvoiceFields::paymentTime, FormElementId.of("invoice-payment-time"))
private val errorSummary = FormElementId.of("invoice-errors")

internal fun RequestContext.invoiceLanguage(): InvoiceLanguage? =
    when (language.value) {
        "de", "de-de" -> InvoiceLanguage.GERMAN
        "en", "en-us" -> InvoiceLanguage.ENGLISH
        "ar", "ar-eg" -> InvoiceLanguage.ARABIC
        else -> null
    }

@WogeAction("validate-localized-invoice")
public suspend fun validateLocalizedInvoice(
    command: LocalizedInvoiceFields,
    context: RequestContext,
): PageResult {
    val language = context.invoiceLanguage()
    return when {
        context.csrf != CsrfVerification.VERIFIED -> failure(FailureCategory.FORBIDDEN, context.correlationId)
        language == null -> failure(FailureCategory.BAD_REQUEST, context.correlationId)
        command.formLanguage != language.tag ->
            invalidInvoice(
                context,
                language,
                command.submittedText(),
                listOf(FormError(null, language.messages.getString("languageChanged"))),
            )
        else -> validateInvoiceValues(command, context, language)
    }
}

private fun validateInvoiceValues(
    command: LocalizedInvoiceFields,
    context: RequestContext,
    language: InvoiceLanguage,
): PageResult =
    when (val parsed = parseInvoiceFields(command, language)) {
        is InvoiceValidation.Invalid ->
            invalidInvoice(
                context,
                language,
                command.submittedText(),
                parsed.issues.map { (name, message) ->
                    FormError(invoiceFields.getValue(name), language.messages.getString(message))
                },
            )
        is InvoiceValidation.Valid -> {
            val text = language.accepted(parsed.values)
            context.render.updates(applicationUrl("/localized-invoice")) {
                replace(InvoiceStatusRegion, InvoiceStatusView(language, text))
            }
        }
    }

private val invoiceFields = listOf(amountField, serviceDayField, dueDayField, paymentTimeField).associateBy { it.name }

private val invoiceDecoderErrors =
    PageUseCase<FormValidation> { request ->
        val context = request.context
        if (context.csrf !=
            CsrfVerification.VERIFIED
        ) {
            return@PageUseCase failure(FailureCategory.FORBIDDEN, context.correlationId)
        }
        val language =
            context.invoiceLanguage() ?: return@PageUseCase failure(FailureCategory.BAD_REQUEST, context.correlationId)
        val values = request.input.values
        val messages = language.messages
        val errors =
            if (values.first("formLanguage") != language.tag) {
                listOf(FormError(null, messages.getString("languageChanged")))
            } else {
                request.input.errors.map { error ->
                    FormError(invoiceFields[error.field], messages.getString("invalidField"))
                }
            }
        invalidInvoice(context, language, values, errors)
    }

public val localizedInvoiceAction: ActionExecutor<FormSubmission<LocalizedInvoiceFields>> =
    ValidateLocalizedInvoiceAction.withFormValidation(invoiceDecoderErrors)

private fun invalidInvoice(
    context: RequestContext,
    language: InvoiceLanguage,
    raw: FormValues,
    errors: List<FormError<LocalizedInvoiceFields>>,
): PageResult.RegionUpdates {
    val view = InvoiceFormView(language, FormErrors(raw, errors))
    return context.render.validation(invoiceDocument(context, view, ResponseStatus.BAD_REQUEST), errorSummary) {
        replace(InvoiceFormRegion, view)
    }
}

public fun localizedInvoicePage(context: RequestContext): PageResult {
    val language = context.invoiceLanguage() ?: return failure(FailureCategory.BAD_REQUEST, context.correlationId)
    return invoiceDocument(context, InvoiceFormView(language, FormErrors()), ResponseStatus.OK)
}

private fun invoiceDocument(
    context: RequestContext,
    view: InvoiceFormView,
    status: ResponseStatus,
): PageResult.Document =
    htmlPage(ResponseMetadata(status = status)) {
        doctype()
        html(attributes = {
            attribute("lang", view.language.tag)
            attribute("dir", view.language.direction)
        }) {
            head {
                context.render.metadata(this)
                title(view.language.messages.getString("title"))
                moduleScript(applicationUrl("/assets/woge/index.js"))
            }
            body {
                region(context.render.target(InvoiceFormRegion), view)
                region(
                    context.render.target(InvoiceStatusRegion),
                    InvoiceStatusView(view.language, ""),
                    elementName = "p",
                    attributes = {
                        attribute("id", "invoice-status")
                        attribute("role", "status")
                    },
                )
                p(attributes = {
                    attribute("id", "invoice-alert")
                    attribute("role", "alert")
                }) {}
            }
        }
    }

internal data class InvoiceFormView(
    val language: InvoiceLanguage,
    val errors: FormErrors<LocalizedInvoiceFields>,
)

@WogeRegion
internal fun HtmlWriter.invoiceForm(view: InvoiceFormView) {
    val messages = view.language.messages
    actionForm(ValidateLocalizedInvoiceAction, attributes = {
        attribute("lang", view.language.tag)
        attribute("dir", view.language.direction)
        data("woge-action", "")
        data("woge-status", "invoice-status")
        data("woge-alert", "invoice-alert")
        data("woge-error-summary", errorSummary.value)
        data("woge-failure-message", messages.getString("failed"))
    }) {
        formErrorSummary(view.errors, errorSummary, messages.getString("summary"))
        invoiceInput(amountField, view.errors, messages.getString("amount"), "text", inputMode = "decimal")
        invoiceInput(serviceDayField, view.errors, messages.getString("serviceDay"), "date")
        invoiceInput(dueDayField, view.errors, messages.getString("dueDay"), "text")
        invoiceInput(paymentTimeField, view.errors, messages.getString("paymentTime"), "text")
        input(attributes = {
            attribute("type", "hidden")
            attribute("name", "formLanguage")
            attribute("value", view.language.tag)
        })
        button { text(messages.getString("submit")) }
    }
}

private fun HtmlWriter.invoiceInput(
    field: FormField<LocalizedInvoiceFields>,
    errors: FormErrors<LocalizedInvoiceFields>,
    caption: String,
    type: String,
    inputMode: String? = null,
) {
    label(attributes = { attribute("for", field.id.value) }) { text(caption) }
    input(attributes = {
        formField(field, errors)
        attribute("type", type)
        if (field == serviceDayField || field == paymentTimeField) attribute("dir", "ltr")
        inputMode?.let { attribute("inputmode", it) }
        attribute("value", errors.value(field).orEmpty())
    })
    formFieldErrors(field, errors)
}

internal data class InvoiceStatusView(
    val language: InvoiceLanguage,
    val message: String,
)

@WogeRegion
internal fun HtmlWriter.invoiceStatus(view: InvoiceStatusView) {
    element("span", attributes = {
        attribute("lang", view.language.tag)
        attribute("dir", view.language.direction)
    }) { text(view.message) }
}
