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
4. **Fast restart disabled** means `Unsupported`, so the orchestrator escalates to a cold restart.
5. **Readiness update (2026-10-09):** ordinary startup logs are not sufficient. They happen before
   application runners finish and cannot identify a generation. The host now writes a fresh random
   token to the classpath trigger file before every attempt. A development-only Spring
   `ApplicationReadyEvent` listener echoes that token on stdout. Only the exact current token counts.
   Raw output never becomes a diagnostic. Fast restart can be disabled explicitly; the trigger file
   remains the handshake request file in both modes.
6. **Bounded failures.** Occupied port, start error, early exit and timeout each give one stable
   diagnostic code (`SPRING-HOST-*`). After a failed start the child is stopped. Repeated failures
   add `SPRING-HOST-CRASH-LOOP`.
7. **The child readiness listener has a development-only Spring dependency.** MVC and WebFlux look
   the same from outside. Host-specific types still cannot leak into the model or orchestrator.
8. **Unexpected child exits are observable.** The host emits `ServerExited` with the ready generation.
   The orchestrator clears the unavailable application's URLs and reports `SERVER_FAILED`; exits
   from intentionally stopped or superseded children cannot invalidate a newer generation. An exit
   racing the readiness response is held against its pending generation and triggers the fallback
   without publishing the dead child as ready.
9. **Cancellation stops the child.** Graceful shutdown is followed by a bounded forced stop, including
   child descendants. Blocking process waits run on an IO dispatcher, not the coordinator.

## Alternatives considered

- **Poll an HTTP health URL.** Needs an endpoint and cannot tell an old context from a restarted one.
- **An uncorrelated startup log.** Initially selected, then replaced: runners can still fail after
  this line. Only the small development-only child listener needs Spring types.
- **Reverse proxy for zero-downtime restarts.** Out of scope for M1 (ADR 0041).

## Consequences

- Real MVC and WebFlux integration tests compile Kotlin into a staging directory, publish only good
  classes, exercise implementation and structural generated-source edits, recover from a compile
  failure, coalesce consecutive saves and assert unchanged JVM identity on fast restarts.
- Without DevTools, every Kotlin change is a cold restart (slower, always correct).

## Follow-up

- Gradle build adapter and the `wogeDev` task wiring belong to
  [#47](https://github.com/christian-draeger/woge/issues/47). The host consumes the same successful-build
  handoff from any build adapter. KSP registration is part of that Gradle wiring.
  [ADR 0045](0045-wogedev-gradle-launcher-and-development-head-hook.md) records the composition. The
  readiness listener now lives in `woge-dev-spring-child`, which the app receives as `developmentOnly`.
- [#145](https://github.com/christian-draeger/woge/issues/145): SSE browser channel.
- [ADR 0074](0074-ktor-development-restart-parity.md) renames the module to `woge-dev-process-host`
  and adds a Ktor configuration with port readiness and full restarts. The Spring behavior above is
  unchanged; its diagnostic codes keep the `SPRING-HOST-*` prefix.
