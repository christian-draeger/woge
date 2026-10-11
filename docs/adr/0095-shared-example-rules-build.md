# ADR 0095: Keep shared note rules and their JS build example-owned

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#55](https://github.com/christian-draeger/woge/issues/55), [#242](https://github.com/christian-draeger/woge/issues/242)
- Refines: [ADR 0093](0093-kotlin-js-local-islands.md), [ADR 0094](0094-lazy-local-island-lifecycle.md)

## Context

Sharing a counter does not justify Kotlin/JS. A note draft can share deterministic field validation,
safe error codes and guarded transitions while preserving server authority. The root CI gate already
approaches its timeout; adding browser toolchain downloads or a second UI framework is inappropriate.

## Decision

Use a small example-only KMP module with JVM and Kotlin/JS IR ES modules, common rules/tests, and one
JS export adapter. Run the exact common tests on JVM and Node in root `check`. Node executes the pure
JS logic, not a substitute DOM implementation; the reference's Chromium journeys prove actual browser
integration. Use system Node/npm (Node 24 in CI), disable both Kotlin Node download scopes, and use
npm rather than downloading Yarn. Keep its resolution lockfile and Gradle's existing caches.

Copy the production compiler's complete ES module output into generated reference JVM resources via
an example-local task. Retain exported names and production DCE/shortened member naming; do not ship
source maps. The existing Spring and Ktor resource wiring serves the same jar. No framework module or
adapter gains a Node dependency, DOM code, KMP target or asset API. Plain framework JVM tasks stay
Node-free; building the reference example or running the full repository gate now requires Node/npm.

The server constructs a fresh draft and revalidates before persistence. Shared states/error codes are
not authorization facts. CSRF, authentication, resource authorization and persistence remain server
concerns. A versioned string-field codec restores only untrusted draft text, not a submitted phase.

## Alternatives considered

- **Copy rules into JavaScript:** simpler for a counter, but cannot prove a single tested rule source.
- **Karma/webpack browser tests in the shared module:** unnecessary overhead for pure logic; the existing
  reference Playwright journeys already cover real DOM and host behavior.
- **General Kotlin/JS Gradle asset registration:** deferred to #242; this task should not introduce a
  framework-wide Node requirement or an unreviewed asset contract.
- **Share server security/persistence models:** rejected; compiling code for clients publishes it and
  never makes a client's validation result trustworthy.

## Consequences

The sample demonstrates immediate client feedback and authoritative revalidation with matching error
codes and serialization semantics. Its compiler output includes the Kotlin runtime, so it is still
more expensive than plain JavaScript. Deployment owns the wrapper and all imported modules together;
unversioned child paths must not receive immutable cache headers. Root CI needs system Node, but
framework-only consumers do not. Node tests are fast and deterministic, and browser tests stay in the
existing reference gate rather than duplicating a browser harness.

## Follow-up

- #242: general opt-in asset registration, immutable hashed chunks, module size reporting and manifests.
- #56: explicit optimistic action reconciliation; local `READY` is not a persisted result today.
- Measure CI cold-cache time separately from the focused local timings documented in the issue report.
