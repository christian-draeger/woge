# ADR 0032: Stream Spring MVC responses through adapter-owned Servlet async handlers

- Status: Accepted
- Date: 2026-09-06
- Decision owners: Woge maintainers
- Related issues: [#67](https://github.com/christian-draeger/woge/issues/67), [#65](https://github.com/christian-draeger/woge/issues/65), [#24](https://github.com/christian-draeger/woge/issues/24), [#122](https://github.com/christian-draeger/woge/issues/122), [#124](https://github.com/christian-draeger/woge/issues/124)

## Context

Spring MVC must run the same `PageUseCase` and `DeferredRegionsUseCase` implementations as WebFlux
without exposing Servlet types through the host SPI. It must release the initial request thread,
preserve Woge frame flushes and keep outstanding region work under one cancellable request lifetime.

Servlet output writes remain blocking even during asynchronous request processing. The Servlet API
also has no proactive remote-disconnect notification: an application learns about an idle disconnect
when a later write fails. Treating MVC as if it had WebFlux subscriber cancellation would create a
false portability promise.

The reference application's HTML, CSS and browser module are host-neutral. Adding an MVC launcher
should not fork those sources merely because startup and routing differ.

## Decision

`woge-spring-mvc` exposes adapter-owned `HttpRequestHandler` implementations created by
`WogeSpringMvcHandlers`. Applications register those handlers with `SimpleUrlHandlerMapping` and
supply a small `SpringMvcPageInput` decoder. Routes and request decoding stay visible Spring MVC
code, while applications own no Woge transport controller. Page handlers admit GET and HEAD;
deferred handlers admit GET and return a bodyless 405 plus `Allow` for other methods.

The adapter snapshots input and `RequestContext` on the Servlet request thread, calls
`startAsync(request, response)`, and runs the portable use case in one request coroutine. The default
dispatcher is `Dispatchers.IO`; applications can replace the handler-factory bean with one using an
explicit bounded and observable dispatcher. Suspended work releases its worker. HTML rendering and
Servlet writes are synchronous and therefore occupy a worker while writing.

Response status, UTF-8 content type, safe headers, cookies and redirects are applied before body
collection. Each HTML frame renders through the shared 8 KiB `StreamingHtmlSink` and then flushes the
Servlet output. Each encoded deferred patch is written and flushed before collection continues.
There is no page-sized adapter buffer or unbounded producer queue. Container and proxy buffering are
deployment concerns that must be tested separately.

An async listener cancels the coroutine on timeout and container error. Failed writes propagate into
the collecting coroutine and cancel structured deferred children. Normal completion completes the
`AsyncContext`. MVC does not advertise the TCK's passive client-abort capability because a closed,
idle socket is not observable until another write; later live protocols need heartbeats to bound that
latency.

The Spring Boot integration conditionally supplies `DefaultSpringMvcRequestContextFactory` and
`WogeSpringMvcHandlers` for Servlet applications. `woge.mvc.async-timeout` defaults to 60 seconds,
separate from the 30-second per-region timeout. Both beans back off for application replacements.

The maintained reference application's CSS, JavaScript and fallback runtime move to the shared
consumer project. MVC and WebFlux launchers contain only stack-specific startup, routing,
configuration and real-server tests. This amends the initial WebFlux-only asset ownership described
by [ADR 0031](0031-root-build-spring-boot-quickstart-consumer.md) without changing its separated
consumer architecture.

## Alternatives considered

- **Require an application controller returning `StreamingResponseBody`:** rejected because every
  application would repeat response mapping and coroutine lifecycle code.
- **Use `ResponseBodyEmitter` for Woge patch bytes:** rejected because Woge already owns framing and
  direct byte writes avoid message-converter behavior at the protocol boundary.
- **Expose Reactor and let MVC adapt it:** rejected because MVC would depend on the WebFlux execution
  model and the public adapter would no longer be a genuine Servlet implementation.
- **Implement Servlet `WriteListener` non-blocking I/O now:** rejected because the synchronous HTML
  sink would require a substantially more complex resumable state machine. It would not make the
  surrounding Spring MVC application non-blocking.
- **Advertise immediate passive disconnect cancellation:** rejected because Servlet provides no such
  signal and the TCK could pass only through fixture-specific behavior.
- **Duplicate browser assets per launcher:** rejected because host parity would acquire multiple
  sources of truth.

## Consequences

### Positive

- The same application object and HTML source run on MVC and WebFlux.
- MVC applications use familiar URL mappings and no generated transport controller.
- Real HTTP tests prove shell-frame visibility, patch completion order and metadata parity.
- Buffering and thread ownership are explicit and configurable.
- Timeouts, container failures and failed writes cancel outstanding structured work.
- Boot auto-configuration and IDE metadata make MVC a first-class selectable adapter.

### Negative

- Blocking Servlet writes consume an execution worker and need capacity planning.
- Passive disconnect cancellation can lag until the next protocol write.
- Proxy or container buffering can hide application flushes in a deployment.
- Routes still need small hand-written input decoders until typed route generation lands.

## Follow-up

- Run shared browser journeys across MVC, WebFlux and Ktor in [#24](https://github.com/christian-draeger/woge/issues/24).
- Define hard pending-write and frame budgets in [#122](https://github.com/christian-draeger/woge/issues/122).
- Complete canonical pre/post-commit diagnostics in [#124](https://github.com/christian-draeger/woge/issues/124).
- Add heartbeat and long-lived live-update policy with the M2 protocol work.
