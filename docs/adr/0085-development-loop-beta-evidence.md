# ADR 0085: Gate development-loop beta claims on repeatable evidence and incubate MCP

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#153](https://github.com/christian-draeger/woge/issues/153)
- Builds on: [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md), [ADR 0075](0075-experimental-development-mcp-endpoint.md), [ADR 0083](0083-production-performance-and-bundle-budgets.md)

## Context

The development lifecycle, reconnect protocol, failure recovery and production artifact boundaries
already have executable contracts. Beta needs reproducible save-to-feedback evidence and clear
support claims, not a second orchestrator or an implied promise of JVM class redefinition.

## Decision

Use the existing scaffold harness for an opt-in, Node-free multi-module save-to-ready-HTTP benchmark.
Record P50/P95 for CSS, static frontend assets, Kotlin implementation and structural KSP generation,
including build/generation identities. Apply fixed ceilings and a baseline tolerance of 50% plus
2 seconds. Keep it outside root `check`; review reports manually before release. Production boot JAR
verification and a real production response check follow the same development session.

Watch main sources and build files across the Gradle build. Multi-module Spring sessions use full
child restarts because project dependency JARs are outside DevTools' safely replaceable classes.
Single-module Spring retains the fast-restart adapter and its correctness fallback. Kotlin and
frontend tooling remain ordinary application dependencies; no Node requirement is introduced.

Spring MVC and WebFlux remain release-gated; Ktor participates in the existing shared smoke gate
with full JVM restarts. Beta-supported development OS claims are Linux and macOS. Windows launcher
portability is not sufficient release evidence and remains evaluation-only.

**MCP decision: incubate.** Keep the experimental opt-in local adapter, its authentication and owner-only
connection file. Existing protocol and scaffold tests support experimentation, but do not establish
cross-client interoperability, long-session compatibility or a stable API. Do not promote it into the
stable beta contract, require it in user applications or expose it outside loopback. The benchmark
uses MCP only as an internal observer and checks actual HTTP content independently.

Structured compiler summaries redact quoted values, credential assignments, bearer values and HTTP
URLs while preserving relative location and rule IDs. Raw build details and child logs are privileged
local output, not a sanitized export. Arbitrary prose, identifiers and filenames are not a guaranteed
secret-detection boundary; the guide makes this limitation explicit.

## Alternatives considered

- Shipping MCP as stable: insufficient compatibility and sustained-use evidence.
- Removing MCP: unnecessary; the authenticated opt-in implementation has useful test evidence.
- Timing every PR: shared-runner noise and added build time do not justify a new CI matrix.
- Calling HTTP feedback browser paint or true JVM Hot Reload: incorrect semantics.

## Consequences

A small manual benchmark catches material regressions without slowing the fast Node-free gate.
Three samples make P95 the maximum, not a precise statistical percentile. Existing unit/integration
and browser gates remain authoritative for recovery, reconnect and isolation. A short benchmark is
not a multi-hour soak; release reviewers must record sustained real-application evidence separately.

## Follow-up

- Use the [development guide](../guides/development-loop.md) for commands, support and troubleshooting.
- Record both Spring benchmark hosts and an extended multi-module application session before release.
- Add native Windows lifecycle/reconnect/occupied-port/crash-loop evidence before claiming support.
- Revisit MCP after independent client interoperability and long-session security/compatibility review.
