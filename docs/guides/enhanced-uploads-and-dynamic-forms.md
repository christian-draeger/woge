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
        cancelled = "Upload cancelled. Submit again to retry.",
        failed = "Upload failed. Submit again to retry.",
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

## Evidence and limits

[ADR 0104](../adr/0104-enhanced-uploads-and-dynamic-forms.md) records the policy.
The adapter TCK covers all three hosts, including browser abort and unavailable-module/no-JS fallback.
The optional asset has its own fixed budget; existing core ceilings are unchanged. Resuming, chunking,
background uploads and undoing an already-committed action are not provided.
