# ADR 0033: Bind Woge to idiomatic suspending Ktor routes

- Status: Accepted
- Date: 2026-09-06
- Decision owners: Woge maintainers
- Related issues: [#68](https://github.com/christian-draeger/woge/issues/68), [#24](https://github.com/christian-draeger/woge/issues/24), [#122](https://github.com/christian-draeger/woge/issues/122), [#124](https://github.com/christian-draeger/woge/issues/124)

## Context

Ktor must prove that Woge's host SPI and portable page code do not depend on Spring, Reactor or the
Servlet API. The integration should still look like normal Ktor to a Ktor developer: paths, query
parameters, authentication plugins and application startup belong to the host application.

Woge HTML writers are intentionally synchronous while Ktor response channels suspend. Ktor's Netty
engine can stream and flush a response incrementally, but it does not notify a handler reliably when
an idle peer disappears. Like Servlet, it observes that condition no later than a subsequent failed
write. The adapter must describe that limitation honestly instead of passing a fixture through a
transport-specific workaround.

Spring Boot is Woge's primary onboarding and enterprise integration. Ktor is a first-class maintained
adapter and portability proof, not a replacement product direction. RPC, including `kotlinx.rpc`, is
outside this HTTP and HTML adapter boundary.

## Decision

`woge-ktor` exposes `WogeKtorHandlers`, a route-local `KtorPageInput` decoder and a replaceable
`KtorRequestContextFactory`. Applications call the resulting suspending handlers from ordinary
`get` and `head` routes. `ApplicationCall` remains confined to adapter/bootstrap code; portable
`PageUseCase` and `DeferredRegionsUseCase` implementations receive only Woge host types.

The default request-context factory admits GET, HEAD and OPTIONS, creates request-local trace IDs,
copies parsed cookies and accepted language, and omits raw authentication, cookie and CSRF headers.
Authenticated or unsafe routes require an application factory that translates verified Ktor plugin
state into immutable Woge security facts.

Page metadata is applied before collection. Ktor's `respondBytesWriter` owns one suspending response
producer. Each HTML frame renders through Woge's shared 8 KiB chunking sink, its chunks are written,
and the channel is flushed before the next frame is collected. Patch chunks are written and flushed
one at a time. This preserves Woge flush semantics and Ktor backpressure. One HTML frame is currently
retained while crossing the synchronous-writer/suspending-channel boundary; hard frame budgets remain
the responsibility of [#122](https://github.com/christian-draeger/woge/issues/122).

Status, content type, headers, cookies and redirects derive from the same `ResponseMetadata` as the
Spring adapters. HEAD returns identical document metadata without collecting body frames. A use-case
failure before streaming is logged server-side and becomes a bodyless 500 so private exception text
cannot reach the client. Controlled failures remain typed and bodyless.

The response producer collects the deferred-region flow in the Ktor request coroutine. Coroutine
cancellation and downstream write failures propagate unchanged and cancel its structured children.
The Netty harness does not advertise the adapter TCK's passive client-abort capability: closing an
idle response is not itself a deterministic server signal until another write occurs. Later
long-lived protocols need heartbeat policy if they require a bounded disconnect-detection delay.

The adapter uses Ktor 3.5.2 and its Netty engine in tests and the example. The shared adapter TCK runs
over a real ephemeral HTTP listener. A separate executable Ktor launcher reuses the exact project
page, markup and assets used by Spring MVC and WebFlux.

## Alternatives considered

- **Hide routing behind a generated Ktor application plugin:** rejected because normal routes and
  host-owned authentication should remain visible. Generated typed route helpers can be additive.
- **Expose Ktor types to page code:** rejected because it would invalidate host portability and make
  the shared TCK application impossible.
- **Buffer an entire document before responding:** rejected because delayed frames would block the
  shell and erase the streaming contract.
- **Run a blocking bridge around `ByteWriteChannel`:** rejected because it would bypass Ktor's
  suspending backpressure and complicate cancellation.
- **Advertise immediate passive client-abort cancellation:** rejected because Netty cannot prove it
  while an idle response performs no I/O.
- **Use RPC as the page transport:** rejected because links, forms, HTML, HTTP status and progressive
  enhancement are the first-class interface. RPC may only be reconsidered for a separate capability.

## Consequences

### Positive

- The same application and HTML source now run on Spring WebFlux, Spring MVC and Ktor.
- Ktor developers keep familiar routing, plugins, request decoding and server lifecycle.
- A suspending response producer preserves backpressure and structured cancellation.
- The real-server TCK proves metadata, safe failures, HTML flushes and patch completion order.
- The public adapter API is small, compiler-visible and ABI-checked.

### Negative

- One HTML frame is retained as bounded chunks before a suspending channel write.
- Passive disconnect cancellation can lag until the next write.
- Applications still write small route-input decoders until typed route generation exists.
- Spring Boot and Ktor require separate bootstrap code even though their page code is shared.

## Follow-up

- Run shared browser journeys across all three hosts in [#24](https://github.com/christian-draeger/woge/issues/24).
- Define hard pending-write and frame budgets in [#122](https://github.com/christian-draeger/woge/issues/122).
- Complete canonical pre/post-commit diagnostics in [#124](https://github.com/christian-draeger/woge/issues/124).
- Add heartbeat policy only with a protocol that genuinely needs long-lived idle responses.
