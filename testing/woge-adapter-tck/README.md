# Woge server-adapter TCK

This test-support module owns the framework-neutral compatibility contract for Woge server adapters.
An adapter test supplies an `AdapterTckHarnessFactory` that binds the shared `AdapterTckApplication`
to the canonical paths on an ephemeral real HTTP server. `ServerAdapterContract.verify()` owns the
requests and assertions; it never imports a Spring, Reactor, Servlet or Ktor type.

The initial contract covers page metadata and request mapping, GET and HEAD, redirects, controlled
and pre-stream failures, document flush boundaries, deferred completion order and client-abort
cancellation. Additive `AdapterTckExtension` suites are the hook for actions, CSRF, caching,
multipart and SSE when those capabilities become executable.

Resource exhaustion is part of the core contract: deferred admission must reject before work,
HTML byte overflow must stop later frames, and patch byte overflow must not emit a successful
completion frame. Bind `application.observer` on page/deferred handlers and
`application.deferredPatchStreamLimits` on the deferred handler. This deliberately small fixture
allowance proves host override wiring and incremental rejection without allocating multi-megabyte
test responses. It is not the production default.

See the [server-adapter parity matrix](../../docs/architecture/server-adapter-parity.md) for contract
ownership and current adapter coverage.
