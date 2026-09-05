# ADR 0028: Adapt Woge through functional Spring WebFlux handlers

- Status: Accepted
- Date: 2026-09-05
- Decision owners: Woge maintainers
- Related issues: [#65](https://github.com/christian-draeger/woge/issues/65), [#66](https://github.com/christian-draeger/woge/issues/66), [#69](https://github.com/christian-draeger/woge/issues/69), [#73](https://github.com/christian-draeger/woge/issues/73), [#122](https://github.com/christian-draeger/woge/issues/122), [#124](https://github.com/christian-draeger/woge/issues/124)

## Context

Spring Boot WebFlux is Woge's first production host. Portable application code already opens a page
through `PageUseCase` and declares independent `DeferredRegion` values. The adapter must preserve
that Kotlin API, map finalized metadata before body collection, flush visible work incrementally and
cancel coroutine children when the reactive subscriber disappears.

Spring supports suspending functional handlers and maps Kotlin `Flow` to reactive publishers. Its
native response writer can also flush a publisher of publishers as independent groups. Exposing
`Mono`, `Flux`, `ServerRequest` or `ServerResponse` through the host SPI would nevertheless couple
the shared application to WebFlux and make MVC and Ktor secondary translations.

[ADR 0027](0027-fetch-deferred-patches-after-html-shell.md) also requires the portable browser path
to use one normal HTML response followed by one independently authorized Fetch patch response.

## Decision

`woge-spring-webflux` provides functional-route handlers instead of generated or application-owned
controllers:

- `WogeWebFluxPageHandler` decodes route input, snapshots request context, invokes one
  `PageUseCase`, and maps its document, redirect or controlled failure to `ServerResponse`.
- `WogeWebFluxDeferredHandler` decodes the patch request, invokes one host-neutral
  `DeferredRegionsUseCase`, executes the declarations, and returns the version-1 patch media type.
- `WebFluxPageInput` is the adapter-local escape hatch for path and query decoding until generated
  route descriptors own that work.
- `WebFluxRequestContextFactory` is the explicit integration point for Spring Security, CSRF,
  tracing and other host facts.

`DeferredRegionsUseCase` lives in `woge-host-spi`, not the Spring adapter. It receives the same typed
`PageRequest` as the page port, so one application class can implement both contracts without a
Spring import. It runs again for the Fetch request because authentication and domain authorization
must be current; the page epoch and region IDs are never capabilities.

The default request-context factory supports only GET, HEAD and OPTIONS. It generates request-local
trace IDs, copies parsed cookies and ordinary headers, and derives the preferred language range. Raw
Authorization, Cookie, proxy authorization and common CSRF token headers are not copied into the
general header bag. The default caller is anonymous. Any authenticated endpoint or unsafe method
must install a factory that has already translated Spring Security and CSRF outcomes into Woge
facts.

The page adapter commits status, content type and charset, safe response headers, cookies and redirect
location from the host SPI before body collection. HTML uses one WebFlux flush group per `HtmlFrame`.
Each frame is rendered into ordered UTF-8 chunks with the shared HTML writer before its group is
published. This bounds individual byte-array chunks but temporarily retains the chunks for one
application frame; hard frame and pending-write budgets remain follow-up work in
[#122](https://github.com/christian-draeger/woge/issues/122).

The deferred adapter uses one flush group per `EncodedPatchChunk`, including a separate terminal
group. Reactor types and `asPublisher` bridging are internal to the adapter. Subscriber cancellation
therefore cancels Flow collection, the deferred executor and all active or queued region children.
Errors before a `ServerResponse` is returned remain available to normal Spring status handling;
post-commit errors terminate the response. The complete portable failure mapping remains owned by
[#124](https://github.com/christian-draeger/woge/issues/124).

The implementation compiles against the current stable Spring Boot 4.1.1 dependency platform and
Spring Framework 7.0.9. Woge's published compatibility range is not frozen by this implementation
version and will require consumer testing before beta.

## Alternatives considered

- **Require application controllers:** rejected because every application would repeat response,
  coroutine and error-lifecycle glue and generated routes could not reuse one adapter handler.
- **Expose Reactor from the host SPI:** rejected because portable use cases would no longer run
  unchanged through MVC and Ktor.
- **Make one generic `WebHandler` own all routing:** rejected because it would duplicate Spring's
  router and hide familiar HTTP route composition from web developers.
- **Reuse one request for HTML and fallback patches:** rejected by the one-media-type and stable
  browser constraints recorded in ADR 0027.
- **Copy the full raw request into `RequestContext`:** rejected because security tokens and mutable
  framework state would cross the portable boundary invisibly.
- **Claim fully bounded frame rendering now:** rejected because the synchronous HTML writer cannot
  suspend midway through a callback without a larger sink-contract change. Explicit frame buffering
  is documented and measured before a hard resource promise.

## Consequences

### Positive

- Application page and deferred-region code contains no Spring or Reactor type.
- WebFlux applications compose Woge with the familiar `coRouter` DSL and no controller class.
- Real server tests prove page-frame and patch-frame visibility before response completion.
- Closing the client body cancels outstanding structured coroutine work.
- Response status, content type, charset, headers, cookies and redirects retain the host-SPI policy.
- ABI validation detects accidental Reactor or implementation-type leakage.

### Negative

- The host bootstrap still supplies route patterns and small input decoders until code generation.
- Authenticated applications must provide an explicit Spring Security context factory.
- One HTML frame is temporarily materialized as bounded chunks before WebFlux publishes that group.
- Pre- and post-commit failures have only the current provisional mapping until the canonical failure
  contract is implemented.

## Follow-up

- Select and configure this adapter automatically in the Spring Boot integration in
  [#69](https://github.com/christian-draeger/woge/issues/69).
- Turn the tested route into the executable Spring-first guide and no-JavaScript example in
  [#73](https://github.com/christian-draeger/woge/issues/73).
- Move the shared lifecycle cases into the cross-host adapter TCK in
  [#65](https://github.com/christian-draeger/woge/issues/65).
- Add generated typed route/input binding in [#27](https://github.com/christian-draeger/woge/issues/27).
- Define hard buffering and pending-write budgets in [#122](https://github.com/christian-draeger/woge/issues/122).
