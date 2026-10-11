# ADR 0107: Verify production rollout at the container boundary

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#61](https://github.com/christian-draeger/woge/issues/61), [#45](https://github.com/christian-draeger/woge/issues/45), [#117](https://github.com/christian-draeger/woge/issues/117), [#118](https://github.com/christian-draeger/woge/issues/118), [#122](https://github.com/christian-draeger/woge/issues/122), [#123](https://github.com/christian-draeger/woge/issues/123), [#124](https://github.com/christian-draeger/woge/issues/124)

## Context

Socket cancellation and versioned wire fixtures do not alone prove that a packaged JVM receives
SIGTERM or that retained browser assets survive a rollout. The reference application has three hosts
and deliberately in-memory domain demonstrations; it must not imply distributed persistence.

## Decision

Add a small stateless typed deployment page/action and an opt-in operations profile to the maintained
hosts. Use Boot Actuator's private status-only health probes for MVC/WebFlux and application-owned
status routes for Ktor. Leave detailed diagnostics out of public HTTP. Bind existing request/live
policies through example-owned environment settings and existing semantic observation values.

Bound live lifetime below host graceful-shutdown time. Remove old nodes from new traffic before
SIGTERM and preserve their established streams until completion or the host deadline. Reuse the
runtime lifetime and request cancellation machinery; add no shutdown scheduler, patch broker or
cluster quota. Use PID-1 exec and existing Boot/Netty lifecycle settings.

An opt-in Gradle task packages actual reference executables into non-root containers. A stdlib Python
verifier runs nginx, retains release-prefixed HTML/client assets, switches ready releases, checks a
historical v1-compatible action response and negotiation refusal, forces live admission rejection and
verifies heartbeat delivery plus normal JVM termination after SIGTERM (0 or 143, never 137).
Spring must drain the bounded stream; a Ktor/Netty transport closure is recorded as termination,
never reported as successful stream completion.
All operations are bounded and
cleanup targets only run-owned resources. Root check remains Docker/browser-free.

This is container evidence for two release identities of the current build plus supported historical
wire fixtures, not a historical binary/browser or distributed-database certification. Existing TCK
and proxy probes remain authoritative for host-neutral negotiation, exhaustion and streaming resets.

## Alternatives considered

- **Call JVM-only TCK tests container smoke:** provides no image, proxy or PID-1 evidence.
- **Add Kubernetes, a broker and a monitoring stack:** duplicates application infrastructure and
  obscures the small HTTP contract.
- **Wait indefinitely for live clients during shutdown:** prevents bounded deployments.
- **Expose environment diagnostics publicly:** leaks secrets without improving readiness.
- **Require Node/browser/Docker in root check:** makes ordinary server development slower and brittle.

## Consequences

All three maintained hosts have a reproducible container rollout profile. Application persistence,
session replication, identity rotation and actual historical release compatibility remain explicit
application responsibilities. Optional WebSockets and experimental native DPU are not core rollout
requirements. Release asset directories are immutable and need an application-defined retention
window. The test reports Docker engine/version and fails explicitly when the engine is unavailable.

## Follow-up

Run real application N-1/N binaries, migrations and browser journeys before release. Measure TLS/CDN
and other engines separately. See the [operations guide](../guides/production-operations.md) for
shutdown order, caching, health isolation, limits and stateless/live-state scaling choices.
