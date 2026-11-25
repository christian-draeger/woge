# Upload progress and dynamic form rows

Start with [native multipart uploads](native-multipart-uploads.md). Keep a normal file input,
multipart POST and typed action URL. File contents never go through a patch stream.
This guide adds optional progress, cancellation and nested fields; no new module is required.

## Opt into progress on one page

Add `woge-ui-headless` to the application's dependencies. Load `/assets/woge-ui/upload.js` as a
`type="module"` script **only on pages using this feature**. Serve that classpath asset through
your existing Spring or Ktor static-resource configuration. Do not add it to the core client bundle.

```kotlin
val meter = UiId("attachment-progress")
script(attributes = {
    attribute("type", "module")
    url("src", applicationUrl(UPLOAD_MODULE_PATH))
})
multipartActionForm(SaveAttachmentAction, attributes = { enhancedUpload(meter) }) {
    label(attributes = { attribute("for", "attachment") }) { text("Attachment") }
    input(attributes = {
        attribute("id", "attachment")
        attribute("name", "attachment")
        attribute("type", "file")
    })
    button(attributes = { attribute("type", "submit") }) { text("Save attachment") }
    uploadProgress(meter, "Upload progress", "Cancel upload", UploadMessages(
        started = "Uploading",
        cancelled = "Upload cancelled. Check saved attachments before submitting again.",
        failed = "The upload result could not be confirmed. Check saved attachments before retrying.",
        complete = "Uploaded",
    ))
}
```

The action should return a normal **303 redirect after success**. The optional module sends multipart
with XHR and navigates to that GET destination; it does not inject a server HTML string or send patch
frames. Validation/error responses leave the existing form available for an intentional native retry.
Do not combine `data-woge-action` with this upload opt-in.

The labeled progress element starts without a percentage when the total is unknown. The polite
status speaks progress at most once per second. Translate the messages and labels in your application.
Cancel is a real keyboard-accessible button. It aborts transport and returns focus to the submit button.
Cancel or failure disables enhancement for the next submission without clearing any selected file.
There is no automatic retry: a disconnected request may already have committed on the server.
Without JavaScript, or if the optional asset fails to load, the original form submits normally.

## Choose temporary storage and permissions explicitly

The old adapter defaults still work. For explicit configuration, build one policy and pass it to the
host binding (`springMvcMultipart(policy)`, `webFluxMultipart(policy)` or `ktorMultipart(policy)`).

```kotlin
val uploads = UploadPolicy(
    limits = UploadLimits(fileBytes = 4L * 1024 * 1024, requestBytes = 8L * 1024 * 1024),
    temporaryFiles = { DirectoryUploadTemporaryFiles(applicationUploadDirectory) },
    authorize = { metadata -> metadata.field == "attachment" },
)
val input = decoder.springMvcMultipart(uploads)
```

Create the configured directory yourself with restricted filesystem permissions. The factory must
return a **fresh request-owned** store. Implement `UploadTemporaryFiles.create()` and `close()` when
using another local storage policy: create private random paths, and delete them in `close()`.
That close hook runs after handling, failures, rejected input and cancellation/disconnect. Keep it
idempotent and let cleanup failures propagate. A process crash still needs your application's janitor.

`authorize` sees untrusted field/filename/media-type metadata before file creation. Use it for allowed
parts; never trust a filename as a path or media type as proof of content. This is additional authorization,
not a replacement for ingress authentication/CSRF or domain permissions checked inside the action.
All three hosts reuse the streaming per-file, request, file-count, header and total temporary-byte limits.
MVC still needs `WogeMultipartResolver` on the upload action paths to avoid eager Servlet parsing.

## Name nested rows with typed paths

```kotlin
@Serializable
data class EditOrder(val rows: List<OrderRow>)

@Serializable
data class OrderRow(val key: Int, val title: String, val quantities: List<Int>)

val decoder = FormDecoder(EditOrder.serializer())
val key = FormRowKey.of(7)
val title = decoder.path(EditOrder::rows).row(key).child(OrderRow::title)
    .field(FormElementId.of("row-7-title"))
```

`title.name` is `rows[7].title`; use `formField(title, errors)` on the control and share the same field
with its error renderer. Property names follow the serializer. Supply `serializedName` when a property
uses `@SerialName`. For quantities, repeated `rows[7].quantities` controls still bind as a scalar list.
Nested list items also have typed indexed paths. Schemas are limited to eight levels and cannot recurse.

Render an explicit `rows[7].key` control too, so your application can retain the row key across handling
and validation. Allocate a new nonnegative key for each new row; never renumber existing rows after a
removal. The decoder accepts sparse keys and sorts them numerically. Preserve a separate order field
if user-controlled ordering matters. Treat submitted keys as untrusted and check ownership on the server.

For add/remove workflows, use ordinary named submit buttons and native POST actions that render the
updated form. Keep the remaining row keys and bounded submitted text when rendering errors. This also
works without JavaScript; the framework does not require a client-side row template engine. A native
page navigation cannot retain a selected file: ask for the file after row editing or explain reselection.

## Localized text needs a configured locale

Ordinary HTML date and number controls send canonical values. Keep that default where possible.
For localized **text** controls, explicitly select the locale, date pattern and serializer field paths:

```kotlin
val decoder = FormDecoder(
    EditInvoice.serializer(),
    FormLocalization(Locale.GERMANY, "dd.MM.uuuu", setOf("rows[].amount", "rows[].date")),
)
```

Here `1.234,5` becomes `1234.5` and `11.10.2026` becomes the ISO date string `2026-10-11`.
Use `uuuu` for the year in strict date patterns. Listed string properties are dates; listed numeric
properties are numbers. Unlisted fields retain canonical parsing. Incorrect paths or an empty/root
locale fail during configuration. Bad calendar dates, partial numbers and invalid grouping produce
normal field errors, never silently use the machine's default locale. Keep locale selection tied to your
application's explicit preferences rather than guessing from the runtime environment.

## Validate a localized invoice

The [compiled invoice example](../../examples/invoice-desk/src/main/kotlin/dev/woge/examples/invoice/LocalizedInvoice.kt)
uses Woge's typed form decoder and error renderers. It keeps text in the command until the application
can validate it against the server-selected language. US English, German, and Egyptian Arabic use
the same EUR currency. Arabic uses Arabic-Indic digits and sets right-to-left direction on the
document, the form, and the enhanced confirmation.

<!-- snippet: examples/invoice-desk/src/main/kotlin/dev/woge/examples/invoice/LocalizedInvoice.kt -->

| Field | German wire | English wire | Arabic wire | Application value |
| --- | --- | --- | --- | --- |
| EUR amount in a text input | `1234,50` | `1234.50` | `١٢٣٤٫٥٠` | Exact `BigDecimal("1234.50")` |
| Service date in a native date input | `2026-10-11` | `2026-10-11` | `2026-10-11` | `LocalDate` |
| Due date in a localized text input | `11.10.2026` | `10/11/2026` | `١١/١٠/٢٠٢٦` | `LocalDate` |
| Berlin payment time in a text input | `2026-10-11T12:30` | `2026-10-11T12:30` | `2026-10-11T12:30` | `Instant` displayed as `2026-10-11T10:30:00Z` |

The amount parser accepts the active locale's digits and decimal separator, with up to two fractional
digits. It rejects grouping separators, mixed digit systems, and scientific notation. The service
date's native control sends an ISO calendar value. The due date uses a strict localized calendar
pattern. Both represent dates independently of time zones.

The [invoice localization policy](../../examples/invoice-desk/src/main/kotlin/dev/woge/examples/invoice/InvoiceLocalization.kt)
fixes the payment zone to `Europe/Berlin`. The left-to-right text input sends
an ASCII local date and time at minute precision and keeps malformed submissions visible. The server
requires exactly one valid offset under the JDK zone rules. It rejects missing times during the spring
transition and repeated times during the autumn transition with distinct translated field errors.
For example, both `2026-03-29T02:30` and `2026-10-25T02:30` require a correction. Posted offsets and
zone identifiers are invalid in this field. The confirmation displays the resulting UTC instant.

Resolve the language on the server. Spring hosts already use the
[resolved Spring locale](spring-boot-starter.md#use-spring-locale-identity-and-sessions). The form
posts its display language as a stale-form check. If the server language changed since rendering,
the action asks the user to review the fields and keeps the submitted text. The body cannot select
the parsing locale. The example uses JDK resource bundles for labels, errors and announcements.

Mount `localizedInvoicePage` at `/localized-invoice` and the submission action at
`ValidateLocalizedInvoiceAction.path`. Use the decoder's submission binding from the
[typed actions guide](typed-actions.md#render-native-field-errors) so the action
receives both the decoded command and bounded submitted text.

The same validation result provides a 400 native document or an enhanced replacement with a
focusable summary. Invalid text is escaped and remains associated with its field. Browsers can make
an invalid date control appear empty even when a tampered request's date string was retained in the
HTML value attribute. A successful result provides a native 303 or an enhanced confirmation. The
fixture validates the invoice and prepares that response. Applications supply their own persistence.

Run `./gradlew :woge-invoice-desk:test --tests '*LocalizedInvoice*Test'` to verify the portable
fixture and the [shared WebFlux and Ktor HTTP journey](../../examples/invoice-desk/src/test/kotlin/dev/woge/examples/invoice/LocalizedInvoiceHttpTest.kt).
WebFlux uses Spring's `AcceptHeaderLocaleContextResolver` for the three supported locales.
The [Ktor binding](../../examples/invoice-desk/src/main/kotlin/dev/woge/examples/invoice/InvoiceDesk.kt)
selects the highest-weight supported exact tag and retains the adapter's same-origin security facts.
Both default to US English. Tests on both hosts cover preserved values, translated errors and
corrected native and enhanced submissions, including locale changes to English and Arabic. An
enhanced stale-form replacement carries its own language and direction until the next full document
navigation. Tests on both sides of each Berlin transition check exact UTC instants.

For the [manual reference review](https://github.com/christian-draeger/woge/issues/253):

- Open the Arabic form. Check right-to-left labels, tab order, readable Arabic-Indic amounts, and the
  left-to-right ISO fields. Switch the browser's preferred language before submitting and review the
  direction and preserved input after replacement.
- Submit a payment time inside a Berlin gap or overlap. With a keyboard, follow the summary link,
  correct the value, and submit again. Repeat with JavaScript disabled through the native response.
- With a screen reader, verify the form language, summary and linked field error, and the accepted
  announcement with the UTC instant.

## Save and edit a localized invoice with a receipt

Run the [standalone Ktor invoice desk](../../examples/invoice-desk/README.md) to try this workflow.

The [saved-invoice application](../../examples/invoice-desk/src/main/kotlin/dev/woge/examples/invoice/SavedInvoiceForm.kt)
reuses the same locale policy, strict invoice parsing, and property bundles. Its two nested rows
have stable keys `7` and `42`. Each row sends two controls with the same typed `amounts` field name.
Those repeated values follow the total amount's decimal rules. The keys are explicitly checked
before saving. The service receipt uses the nested multipart field `rows[7].receipt`. The upload
policy permits only that field and bounds the request and temporary file. The action checks UTF-8
text contents and stores the accepted bytes without using the supplied filename or media type.

The [application-owned JDBC store](../../examples/invoice-desk/src/main/kotlin/dev/woge/examples/invoice/SavedInvoiceStore.kt)
uses an embedded H2 file, exact `DECIMAL(18,2)` amounts, calendar dates, and UTC instants. Its
atomic update includes the displayed domain revision. A stale edit returns 409 and the saved record
keeps its previous values and receipt. An edit without a new file retains the stored receipt.
The store has a fixed two-row schema for the example, and it runs off the host event loop.
The list pages and file/status labels come from the same three application `ResourceBundle`s.
[H2 data types](https://h2database.com/html/datatypes.html)

<!-- snippet: examples/invoice-desk/src/main/kotlin/dev/woge/examples/invoice/SavedInvoiceForm.kt -->

Both hosts bind the typed multipart input and use the existing same-origin request facts. The
fixture runs on loopback and has a shared application store. A private deployment also needs
its usual sign-in and per-invoice authorization. The optional upload module sends XHR and follows
the normal 303 redirect after successful creation or editing. A 400 leaves the enhanced draft and
selected file in place. The status tells the user to check saved invoices before retrying in the
standard form. A deliberate native submission renders the translated summary and field errors with the
bounded literal text. A native navigation requires selecting the file again. File bytes stay out
of response patches in both modes.

Run the [same real-server tests on WebFlux and Ktor](../../examples/invoice-desk/src/test/kotlin/dev/woge/examples/invoice/SavedInvoiceHttpTest.kt).
They check all three locales, the exact stored receipt bytes, a rejected stale edit, and data
reopened through the other host after the first server stops. The extra browser run uses the
installed Playwright Chromium and checks all three languages in both JavaScript modes on both hosts.

```sh
./gradlew :woge-invoice-desk:test --tests '*SavedInvoiceHttpTest' --tests '*LocalizedInvoice*Test'
npm --prefix client/woge-fallback-client ci --ignore-scripts
npm --prefix client/woge-fallback-client exec -- playwright install chromium
WOGE_INVOICE_BROWSER=true ./gradlew :woge-invoice-desk:test --tests '*SavedInvoiceHttpTest'
```

For [the manual reference review](https://github.com/christian-draeger/woge/issues/253), navigate
both repeated Arabic allocation inputs and the services receipt using the keyboard. Check that the
summary link takes the user to the appropriate allocation or file control. With a screen reader,
check both repeated controls announce the shared row error, the upload-failure message and upload
cancellation are announced in Arabic, and the next-page link and record values are understandable.
Repeat with JavaScript disabled. The browser checks exercise visible behavior on Chromium. This
checklist records the remaining human assistive-technology review. The saved-invoice sample does
not add a locale-aware formatter to the core DSL or translate the browser's built-in file picker.

## Evidence and limits

[ADR 0104](../adr/0104-enhanced-uploads-and-dynamic-forms.md) records the policy.
The adapter TCK covers all three hosts, including browser abort and unavailable-module/no-JS fallback.
The optional asset has its own fixed budget; existing core ceilings are unchanged. Resuming, chunking,
background uploads and undoing an already-committed action are not provided.
