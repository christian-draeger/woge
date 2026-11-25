package dev.woge.examples.m1

import dev.woge.host.ActionDescriptor
import dev.woge.host.ActionId
import dev.woge.host.CsrfVerification
import dev.woge.host.DirectoryUploadTemporaryFiles
import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.FormElementId
import dev.woge.host.FormError
import dev.woge.host.FormField
import dev.woge.host.FormLimits
import dev.woge.host.FormProblem
import dev.woge.host.FormResult
import dev.woge.host.FormRowKey
import dev.woge.host.FormValues
import dev.woge.host.MultipartSubmission
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.ResponseStatus
import dev.woge.host.UploadLimits
import dev.woge.host.UploadPolicy
import dev.woge.host.UploadedFile
import dev.woge.host.WogeRoute
import dev.woge.host.child
import dev.woge.host.failure
import dev.woge.host.redirect
import dev.woge.host.row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.file.Path

@WogeRoute("/localized-invoices")
internal data class SavedInvoiceInput(
    val invoice: Long? = null,
    val page: Int? = null,
)

@Serializable
internal data class SavedInvoiceFields(
    val invoice: LocalizedInvoiceFields,
    val id: Long? = null,
    val revision: Long = 0,
    val rows: List<InvoiceAllocationFields>,
)

@Serializable
internal data class InvoiceAllocationFields(
    val key: Int,
    val amounts: List<String>,
    // Browsers send the file separately. This field supplies the typed name and error association.
    val receipt: String? = null,
)

internal val savedInvoiceDecoder =
    FormDecoder(SavedInvoiceFields.serializer(), FormLimits(bodyBytes = 4096, fieldCount = 24, valueBytes = 1024))
private val invoicePath = savedInvoiceDecoder.path(SavedInvoiceFields::invoice)
private val allocationsPath = savedInvoiceDecoder.path(SavedInvoiceFields::rows)
internal val savedAmount = invoicePath.child(LocalizedInvoiceFields::amount).field(FormElementId.of("saved-amount"))
internal val savedService =
    invoicePath
        .child(
            LocalizedInvoiceFields::serviceDay,
        ).field(FormElementId.of("saved-service-day"))
internal val savedDue = invoicePath.child(LocalizedInvoiceFields::dueDay).field(FormElementId.of("saved-due-day"))
internal val savedPayment =
    invoicePath
        .child(
            LocalizedInvoiceFields::paymentTime,
        ).field(FormElementId.of("saved-payment-time"))
internal val savedLanguage = invoicePath.child(LocalizedInvoiceFields::formLanguage).name
internal val savedId = savedInvoiceDecoder.path(SavedInvoiceFields::id).name
internal val savedRevision = savedInvoiceDecoder.path(SavedInvoiceFields::revision).name
private const val SERVICES_KEY = 7
private const val MATERIALS_KEY = 42
internal val servicesFields = InvoiceAllocationControls(SERVICES_KEY, "services")
internal val materialsFields = InvoiceAllocationControls(MATERIALS_KEY, "materials")
internal val receiptField =
    allocationsPath
        .row(
            FormRowKey.of(servicesFields.key),
        ).child(InvoiceAllocationFields::receipt)
        .field(FormElementId.of("saved-receipt"))

private val nestedInvoiceFields =
    mapOf(
        LocalizedInvoiceFields::amount.name to savedAmount,
        LocalizedInvoiceFields::serviceDay.name to savedService,
        LocalizedInvoiceFields::dueDay.name to savedDue,
        LocalizedInvoiceFields::paymentTime.name to savedPayment,
    )
private val fieldsByName =
    (nestedInvoiceFields.values + listOf(servicesFields.amounts, materialsFields.amounts, receiptField)).associateBy {
        it.name
    }

internal class InvoiceAllocationControls(
    val key: Int,
    val labelKey: String,
) {
    private val row = allocationsPath.row(FormRowKey.of(key))
    val keyName = row.child(InvoiceAllocationFields::key).name
    val amounts = row.child(InvoiceAllocationFields::amounts).field(FormElementId.of("saved-row-$key-first"))
    val second = FormElementId.of("saved-row-$key-second")
}

internal fun savedInvoiceUploads(directory: Path) =
    UploadPolicy(
        limits = UploadLimits(requestBytes = 16_384, fileBytes = MAX_RECEIPT_BYTES.toLong(), fileCount = 1),
        temporaryFiles = { DirectoryUploadTemporaryFiles(directory) },
        authorize = { it.field == receiptField.name },
    )

/** A portable application boundary with the repository injected by its host. */
internal class SavedInvoices(
    private val store: SavedInvoiceStore,
) {
    val save: ActionDescriptor<MultipartSubmission<SavedInvoiceFields>> =
        object : ActionDescriptor<MultipartSubmission<SavedInvoiceFields>>(ActionId.of("save-localized-invoice")) {
            override suspend fun execute(request: PageRequest<MultipartSubmission<SavedInvoiceFields>>): PageResult =
                save(request.input, request.context)
        }

    val page: PageUseCase<SavedInvoiceInput> =
        PageUseCase { request ->
            val language = request.context.invoiceLanguage()
            val number = request.input.page ?: 1
            val id = request.input.invoice
            val validId = id == null || id > 0
            if (language == null || number < 1 || !validId) {
                return@PageUseCase failure(FailureCategory.BAD_REQUEST, request.context.correlationId)
            }
            val view =
                withContext(Dispatchers.IO) {
                    val existing = id?.let(store::find)
                    if (id == null || existing != null) {
                        SavedInvoiceView(language, store.page(number), number, save, existing)
                    } else {
                        null
                    }
                }
            if (view == null) {
                failure(FailureCategory.NOT_FOUND, request.context.correlationId)
            } else {
                savedInvoiceDocument(view)
            }
        }

    private suspend fun save(
        multipart: MultipartSubmission<SavedInvoiceFields>,
        context: RequestContext,
    ): PageResult {
        val language = context.invoiceLanguage()
        return when {
            context.csrf != CsrfVerification.VERIFIED -> failure(FailureCategory.FORBIDDEN, context.correlationId)
            language == null -> failure(FailureCategory.BAD_REQUEST, context.correlationId)
            multipart.form.values.first(savedLanguage) != language.tag ->
                invalid(language, multipart.form.values, listOf(error(language, null, "languageChanged")))
            else -> submit(multipart, language)
        }
    }

    private suspend fun submit(
        multipart: MultipartSubmission<SavedInvoiceFields>,
        language: InvoiceLanguage,
    ): PageResult {
        val fields = multipart.form.values
        return when (val result = multipart.form.result) {
            is FormResult.Decoded -> saveDraft(result.command, multipart, language)
            is FormResult.Rejected -> {
                val problem = result.problem
                val errors =
                    if (problem is FormProblem.Fields) {
                        problem.errors.map { error(language, fieldsByName[it.field], "invalidField") }
                    } else {
                        listOf(error(language, null, "invalidField"))
                    }
                invalid(language, fields, errors)
            }
        }
    }

    private suspend fun saveDraft(
        command: SavedInvoiceFields,
        multipart: MultipartSubmission<SavedInvoiceFields>,
        language: InvoiceLanguage,
    ): PageResult {
        val fields = multipart.form.values
        if (!command.hasExpectedShape(fields)) {
            return invalid(language, fields, listOf(error(language, null, "invalidField")))
        }
        val invoice = parseInvoiceFields(command.invoice, language)
        val services = language.allocations(command.rows[0])
        val materials = language.allocations(command.rows[1])
        val offeredFile = multipart.files(receiptField.name).singleOrNull()
        val file = offeredFile?.takeUnless { it.filename.isEmpty() && it.size == 0L }
        val receipt = file?.let { withContext(Dispatchers.IO) { it.checkedReceipt() } }
        val errors =
            buildList {
                addAll(invoice.errors(language))
                if (services == null) add(error(language, servicesFields.amounts, "savedAllocationsError"))
                if (materials == null) add(error(language, materialsFields.amounts, "savedAllocationsError"))
                if (file != null && receipt == null) add(error(language, receiptField, "savedReceiptError"))
                if (file == null && command.id == null) add(error(language, receiptField, "savedReceiptRequired"))
            }
        return if (errors.isEmpty()) {
            val values = (invoice as InvoiceValidation.Valid).values
            val draft = InvoiceDraft(values, checkNotNull(services), checkNotNull(materials))
            persist(command, draft, receipt, fields, language)
        } else {
            invalid(language, fields, errors)
        }
    }

    private suspend fun persist(
        command: SavedInvoiceFields,
        draft: InvoiceDraft,
        receipt: ByteArray?,
        fields: FormValues,
        language: InvoiceLanguage,
    ): PageResult {
        val id =
            withContext(Dispatchers.IO) {
                val existingId = command.id
                if (existingId == null) {
                    store.create(draft, checkNotNull(receipt))
                } else {
                    existingId.takeIf { store.edit(it, command.revision, draft, receipt) }
                }
            }
        return if (id != null) {
            redirect(SavedInvoiceRoute.url(SavedInvoiceInput(invoice = id)))
        } else {
            invalid(language, fields, listOf(error(language, null, "savedStale")), ResponseStatus.CONFLICT)
        }
    }

    private suspend fun invalid(
        language: InvoiceLanguage,
        values: FormValues,
        errors: List<FormError<SavedInvoiceFields>>,
        status: ResponseStatus = ResponseStatus.BAD_REQUEST,
    ): PageResult.Document {
        val id = values.first(savedId)?.toLongOrNull()?.takeIf { it > 0 }
        val (records, existing) = withContext(Dispatchers.IO) { store.page(1) to id?.let(store::find) }
        return savedInvoiceDocument(SavedInvoiceView(language, records, 1, save, existing, values, errors, status))
    }
}

private fun SavedInvoiceFields.hasExpectedShape(fields: FormValues): Boolean {
    val expectedRows = listOf(servicesFields, materialsFields)
    val validRevision = if (id == null) revision == 0L else id > 0 && revision > 0
    val validKeys =
        rows.map { it.key } == expectedRows.map { it.key } &&
            expectedRows.all { fields.first(it.keyName) == it.key.toString() }
    return validRevision && validKeys && rows.none { it.receipt != null }
}

private fun InvoiceValidation.errors(language: InvoiceLanguage): List<FormError<SavedInvoiceFields>> =
    when (this) {
        is InvoiceValidation.Invalid ->
            issues.map { (name, message) ->
                error(language, nestedInvoiceFields.getValue(name), message)
            }
        is InvoiceValidation.Valid ->
            if (values.amount.precision() > MAX_INVOICE_PRECISION) {
                listOf(error(language, savedAmount, "savedPrecision"))
            } else {
                emptyList()
            }
    }

private fun InvoiceLanguage.allocations(input: InvoiceAllocationFields): InvoiceAllocations? {
    if (input.amounts.size != 2) return null
    val first = amount(input.amounts[0])?.takeIf { it.precision() <= MAX_INVOICE_PRECISION }
    val second = amount(input.amounts[1])?.takeIf { it.precision() <= MAX_INVOICE_PRECISION }
    return if (first != null && second != null) InvoiceAllocations(first, second) else null
}

private fun UploadedFile.checkedReceipt(): ByteArray? {
    val bytes = read { it.readAllBytes() }
    val body =
        try {
            Charsets.UTF_8
                .newDecoder()
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            return null
        }
    return bytes.takeIf { body.isNotBlank() && body.none { it.isISOControl() && it !in "\n\r\t" } }
}

private fun error(
    language: InvoiceLanguage,
    field: FormField<SavedInvoiceFields>?,
    key: String,
): FormError<SavedInvoiceFields> = FormError(field, language.messages.getString(key))
