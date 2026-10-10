# Native file uploads

Use a normal `<input type="file">` and a POST form with `enctype="multipart/form-data"`.
The browser handles the upload with JavaScript disabled. Upload bytes never enter patch streams,
and an application does not need Node or Vite.

The [shared executable upload action](../../testing/woge-adapter-tck/src/main/kotlin/dev/woge/tck/TckUploadCommand.kt)
shows the generated action, typed text command, native form and redirect used by every host.
It is a transport test fixture, not a production authentication configuration.

## Declare an upload action and form

An upload action takes `MultipartSubmission<YourCommand>` and `RequestContext`. Annotate it with
`@WogeAction` as in [typed actions](typed-actions.md). The command is the same flat `@Serializable`
data class used for text-only forms; do not put file bytes or host framework types in it.

KSP generates the typed descriptor. Render `multipartActionForm(YourUploadAction) { ... }` with
ordinary `label`, `input` and `button` elements. Give text and file inputs names. The helper fixes
the POST method, descriptor URL, multipart encoding and UTF-8; conflicting attributes fail.
Do not add `data-woge-action`: the current enhancement handles text-only forms, not uploads.

Inside the action:

- Make your domain authorization decision before accessing file contents.
- Inspect `submission.form.result`: `FormResult.Decoded` has the typed command;
  `FormResult.Rejected` has the same bounded field errors as text-only forms.
- Select files with `submission.files("attachment")`. A name may have zero, one or several files;
  validate your application's required count explicitly.
- Use `file.read { input -> ... }` to inspect or copy bytes to trusted application-owned storage.
  The stream closes when the block returns.
- Return an ordinary 303 redirect after success, or a native validation page after rejection.
  A browser never restores the selected file automatically after validation.

`filename` and `mediaType` are untrusted metadata. Never resolve the submitted filename under a
storage directory or trust a MIME declaration as proof of content. An empty browser file selection
may arrive as an empty file with an empty filename; decide whether that satisfies your form.

Uploads are available only while the action runs. Every host closes them when the action returns
or throws, before lazy HTML or deferred content is rendered. Copy accepted bytes during the action;
do not capture an upload in a later frame or keep its stream for background work.

## Register the host binding

Create `FormDecoder(YourCommand.serializer())` as for text-only forms, then pass one of these
inputs to `handlers.action(YourUploadAction, input, securityContexts)`:

| Host | Input |
| --- | --- |
| Spring Boot MVC | `decoder.springMvcMultipart()` |
| Spring Boot WebFlux | `decoder.webFluxMultipart()` |
| Ktor | `decoder.ktorMultipart()` |

Register the action's exact POST path using the host's normal routing API. These inputs read the raw
body incrementally; they do not accept a body already consumed by a framework multipart parser.

**MVC also needs a resolver bean:**

```kotlin
@Bean(name = ["multipartResolver"])
fun multipartResolver(): MultipartResolver =
    WogeMultipartResolver(setOf(YourUploadAction.path))
```

List every Woge upload path. The default delegate preserves normal Servlet multipart behavior for
other Spring controllers. If you already customize a resolver, pass it as the second argument.
Woge explicitly rejects a pre-parsed request rather than silently weakening its limits.

## Security stays explicit

All bindings require the host security context used by ordinary actions. Establish authentication
and verify request authenticity before Woge reads the body; an authenticated session alone is not
CSRF protection. Woge never installs or fabricates that policy. The action still authorizes the
specific domain operation after decoding.

A native browser POST cannot send an arbitrary token header without JavaScript. Choose an explicit
host strategy that supports native uploads and leaves the body readable. If your CSRF integration
reads a hidden multipart token first, its preparation step must enforce the same bounds, retain one
owned submission and close it on **every** security rejection, error or disconnect. Do not use
unbounded `getParameter`, `getParts` or full-body collection as a shortcut.

## Set request limits

Pass `limits = UploadLimits(...)` and optionally `temporaryDirectory = trustedPath` to any host
binding. The directory is an application-selected existing parent, never a submitted path.
The default uses the system temporary directory and creates a private child directory per upload.

| Allowance | Default |
| --- | --- |
| Complete request, including multipart headers and framing | 16 MiB |
| Each file | 8 MiB |
| All temporary file content in this request | 16 MiB |
| File parts | 8 |
| Headers per part, including the closing blank line | 8192 bytes |

Text fields also use `FormLimits`: 128 fields, 256-byte names, 16 KiB values and 64 KiB total text
by default. Multipart text preserves Unicode, spaces and literal plus signs; it is not URL-decoded.
Every allowance must be positive. Overflow returns 413, malformed multipart returns 400, and
non-multipart input returns 415. Diagnostics never include field content, filenames or temporary
paths. Limits do not authorize the request or make a failed mutation safe to repeat.

Partial files are removed on decoding failure, application failure, cancellation and disconnect.
This is request-owned storage, not a process-wide disk quota; a process crash may need operational
temporary-file cleanup. See [resource budgets](resource-budgets.md) for remaining boundaries.

Enhanced progress/cancellation, resumable uploads and advanced dynamic forms belong to
[#119](https://github.com/christian-draeger/woge/issues/119), not this native API.
