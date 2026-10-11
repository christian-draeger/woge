# ADR 0104: Enhance native uploads and bind stable dynamic form rows

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#119](https://github.com/christian-draeger/woge/issues/119)
- Builds on: [ADR 0066](0066-request-owned-native-multipart-uploads.md),
  [ADR 0048](0048-document-owned-accessibility-announcements.md),
  [ADR 0053](0053-bounded-native-form-decoding.md)

## Context

Native multipart already provides bounded streaming, request-owned cleanup and a dedicated POST
endpoint across MVC, WebFlux and Ktor. Progress must not turn files into Patch IR messages or add
bytes to the nearly full core browser bundle. Repeated scalar fields alone cannot describe nested rows.

## Decision

`woge-ui-headless` ships a separately budgeted, dependency-free `upload.js`. Only pages rendering
`enhancedUpload` and `uploadProgress` load it. It sends ordinary multipart using XMLHttpRequest,
including the submitting button, with `Accept: text/html`. Successful actions use normal
POST/303/GET; the browser navigates to the returned same-origin GET destination. No patch upload
protocol, automatic mutation replay, HTML injection or new core-client dependency is introduced.
A direct HTML response instead of a redirect is not enhanced: it shows the failure message and leaves
an intentional native submission available. Applications choosing enhancement should use PRG on success.

A labeled native progress element is initially indeterminate. A document-owned polite status reports
progress at most once per second and one terminal result. This is an explicit progress-specific
extension to ADR 0048's one-outcome announcement rule, not permission to announce every byte or
background update. The form is busy only during the request. A real button calls XHR abort; if it
owns focus, focus returns to the submit button before the cancel control hides. Failure/cancel removes
the enhancement marker, restores submit semantics and retains controls and selected files. Unavailable
JavaScript, invalid configuration and unsupported native overrides never intercept the form.

`UploadPolicy` reuses `UploadLimits` and adds a fresh `UploadTemporaryFiles` factory plus part
metadata authorization before file creation/content storage. `DirectoryUploadTemporaryFiles` is the
simple filesystem default. Hosts retain their existing pre-body authentication/CSRF and action
cleanup boundaries. Custom stores own their close hook on success, failure, partial input and cancel;
application/domain authorization remains mandatory. Per-file, total request and temporary byte limits
are still enforced while streaming, not after host buffering. Existing adapter overloads remain intact.

The form decoder and action generator accept nested data classes and lists, bounded to eight descriptor
levels. `FormPath.child` follows serializer properties and `FormPath.row(FormRowKey)` creates indexed
names, such as `rows[7].title`. Numeric keys may be sparse and remain unchanged when another row is
removed. Binding sorts existing keys without allocating an array sized by a submitted index. Keys
are untrusted form data, not database permissions. Applications retain them as explicit row properties
when rendering validation, add/remove actions or reordered rows. Repeated scalar lists remain supported.

`FormLocalization` requires a locale, date pattern and explicit field paths (with `[]` for rows).
No JVM default locale is consulted. Selected date strings normalize to ISO dates with strict calendar
validation; numbers require complete parsing and valid grouping. Invalid configuration fails loudly;
invalid submitted values are ordinary field errors. Native date/number controls keep their canonical
wire formats unless the application explicitly selects localized text input behavior.

## Alternatives considered

- Add progress to the core client: rejected because the core budget has almost no headroom.
- Put files in patch frames: rejected; native multipart already has dedicated endpoints and streaming limits.
- Use framework-specific upload storage: rejected; it would diverge across MVC, WebFlux and Ktor.
- Rename row indices after every removal: rejected; it breaks existing field names and error references.
- Infer locale from the JVM: rejected; identical forms would parse differently between deployments.

## Consequences

The core byte ceiling stays unchanged. Cross-host HTTP/browser contracts prove native fallback,
progress, abort, policy rejection, nested localized rows and cleanup. Unit tests cover typing, sparse
keys, malformed names, strict localization, cleanup hooks and announcement throttling. No new module
is needed; the TCK uses headless markup as a test-only dependency.

## Follow-up

Resuming/chunking uploads, client-side row templates and background persistence are out of scope.
Applications provide native add/remove POST actions and durable storage, authorization for row keys,
and crash-time cleanup of their configured directory. Cancel stops transport; it cannot undo an action
that already committed. Cancellation and progress should also receive manual screen-reader checks.
