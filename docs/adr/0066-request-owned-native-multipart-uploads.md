# ADR 0066: Own bounded native multipart uploads for one request

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#60](https://github.com/christian-draeger/woge/issues/60), [#122](https://github.com/christian-draeger/woge/issues/122)
- Builds on: [ADR 0053](0053-bounded-native-form-decoding.md), [ADR 0054](0054-native-form-validation-boundary.md), [ADR 0065](0065-request-owned-resource-admission.md)

## Context

An ordinary HTML file input needs `multipart/form-data`, not a patch protocol or JavaScript upload
client. Framework multipart parsers have different buffering, limits and temporary-file lifetimes.
Spring MVC can parse a body before Woge's action security boundary. Accepting that already-consumed
body would make the same action behave differently across hosts.

## Decision

One incremental reader in the host SPI parses raw multipart bytes for MVC, WebFlux and Ktor.
Text goes through the existing serializer-based form validation. Files stream into randomly named
private temporary files, never into an unlimited in-memory body. Filename and media type remain
untrusted metadata; neither chooses a path or grants permission.

`MultipartSubmission<Command>` contains the bounded `FormSubmission<Command>` and request-owned
`UploadedFile` resources. `UploadedFile.read { stream -> ... }` closes the stream on return.
The application authorizes access and copies accepted content to its own storage while the action
runs. Returning an HTML document or deferred response does not extend the upload lifetime.

`@WogeAction` accepts this one explicit wrapper around an otherwise supported flat form command.
Generated descriptors preserve the complete input type and track its command source dependencies.
`multipartActionForm` accepts an upload descriptor and locks its POST method, action URL and
encoding. It renders ordinary HTML; no Node toolchain or JavaScript is required.

`UploadLimits` defaults to 16 MiB request bytes, 8 MiB per file, 16 MiB total temporary file bytes,
eight files and 8192 header bytes per part. Existing `FormLimits` bound text field count, names,
values and total text bytes. Exact thresholds are accepted. Overflow stops before the next byte
is written and returns 413; malformed multipart returns 400 and other media types return 415.
Diagnostics carry only category, allowance name and threshold, not submitted content or paths.

Each adapter creates authentication and verified authenticity facts before reading upload bytes.
Domain authorization remains inside the action. Woge does not install a CSRF policy or assume that
authentication proves authenticity. Host security must keep the raw body readable; token-in-body
preparation, if needed, must be bounded and own cleanup even when security rejects the request.

The raw reader is closed on partial input, decoding failure and disconnect. After decoding,
the action handler owns the submission and closes it on success, authorization rejection,
application failure and cancellation, before rendering any lazy response. Cleanup errors propagate;
an existing application failure keeps cleanup errors as suppressed exceptions.

MVC requires an explicit `multipartResolver` bean using `WogeMultipartResolver` for registered
upload paths. Those paths bypass eager Servlet parsing; ordinary Spring controllers retain a
delegated resolver. Already-parsed requests fail explicitly instead of bypassing Woge's limits.
MVC decodes in its existing async lifecycle. WebFlux releases raw buffers and offloads temporary-file
writes; Ktor closes its receive channel. None uses a host-specific file abstraction in domain code.

## Alternatives considered

- **Use each host's multipart parser:** different resource limits and ownership; MVC may consume
  input before Woge's security boundary.
- **Collect the body before enforcing limits:** fails precisely when limits are most needed.
- **Expose submitted paths or retain files with a response:** permits unsafe path handling or
  extends storage lifetime into lazy rendering.
- **Build an enhanced upload protocol now:** unnecessary for native forms; progress and explicit
  cancellation UI belong to #119.

## Consequences

The same real-HTTP and JavaScript-disabled/enabled browser journeys run on all three hosts.
Split-input, binary content, exact allowances, malformed headers, errors, cancellation and mid-body
disconnect tests prove cleanup. Framework security and durable content storage remain application
responsibilities. Process crashes can leave temporary files; this request lifecycle is not a
process-wide storage quota or janitor.

## Follow-up

File bytes never enter actions' patch payloads. Advanced forms, resumable uploads, progress and
client-side chunking are not part of this API. Application/session-wide admission and production
SSE budgets remain open under #122.
