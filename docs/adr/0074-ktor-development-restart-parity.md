# ADR 0074: Run Ktor in `wogeDev` as a managed child with full restarts

- Status: Accepted
- Date: 2026-10-12
- Decision owners: @christian-draeger
- Related issues: [#154](https://github.com/christian-draeger/woge/issues/154)
- Builds on: [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md),
  [ADR 0042](0042-single-actor-development-orchestrator.md),
  [ADR 0043](0043-spring-dev-host-uses-log-marker-and-trigger-file.md),
  [ADR 0045](0045-wogedev-gradle-launcher-and-development-head-hook.md)

## Context

`./gradlew wogeDev` worked only for Spring Boot. Ktor is an equal adapter, so Ktor developers need the
same loop: save, build, restart, refresh the browser, and keep the last working app online when the
build fails.

Ktor has its own auto-reload. It swaps classes lazily on the next request, needs `development` mode, a
module function reference and watch paths. Woge could not tell when the new code is live, so it could
not publish a correct server generation (ADR 0041, ADR 0042).

## Decision

1. **One process host for both frameworks.** `woge-dev-spring-host` becomes `woge-dev-process-host`.
   It starts the app as a child process, checks the port and stops the child. Spring Boot and Ktor
   differ only in a small configuration (`ChildProcessHostConfig.springBoot` or `.ktor`).
2. **Ktor always gets a full restart.** After every successful build Woge stops the Ktor process and
   starts a new one. The host answers a fast `SERVER_RESTART` with `Unsupported`, so the orchestrator
   escalates to a cold restart. Ktor auto-reload is not used.
3. **Ready means "the port accepts connections".** Woge first checks that the port is free, so an old
   process cannot answer. Ktor opens the port only after its modules are installed, so the first
   accepted connection belongs to the new process. Diagnostic codes use the `KTOR-HOST-*` prefix.
4. **The app reads its port from `PORT`.** Woge sets this environment variable. No Woge code runs
   inside the Ktor process except the development client markup.
5. **Gradle wiring.** `dev.woge.application` adds `wogeDev` when the project also applies Gradle's
   `application` plugin and not Spring Boot. The main class comes from `application.mainClass`. The
   inner build runs only `classes`. `woge-dev-client` is added to the child classpath through a
   separate `wogeDevRuntime` configuration and never reaches `runtimeClasspath` or the distribution.
   The Woge KSP processor is registered by `dev.woge.application`, so both hosts get typed routes.

## Alternatives considered

- **Ktor auto-reload.** Faster for small edits, but readiness cannot be tied to a build, and it needs
  extra setup in the application. Rejected.
- **A readiness marker like Spring.** Would need Woge code inside every Ktor application. The open
  port is a good enough signal because Ktor binds after loading modules. Rejected for now.
- **A reverse proxy.** Out of scope for M1 (ADR 0041).

## Consequences

- Kotlin and generated-source edits always start a new JVM. This is slower than Spring DevTools but
  always correct.
- A compile error keeps the last Ktor process running. A failed start after a restart does not keep
  the old process, because it was already stopped.
- Spring rewrites a strict `Content-Security-Policy` so the development client may connect. Ktor has
  no such hook without development code in the app. With a strict CSP, allow the `wogeDev` origin in
  `connect-src` and `script-src` while developing.
- Tests: unit tests for port readiness, a real Ktor integration test (edits, compile recovery, port
  conflict, shutdown) and an end-to-end `wogeDev` smoke test in a fresh Ktor project.

## Follow-up

- Revisit a faster Ktor restart only if real projects show the full restart is too slow.
- If Ktor gains a hook for response headers in development, add the automatic CSP rewrite that
  Spring already has.
