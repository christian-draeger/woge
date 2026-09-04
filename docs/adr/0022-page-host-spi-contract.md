# ADR 0022: Use one narrow typed page boundary with policy-checked outcomes

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#18](https://github.com/christian-draeger/woge/issues/18), [#27](https://github.com/christian-draeger/woge/issues/27), [#65](https://github.com/christian-draeger/woge/issues/65)

## Context

[ADR 0005](0005-server-host-use-case-ports.md) assigns request translation, response commit and
cancellation ownership to host adapters while keeping application execution portable. The first M1
implementation needs enough public API to exercise a page through an in-memory host and later through
Spring MVC, Spring WebFlux and Ktor. It must not freeze action, route-generation or live-update shapes
before their first vertical slices exist.

The boundary also crosses security-sensitive data. Framework authentication and CSRF processing can
supply useful facts, but neither proves domain authorization. Redirect strings can become open
redirects, arbitrary response headers can conflict with transport lifecycle, and error objects can
leak request values or stack traces.

## Decision

Implement one typed page port now:

```kotlin
fun interface PageUseCase<Input : Any> {
    suspend fun open(request: PageRequest<Input>): PageResult
}
```

`PageRequest` contains decoded typed input and an immutable `RequestContext`. The context snapshots
only Woge-owned method, identifier, language, header, cookie, authentication and CSRF values. It has
no raw server request, session, Spring Security context or generic host-object map. Its diagnostic
string redacts cookies, principal identity and typed input.

`PageResult` is sealed into three explicit outcomes:

- `Document` owns finalized response metadata and a cold ordered `Flow<HtmlFrame>`;
- `Redirect` has no body and owns a policy-validated location;
- `Failure` has no application payload and exposes only a stable category plus correlation ID.

An `HtmlFrame` stores an HTML DSL program rather than a pre-concatenated document string. Collecting a
document renders each frame through the bounded synchronous sink from [ADR 0021](0021-synchronous-bounded-html-sinks.md),
checks coroutine cancellation at every writer call and flushes at frame boundaries. Flow failures,
render failures, cancellation and downstream write failures propagate unchanged. The collector never
closes or flushes the host response itself.

HTML documents are `text/html; charset=UTF-8`. Status, content type, charset, headers and cookies are
typed and finalized before collection. Content-Length, Content-Type, Location, Set-Cookie and
hop-by-hop response fields cannot enter through the general header collection because Woge metadata
or the adapter owns them.

Application-relative redirect URLs are the ordinary path. An external redirect requires both a
validated `ExternalUrl` and an explicit application `ExternalRedirectPolicy`; there is no overload
that accepts a raw string. This implements `WOGE-REDIRECT-001`.

`AuthenticationFacts` and `CsrfVerification` describe adapter work only. Woge exposes no
`isAuthorized` fact. The page use case must authorize the concrete domain operation before returning
a document or redirect, implementing `WOGE-AUTH-001` and `WOGE-CSRF-001`. A failed CSRF check never
enters the port; the adapter rejects it first.

Controlled failures implement `WOGE-DIAG-001` with an enum category and correlation ID. Unexpected
exceptions still propagate. Before response commit an adapter maps them to the shared internal error
policy; after commit it terminates the stream and records a redacted server diagnostic.

Action execution and live subscriptions remain named follow-up capabilities from ADR 0005, not empty
placeholder interfaces. They are added when the typed action registry and Patch IR provide executable
contracts. Generated page descriptors will bind routes to `PageUseCase<Input>` without changing this
host boundary.

## Alternatives considered

- **Expose each framework request and response:** rejected because application code would fork by
  host and the Spring adapters could not share one contract.
- **Build a generic HTTP facade:** rejected because it would recreate weak server APIs and make
  commit, streaming and security ownership ambiguous.
- **Return a single HTML string:** rejected because it defeats bounded rendering and delays useful
  output until the whole page is allocated.
- **Put suspending I/O into every HTML writer call:** rejected because rendering is synchronous CPU
  work; coroutine lifetime and transport backpressure belong to the exchange collector and adapter.
- **Add speculative action and live interfaces now:** rejected because their generated descriptors,
  patch frames and resume rules are not implemented yet.
- **Accept all redirect URLs and let adapters inspect them:** rejected because adapters could drift
  and application-controlled return parameters would create an unsafe default.
- **Attach an exception or message to public failure metadata:** rejected because it makes accidental
  disclosure of input, tokens and stack traces easy.

## Consequences

### Positive

- One ordinary Kotlin use case can run unchanged behind Spring MVC, Spring WebFlux, Ktor or a test
  fake.
- The sealed result makes commit-relevant states exhaustive for adapters and coding models.
- HTML remains lazily renderable and bounded without exposing transport APIs to component code.
- Unsafe redirects, response splitting and client-visible diagnostics fail at narrow constructors.
- Authentication stays useful without being confused with resource authorization.

### Negative

- Adapters perform explicit translation and cannot pass their native context through unchanged.
- A WebFlux adapter still needs an adapter-local bridge from synchronous HTML chunks to its
  non-blocking response publisher.
- Applications must write visible domain authorization instead of relying on controller presence or
  authentication alone.
- Action, validation and live-update outcomes require additive APIs after their own contracts exist.

## Follow-up

- Bind generated route descriptors to `PageUseCase<Input>` in [#27](https://github.com/christian-draeger/woge/issues/27).
- Exercise status, headers, redirects, failures and lifecycle behavior in the shared adapter TCK
  [#65](https://github.com/christian-draeger/woge/issues/65).
- Add Spring MVC, Spring WebFlux and Ktor adapters in [#67](https://github.com/christian-draeger/woge/issues/67),
  [#66](https://github.com/christian-draeger/woge/issues/66) and
  [#68](https://github.com/christian-draeger/woge/issues/68).
- Add action and live ports only with their first complete vertical slices.
