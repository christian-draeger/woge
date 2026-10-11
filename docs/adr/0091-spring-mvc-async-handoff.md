# ADR 0091: Hand the Spring MVC response head back to a container thread

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#238](https://github.com/christian-draeger/woge/issues/238)

## Context

[ADR 0032](0032-async-servlet-spring-mvc-adapter.md) starts Servlet async processing and runs the
page on a worker. That worker also wrote status, headers and cookies. Meanwhile the container thread
was still returning through `DispatcherServlet` and the filter chain. Spring Security's
`HeaderWriterFilter` adds its headers after `chain.doFilter` returns. Servlet responses are not
thread-safe, so both threads changed the same header list at once. CI sometimes lost the
`Woge-Test-Mutation-State` header; a widened test even produced broken responses. Streaming pages
could also commit before the filter had added its headers.

## Decision

The worker still decodes input, checks security and runs the use case, with the same dispatcher,
timeout and cancellation. It no longer touches the response head. It stores the prepared head and
calls `AsyncContext.dispatch()`. The Servlet specification runs that dispatch only after the
original dispatch has returned, so every filter has finished its work.

On the async dispatch the same `HttpRequestHandler` is called again. It finds the prepared head in a
request attribute and only applies it: no decoding, security check or use case runs twice.

- Bodyless results (redirect, failure, 304, refused live stream) finish on that container thread.
- Streaming results (HTML documents, patch streams, live SSE) apply the head, start async again and
  call `flushBuffer()` to commit the headers there. The worker then writes the body, like Spring's
  `ResponseBodyEmitter`. No container thread waits for a long live stream.

The head is handed over only when the first body bytes are ready. A failure before that point still
becomes a clean, bodyless 500. HTML frames are rendered into chunks before being written, as in Ktor.

## Alternatives considered

- **Spring's `WebAsyncManager` / `DeferredResult`:** also re-dispatches, but needs Spring's
  `AsyncWebRequest`, its own timeout handling and result routing. `AsyncContext.dispatch()` gives
  the same guarantee with less code and works on any Servlet container.
- **Lock or synchronize the response:** filters do not take Woge's lock, so this cannot work.
- **Wait on the container thread for the result:** blocks a request thread and breaks long-lived SSE.

## Consequences

Security and other filters see a normal `ASYNC` dispatch. Filters that run again on that dispatch
see a response whose headers are not yet committed, the same as with Spring's own async support. The
worker still writes the body; only header writes moved. Total timeout stays the configured value.

## Follow-up

Add a Jetty smoke test for the handoff when a Jetty-based reference application exists. Consider the
same review for the WebFlux and Ktor adapters if they gain host filters that write after the handler.
