# ADR 0043: Drive the Spring Boot dev host with a child process, a trigger file and a log marker

- Status: Accepted
- Date: 2026-10-10
- Decision owners: @christian-draeger
- Related issues: [#144](https://github.com/christian-draeger/woge/issues/144)
- Builds on: [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md),
  [ADR 0042](0042-single-actor-development-orchestrator.md)

## Context

ADR 0041 says Spring DevTools is a swappable fast-restart adapter and that a complete child restart
is the correctness fallback. The orchestrator (ADR 0042) needs one host adapter that does both, reports
when a new server generation is ready, and fails in a bounded, visible way.

## Decision

`woge-dev-spring-host` implements the orchestrator's host adapter. It talks to Spring Boot only through
things a web developer already knows: a command line, a file, a port and the log.

1. **Woge starts and owns the child process.** The command line is plain configuration.
2. **`SERVER_RESTART` with a live child** writes the trigger file. Spring DevTools then restarts inside
   the same JVM. This happens only after a successful build, because the orchestrator calls the host
   only then.
3. **`COLD_RESTART`** stops the child, checks that the fixed port is free, and starts a new child.
4. **No trigger file configured** means `Unsupported`, so the orchestrator escalates to a cold restart.
5. **Ready means a new "Started … in … seconds" log line** (the pattern is configurable). Output from
   before the trigger is dropped, so an old line cannot count. Only the match result is used; raw
   output never becomes a diagnostic.
6. **Bounded failures.** Occupied port, start error, early exit and timeout each give one stable
   diagnostic code (`SPRING-HOST-*`). After a failed start the child is stopped. Repeated failures
   add `SPRING-HOST-CRASH-LOOP`.
7. **The module has no Spring dependency.** MVC and WebFlux look the same from outside, and Spring
   types cannot leak into the model.

## Alternatives considered

- **Poll an HTTP health URL.** Needs an endpoint and cannot tell an old context from a restarted one.
- **Depend on Spring Boot classes to hook ready events.** Couples the tool to one Spring version and
  blocks other hosts from sharing the code.
- **Reverse proxy for zero-downtime restarts.** Out of scope for M1 (ADR 0041).

## Consequences

- Works with MVC, WebFlux and any app that logs a startup line. A changed log format needs a changed
  marker.
- Without DevTools, every Kotlin change is a cold restart (slower, always correct).

## Follow-up

- Gradle build adapter and the `wogeDev` task wiring.
- Real Spring Boot integration tests (implementation, generated and consecutive edits) once the
  Gradle adapter exists.
- [#145](https://github.com/christian-draeger/woge/issues/145): SSE browser channel.
