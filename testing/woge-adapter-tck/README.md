# Woge server-adapter TCK

This test-support module owns the framework-neutral compatibility contract for Woge server adapters.
An adapter test supplies an `AdapterTckHarnessFactory` that binds the shared `AdapterTckApplication`
to the canonical paths on an ephemeral real HTTP server. `ServerAdapterContract.verify()` owns the
requests and assertions; it never imports a Spring, Reactor, Servlet or Ktor type.

The initial contract covers page metadata and request mapping, GET and HEAD, redirects, controlled
and pre-stream failures, document flush boundaries, deferred completion order and client-abort
cancellation. Additive `AdapterTckExtension` suites are the hook for actions, CSRF, caching,
multipart and SSE when those capabilities become executable.

Native multipart is now part of the shared real-HTTP contract. Bind the generated `TckUploadAction`
with `application.uploadForm`, `application.uploadLimits` and `application.uploadDirectory`, and
register `tckUploadPage` at `/woge-tck/upload-complete`. MVC also registers `WogeMultipartResolver`
for the upload action. The contract checks typed text and binary file content, native redirect, authorization
and authenticity rejection, malformed/oversized input, application failure and mid-body disconnect.
The application owns a dedicated temporary parent and verifies that every journey leaves it empty.
The optional native-form browser gate submits a real file with JavaScript disabled and enabled.

HTTP caching is part of the core contract: explicitly cacheable pages preserve validators and Vary
on bodyless GET/HEAD 304 responses, skip frame collection and never bypass current authorization.
Dynamic results without an explicit policy use no-store. See the
[HTTP caching guide](../../docs/guides/http-caching.md).

Resource exhaustion is part of the core contract: deferred admission must reject before work,
HTML byte overflow must stop later frames, and patch byte overflow must not emit a successful
completion frame. Bind `application.observer` on page/deferred handlers and
`application.deferredPatchStreamLimits` on the deferred handler. This deliberately small fixture
allowance proves host override wiring and incremental rejection without allocating multi-megabyte
test responses. It is not the production default.

See the [server-adapter parity matrix](../../docs/architecture/server-adapter-parity.md) for contract
ownership and current adapter coverage.
