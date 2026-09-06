# ADR 0038: Share one build-independent development lifecycle across tool adapters

- Status: Accepted
- Date: 2026-09-06
- Decision owners: @christian-draeger
- Related issues: [#140](https://github.com/christian-draeger/woge/issues/140)

## Context

Woge needs a short edit-to-browser loop for Spring Boot first and Ktor as a peer. Gradle, application
hosts, optional frontend tooling, browser connections and future MCP or IDE clients each observe a
part of that loop. If one of those technologies owns the state model, other integrations must parse
logs, emulate its lifecycle or leak host-specific types through the architecture.

Fast feedback must not trade away correctness. Rapid saves can finish out of order, cancelled builds
can report late success, and overlapping restarts can announce a server that is already obsolete.
Compilation failure should leave the last valid application usable and expose structured diagnostics.

The reload vocabulary is also commonly ambiguous. “Hot reload” may mean browser refresh, server
restart, frontend module replacement or JVM class redefinition, although those operations have very
different correctness and state-preservation properties.

## Decision

Woge owns an internal, experimental `woge-dev-model` module with no host, build-tool or transport
dependencies. It defines:

- **Live Reload** as rebuild followed by a complete document refresh.
- **Hot Restart** as replacement of the server application generation plus browser synchronization,
  without JVM class redefinition.
- **HMR / Hot Update** as replacement of a proven-safe asset or frontend module without a document
  refresh.
- **True JVM Hot Reload** as class redefinition in a live JVM. This is outside the first release.

`BuildId` and `ServerGeneration` are positive monotonic values scoped to one development session.
The pure reducer accepts typed events for build start, success, failure and cancellation; server
restart and readiness; CSS and frontend changes; and required/applied reloads. It returns `APPLIED`,
`IGNORED_STALE` or `REJECTED` without mutating its input state.

A newer `BuildStarted` supersedes an active older build and coalesces its known changes. Terminal
events for lower build IDs are stale. A terminal event for the current but inactive build is rejected,
which prevents late cancellation success. Starting a newer build invalidates pending reload/restart
work from the older build but retains the active valid server. A failed current build retains that
server generation and its local URLs.

Restart requests coalesce toward the newest requested generation. Readiness below that generation is
stale; readiness above it is unrequested and rejected. Only exact readiness advances the session.

Reload levels are ordered by increasingly broad correctness:

`HOT_ASSET → HOT_FRONTEND_MODULE → DOCUMENT_REFRESH → SERVER_RESTART → COLD_RESTART`

The selected action may escalate to the right. Moving left requires positive evidence that the
specific change is eligible. Server completion is represented by `ServerReady`, never by pretending
that a browser reload completed a restart.

`WogeDevelopmentCapabilities` is the shared structured object-capability for status, bounded
await-build, diagnostics, reload, restart, application-manifest and development-URL access. Gradle,
CLI, Spring, Ktor, optional Vite, browser overlay, MCP, tests and future IDE tooling adapt this same
boundary; none parse terminal output as a protocol. The concrete manifest schema remains owned by
issue #146 behind a minimal extension interface.

Development URLs are loopback-only and exclude credentials, queries and fragments. Diagnostics carry
stable codes, already-redacted one-line summaries and optional repository-relative positions; raw
compiler output, exceptions, source bodies, environment values and absolute paths are excluded.
Transport adapters bind locally by default and authenticate and authorize access before exposing the
privileged capability, especially reload and restart commands.

The development model has a dedicated `tooling-model` architecture role. Production roles cannot
depend on it, it is not published, and production starters verify that no `woge-dev-*` artifact is on
their runtime classpath. Production artifacts contain no dormant development endpoint or resource.

True JVM class redefinition, mandatory Node/Vite and a stable public MCP API are non-goals for the
first release.

## Alternatives considered

- **Make Gradle continuous build the lifecycle model:** useful as one trigger, but Gradle task output
  and daemon behavior are not a stable capability protocol and cannot represent browser/server state.
- **Use Spring Boot DevTools types as the core:** gives Spring a convenient restart mechanism but
  prevents Ktor parity and leaks one host into every consumer.
- **Start with a Vite-owned HMR graph:** mature for frontend modules, but would make Node mandatory and
  cannot decide Kotlin server restart correctness.
- **Parse terminal text in each client:** quick for a demo, but loses stable diagnostics, ordering,
  authorization boundaries and reliable machine consumption.
- **Implement JVM class redefinition first:** can preserve more state for eligible bytecode changes,
  but has class-shape and tooling constraints. Server/document fallback is simpler and dependable.
- **Run exploratory spikes for the event model:** the ordering rules are deterministic and directly
  testable. Spikes are reserved for later process ownership, proxy and restart behavior that depends
  on external systems.

## Consequences

### Positive

- Every tool observes one deterministic state and stale-event policy.
- Spring Boot remains first-class without becoming the portable abstraction.
- Failed builds preserve a usable last-known-good application with structured diagnostics.
- Hot update is an optional optimization over an explicit correctness fallback.
- Tooling is machine-readable and suitable for humans, IDEs and coding agents without special APIs.
- Architecture and starter checks make production isolation executable.

### Negative

- Adapters must translate their native events and enforce transport authorization.
- Monotonic identifiers and exact generation matching add orchestration bookkeeping.
- The first release may refresh or restart more often than specialized hot-reload systems.
- The internal API remains experimental until several adapters validate its shape.

## Follow-up

- Validate process ownership, ports and proxy necessity in
  [#141](https://github.com/christian-draeger/woge/issues/141).
- Build the shared orchestrator over this reducer and capability boundary in
  [#147](https://github.com/christian-draeger/woge/issues/147).
- Add Spring Boot restart and browser-channel adapters in
  [#144](https://github.com/christian-draeger/woge/issues/144) and
  [#145](https://github.com/christian-draeger/woge/issues/145).
- Define the concrete non-secret manifest in
  [#146](https://github.com/christian-draeger/woge/issues/146).
