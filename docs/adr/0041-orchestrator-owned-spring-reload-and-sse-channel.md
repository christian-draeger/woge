# ADR 0041: Let Woge own Spring development orchestration and use SSE for browser lifecycle events

- Status: Accepted
- Date: 2026-10-09
- Decision owners: @christian-draeger
- Related issues: [#141](https://github.com/christian-draeger/woge/issues/141)

## Context

ADR 0038 defines a build- and host-independent lifecycle, but deliberately leaves process ownership,
Spring restart mechanics, browser transport and proxy use open. The first `wogeDev` implementation
needs one topology that preserves the last valid application after compilation failure, handles
rapid or stale events deterministically and remains small enough to implement before more advanced
reload optimizations.

Spring Boot DevTools can restart an application context with a replaceable classloader while keeping
the JVM alive. It also supports a trigger file, so a restart check can happen only after a successful
compile. Its browser LiveReload feature is deprecated in Spring Boot 4.1 and does not carry Woge's
typed build and server-generation semantics.

A stable reverse proxy could keep an application port continuously available while two child
generations overlap. It would also add request forwarding, upgrade handling, cookie and redirect
semantics, shutdown behavior and another security boundary before evidence shows that they are
required.

The bounded [Spring Boot reload topology spike](../../spikes/spring-boot-reload-topology/evidence.md)
compares DevTools restarts with complete managed-child restarts and exercises a proposed browser
channel in real browser tabs.

## Decision

The Woge development orchestrator owns the session, build requests, structured lifecycle state,
browser channel and Spring Boot child process. Spring DevTools is an optional Spring-specific
**restart adapter**, not the owner of the development lifecycle.

For an ordinary Kotlin or generated-source change:

1. the build adapter compiles the change while the last valid child remains available;
2. a failed build publishes diagnostics and does not touch the DevTools trigger file;
3. after a successful build, the Spring adapter updates the classpath trigger file;
4. DevTools replaces its restart classloader inside the same Woge-owned child JVM;
5. readiness advances the `ServerGeneration`, then the browser receives a refresh event.

Build configuration, dependency and incompatible classloader changes escalate to a complete child
restart. Woge still compiles before stopping the last valid child. The replacement binds the same
explicit loopback application port. There can be a short period without a reachable server between
old-child shutdown and replacement readiness; the already-rendered browser document remains visible
and refreshes only after readiness.

The initial lifecycle channel uses browser-native Server-Sent Events on a stable, loopback-bound
orchestrator endpoint. Events carry monotonic build and server-generation identity. Browser clients
reject stale identity, reconnect using normal `EventSource` behavior and may use `Last-Event-ID` for
bounded replay. Privileged commands do not travel upstream over SSE; they use separately authorized
capability or HTTP command boundaries.

The M1 implementation does **not** add a reverse proxy. A fixed application port plus the stable
orchestrator channel is sufficient for DevTools restarts, compile-error survival and browser
synchronization. A session fails before starting if its selected ports are occupied. Proxy work may
be reconsidered only with measured need for atomic full-process cutover, stable-origin routing across
simultaneous generations or comparable production-like behavior.

DevTools and the lifecycle endpoint are development-only dependencies and resources. They must be
absent from production runtime classpaths and artifacts, not merely disabled with configuration.

## Alternatives considered

- **Let Spring DevTools own the loop:** fast for Spring, but it does not own Gradle build outcomes,
  typed diagnostics, stale generations, non-Spring hosts or Woge browser semantics. Its LiveReload
  integration is deprecated in Spring Boot 4.1.
- **Always replace the complete JVM:** simple and host-neutral, but the measured canonical scaffold
  took roughly 4 seconds for Kotlin and generated-source changes versus roughly 2.5 seconds with the
  DevTools trigger path. It remains the correctness fallback.
- **Put a stable reverse proxy in front immediately:** can make a two-generation cutover atomic, but
  the spike found no need for it on ordinary Spring edits. The extra routing and security surface is
  not justified for M1.
- **Use WebSockets:** bidirectional communication is unnecessary for one-way lifecycle events and
  adds a connection protocol when commands already have a separate capability boundary.
- **Use long polling or a raw fetch stream:** both can carry events, but require Woge to recreate
  reconnect and event-identity behavior that `EventSource` already standardizes.
- **Use Spring's browser LiveReload server:** deprecated, limited to one server and unable to express
  Woge build IDs, generations, diagnostics or reload levels.

## Consequences

### Positive

- Compilation failure leaves the valid Spring application running because restart activation follows
  successful compilation rather than raw filesystem writes.
- Spring receives a fast first-class path without leaking DevTools into the shared lifecycle model or
  blocking Ktor and future host adapters.
- One-way SSE matches the browser's needs, reconnects natively and remains ordinary HTTP.
- A complete child restart remains a clear fallback for unsafe or unsupported changes.
- M1 avoids proxy complexity while retaining explicit ports and generations.

### Negative

- DevTools' two-classloader design can expose third-party classloading incompatibilities; affected
  applications must fall back to complete child restart.
- A full child restart has a measured short server-unavailable window because no proxy overlaps two
  generations.
- The orchestrator must coordinate successful-build triggers and readiness rather than delegating the
  complete loop to Spring.
- SSE is downstream-only, so authorized reload or restart commands need another endpoint.

## Follow-up

- Implement the orchestrator and build handoff in
  [#147](https://github.com/christian-draeger/woge/issues/147).
- Implement the Spring trigger-file adapter and complete-child fallback in
  [#144](https://github.com/christian-draeger/woge/issues/144).
- Implement the dev-only SSE endpoint and generated browser client in
  [#145](https://github.com/christian-draeger/woge/issues/145).
- Revisit a stable proxy only if sustained-session evidence demonstrates that the full-restart gap or
  origin switching is materially harmful.
