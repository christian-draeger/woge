# ADR 0005: Model server adapters as Woge use-case exchanges

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#9](https://github.com/christian-draeger/woge/issues/9), [#18](https://github.com/christian-draeger/woge/issues/18), [#63](https://github.com/christian-draeger/woge/issues/63), [#64](https://github.com/christian-draeger/woge/issues/64), [#65](https://github.com/christian-draeger/woge/issues/65)

## Context

Woge application code must run unchanged through Spring MVC, Spring WebFlux and Ktor. Those hosts disagree about request objects, form binding, response streaming, sessions, security context, threading and disconnect notification. The [Spring support spike](../architecture/spring-support-model.md) demonstrated that even a small shared application needs materially different host glue.

The boundary must preserve standard HTTP behavior without exposing a framework API or inventing a universal server framework. It must also allow streaming and cancellation without making Reactor, Servlet or Ktor types public.

## Decision

The host SPI exposes three narrow, capability-oriented Woge use cases:

1. **Page execution** opens a page call and returns response metadata plus a cold flow of document frames.
2. **Action execution** decodes a typed command and returns a redirect, complete document or cold patch flow.
3. **Live-update subscription** authorizes a subscription before commit and returns response metadata plus a cold flow of live patch events.

The following signatures communicate the shape. Exact names may change during M1 implementation, but changing ownership or lifecycle rules requires a new ADR.

```kotlin
public interface PageUseCase {
    public suspend fun open(call: PageCall<*>): PageExchange
}

public interface ActionUseCase {
    public suspend fun execute(call: ActionCall<*>): ActionExchange
}

public interface LiveUpdateUseCase {
    public suspend fun subscribe(call: LiveCall<*>): LiveExchange
}

public data class PageExchange(
    val metadata: ResponseMetadata,
    val frames: Flow<PageFrame>,
)

public sealed interface ActionExchange {
    val metadata: ResponseMetadata

    public data class Redirect(
        override val metadata: ResponseMetadata,
        val location: WogeUrl,
    ) : ActionExchange

    public data class Document(
        override val metadata: ResponseMetadata,
        val frames: Flow<PageFrame>,
    ) : ActionExchange

    public data class Patches(
        override val metadata: ResponseMetadata,
        val frames: Flow<PatchFrame>,
    ) : ActionExchange
}

public data class LiveExchange(
    val metadata: ResponseMetadata,
    val frames: Flow<LiveFrame>,
)
```

`PageCall`, `ActionCall` and `LiveCall` carry a generated descriptor, decoded typed inputs and an immutable `RequestContext`. Routing and body/form decoding happen in the adapter before portable application code runs. The context contains Woge-owned values for request/correlation ID, locale, normalized read-only headers and cookies, authenticated principal/capabilities, CSRF verification, resume cursor and negotiated enhancement capabilities as applicable. It never exposes raw request, response, session or security-context objects.

`ResponseMetadata` is immutable and must be finalized before the first frame is collected. It uses Woge-owned status, header, cookie, cache and content-type values. Adapters reject hop-by-hop headers and unsafe newline/value construction. Redirect locations use `WogeUrl` rather than an unvalidated string.

Flows are cold, single-exchange streams. Collection starts output. Normal completion closes the body. A failure before metadata is returned can still become a complete error response; a failure after collection begins terminates the stream and emits safe diagnostics because the status may already be committed.

Cancellation uses Kotlin structured concurrency. No Woge `CancellationToken` is added. The adapter collects each exchange in a child job tied to request disconnect, host timeout, application shutdown and parent cancellation. Portable rendering and data-loading work must remain children of that scope. Cancellation is not mapped to an application error page after commit.

Host-specific escape hatches are adapter extension points, not a generic object bag in `RequestContext`. Code that needs `HttpServletRequest`, `ServerWebExchange`, Spring Security or a Ktor call explicitly depends on the corresponding adapter module and registers an adapter-local interceptor/customizer. Architecture checks prevent that module from being used by portable core, protocol and application fixtures.

## Ownership matrix

| Concern | Portable Woge/application | Host adapter |
| --- | --- | --- |
| Page/action/live descriptor and typed input | owns | resolves and supplies |
| Domain authorization | owns | supplies authenticated facts |
| Route matching and form/body decoding | defines generated contract | integrates framework and normalizes errors |
| HTML and transport-neutral patch frames | produces | encodes and flushes |
| Status, safe headers, cookies and redirect intent | requests through Woge values | validates and commits to host response |
| Coroutine child work | owns structured children | creates/cancels root request job |
| Disconnect, timeout and shutdown | observes cancellation | detects and initiates cancellation |
| Session/framework security object | does not receive | owns; exposes only explicit adapter extension points |
| Logs and diagnostics | provides safe diagnostic facts | attaches host/request context and redacts output |

## Failure phases

1. **Route or decode failure:** adapter returns the shared Woge 404/400/422 contract without invoking the use case.
2. **Authorization or execution failure before exchange:** use case returns a typed outcome or throws a classified failure; adapter can still select status and error document.
3. **Frame/render failure before first write:** adapter renders the shared error document when the host has not committed.
4. **Failure after commit:** adapter terminates the stream, cancels child work and records a safe correlation diagnostic. It does not append an unrelated error page to a partial stream.
5. **Cancellation:** child jobs are cancelled without treating the client disconnect as a server fault. Cleanup runs in bounded non-cancellable sections only where required.

## Architecture enforcement

The initial build must combine several checks rather than trust package naming:

- Gradle project dependencies allow core/protocol/host-SPI modules to point only inward and prevent adapter dependencies.
- A source or bytecode architecture test rejects `org.springframework`, `reactor`, `jakarta.servlet`, `io.ktor` and adapter-module types from portable modules.
- Public API validation detects accidental framework types in signatures and generated descriptors.
- The adapter TCK compiles one shared application fixture and runs it unchanged through MVC, WebFlux and Ktor.
- Adapter escape-hatch fixtures live in adapter tests and cannot be imported by the portable fixture.

These checks are implemented with the module graph in [#9](https://github.com/christian-draeger/woge/issues/9) and the walking skeleton/TCK in [#18](https://github.com/christian-draeger/woge/issues/18) and [#65](https://github.com/christian-draeger/woge/issues/65).

## Alternatives considered

- **One universal HTTP request/response facade:** rejected because it recreates a weak server framework, makes lifecycle ownership unclear and encourages portable code to inspect host transport details.
- **Framework request objects in common interfaces:** rejected because every non-native adapter becomes a translation layer over another framework and public components stop being portable.
- **One application API per host:** rejected because route, validation, patch and error semantics would drift and examples could not be shared.
- **Callbacks instead of `Flow`:** rejected as the default because callback lifecycle, backpressure and cancellation are harder to compose with Kotlin structured concurrency.
- **A custom cancellation token:** rejected because it duplicates coroutine cancellation and makes forgotten propagation likely.
- **A generic host-object map in request context:** rejected because framework leakage would become runtime-only and invisible to dependency checks.

## Consequences

### Positive

- Ports describe Woge operations instead of mirroring Servlet, Reactor or Ktor.
- Response commit and failure ownership are explicit.
- Coroutine cancellation can propagate across rendering, deferred regions and live updates.
- Framework-specific integration remains possible but visibly non-portable.
- One TCK can define semantic parity while allowing different execution strategies.

### Negative

- Adapters must translate typed routing, decoding and metadata rather than forwarding host objects.
- Streaming APIs require disciplined cold-flow and structured-concurrency rules.
- Some host conveniences need explicit adapter extensions and separate documentation.
- Blocking persistence in WebFlux requires visible execution-policy configuration.

## Follow-up

- Encode these boundaries in the initial module graph and architecture tests in [#9](https://github.com/christian-draeger/woge/issues/9).
- Exercise the provisional signatures in the host SPI implementation [#18](https://github.com/christian-draeger/woge/issues/18).
- Enforce lifecycle and parity through the adapter TCK [#65](https://github.com/christian-draeger/woge/issues/65).
- Revisit names, not ownership, after the M1 walking skeleton supplies compile-time evidence.
