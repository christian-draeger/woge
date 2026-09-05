# Woge server-adapter TCK

This test-support module owns the framework-neutral compatibility contract for Woge server adapters.
An adapter test supplies an `AdapterTckHarnessFactory` that binds the shared `AdapterTckApplication`
to the canonical paths on an ephemeral real HTTP server. `ServerAdapterContract.verify()` owns the
requests and assertions; it never imports a Spring, Reactor, Servlet or Ktor type.

The initial contract covers page metadata and request mapping, GET and HEAD, redirects, controlled
and pre-stream failures, document flush boundaries, deferred completion order and client-abort
cancellation. Additive `AdapterTckExtension` suites are the hook for actions, CSRF, caching,
multipart and SSE when those capabilities become executable.

See the [server-adapter parity matrix](../../docs/architecture/server-adapter-parity.md) for contract
ownership and current adapter coverage.
