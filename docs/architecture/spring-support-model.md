# Spring MVC and WebFlux support model

This M0 report records the evidence and support decision for issue
[#64](https://github.com/christian-draeger/woge/issues/64). The executable fixture is the
[hand-written Spring baseline](../../spikes/spring-html-htmx-baseline/README.md).

## Decision

Spring MVC and Spring WebFlux are both first-class Woge server adapters. They provide equivalent Woge page, action, patch and live-update semantics, but Woge does not promise identical threading or resource-use characteristics.

The adapters remain separate modules. A shared Spring Boot starter owns common configuration and selects exactly one adapter from the Spring web application type. An application containing both stacks must choose explicitly rather than depending on classpath order.

Portable application code owns no Servlet, Reactor, Spring Security, `SseEmitter`, `ServerWebExchange` or Spring session type. Each adapter snapshots the required request context into Woge-owned values and maps Woge outcomes back to its host response lifecycle.

## Executable evidence

Both baseline applications now provide:

- a full semantic HTML response;
- three delayed HTML-region responses;
- native and enhanced form actions;
- equivalent validation and redirect behavior;
- a finite `text/event-stream` response with two ordered events;
- a 404 response for an unknown project before streaming begins.

MVC implements the stream with `SseEmitter`, an `AsyncTaskExecutor` and lifecycle callbacks. WebFlux returns a cold Kotlin `Flow<ServerSentEvent<String>>`. Their HTTP contract tests assert the same status, media type, event order and data.

The spike also found two differences before tests normalized them:

1. MVC accepted form-body values as individual `@RequestParam` arguments. WebFlux required a model object for equivalent form decoding.
2. A `Flow` returned from a view-oriented WebFlux `@Controller` required an explicit `@ResponseBody` boundary, while MVC has a dedicated result handler for `SseEmitter`.

## Support matrix

| Concern | Spring MVC adapter | Spring WebFlux adapter | Woge contract |
| --- | --- | --- | --- |
| Full HTML and fragments | view rendering on Servlet request | reactive view rendering | byte-equivalent semantics, headers and escaping |
| Waiting for application work | blocking work may use the request or configured worker thread | suspending work must not block the event loop | application outcome is portable; execution policy is adapter configuration |
| Streaming writes | Servlet async response; each write is blocking on a worker | non-blocking response pipeline; SSE values flush individually | ordered frames and terminal outcome are portable |
| Disconnect | detected when a write fails; heartbeat required for idle streams | subscriber cancellation propagates to the coroutine/Flow | cancellation signal reaches Woge execution; detection latency may differ |
| Error before commit | normal Spring exception/status mapping | normal reactive exception/status mapping | typed Woge failure maps to the same HTTP contract |
| Error after commit | terminate async response and diagnose | terminate publisher/connection and diagnose | no second status response; emit safe structured diagnostics |
| Form decoding | Servlet parameter binding | form model/request-data binding | one Woge command decoder defines empty, missing and invalid values |
| Locale and request context | often backed by thread-local/request objects | backed by reactive/coroutine context and exchange | immutable Woge snapshot at adapter ingress |
| Session | `HttpSession` integration | `WebSession` integration | optional host capability; never a portable mutable session map |
| Security | Servlet filter-chain context | reactive security context | authenticated principal/capabilities translated at ingress; domain authorization remains application-owned |
| Blocking persistence | idiomatic for MVC | must run on an explicitly configured blocking dispatcher | supported, visible and measurable; never silently block a WebFlux event loop |

Spring documents that MVC streaming releases the original Servlet request thread but performs individual writes on another thread, while WebFlux uses non-blocking I/O. It also documents that Servlet disconnects are discovered through failed writes and therefore need periodic heartbeats. See the [Spring MVC asynchronous request documentation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html).

For WebFlux, reactive multi-value responses are streamed rather than collected, and event-stream values are flushed individually. See [WebFlux controller return values](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/return-types.html) and [Spring coroutine support](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html).

## Port implications

The future host SPI needs capabilities, not a generic HTTP facade:

- **Page execution:** immutable request context in, streamed HTML/patch output out.
- **Action execution:** typed command plus request context in, redirect/full-page/patch outcome out.
- **Live subscription:** request context plus resume cursor in, cancellable flow of patch events out.
- **Response metadata:** status, safe headers, cookies and cache directives represented by Woge-owned types.
- **Cancellation:** a structured execution scope cancelled by adapter disconnect, timeout or shutdown.
- **Host escape hatch:** explicit adapter-local access outside portable application/component APIs.

The request snapshot should contain only stable values needed by portable code: method and canonical route data, locale, authenticated principal/capabilities, CSRF result, request correlation ID and negotiated enhancement capabilities. Raw framework requests and security contexts remain adapter-local.

## Spring Boot module recommendation

- `woge-host-spi`: Woge-owned ports and values only;
- `woge-spring-mvc`: Servlet/MVC transport and lifecycle translation;
- `woge-spring-webflux`: WebFlux/coroutine transport and lifecycle translation;
- `woge-spring-boot-autoconfigure`: shared properties and conditional adapter selection;
- `woge-spring-boot-starter`: primary consumer dependency with no application-facing framework fork.

The starter should fail fast for ambiguous dual-stack configuration. Blocking-persistence support must require an explicit bounded executor/dispatcher with observable saturation rather than an undocumented global offload.

## Required adapter-TCK scenarios

Issue [#65](https://github.com/christian-draeger/woge/issues/65) must cover:

- shell, fragment, native action and enhanced action parity;
- empty, missing and malformed form fields;
- ordered SSE events and per-event flush on a real server;
- 404/error behavior before response commit;
- safe termination and diagnostics after response commit;
- disconnect and timeout cancellation, including heartbeat detection latency;
- locale, principal, CSRF and correlation-context propagation;
- blocking-work isolation and bounded-executor saturation;
- graceful shutdown of active streams.

## Known limitations of the M0 fixture

- Mock-server tests verify SSE encoding and completion but not real proxy buffering or per-event wall-clock visibility.
- No real Spring Security, session store or database is installed; their ownership is evaluated at the boundary only.
- The MVC fixture uses the auto-configured task executor and is not a production sizing recommendation.
- Heartbeats, resume cursors and long-lived authorization checks remain M2 work.

These limitations are implementation/TCK work, not unresolved reasons to choose one Spring stack over the other.
