# ADR 0045: Run `wogeDev` as a Gradle-launched process and add the client through a guarded head hook

- Status: Accepted
- Date: 2026-10-09
- Decision owners: @christian-draeger
- Related issues: [#47](https://github.com/christian-draeger/woge/issues/47)
- Builds on: [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md),
  [ADR 0043](0043-spring-dev-host-uses-log-marker-and-trigger-file.md),
  [ADR 0044](0044-development-browser-snapshots-and-explicit-opt-in.md)

## Context

The orchestrator, the Spring host and the SSE channel exist as libraries. A web developer needs one
command that ties them together: `./gradlew wogeDev`. Three questions decide how it works. Where does
the long-running session live? How does every page get the browser client without the developer
editing templates? How do we make sure none of this reaches the production jar?

## Decision

1. **A Gradle plugin starts a separate launcher JVM.** The plugin `dev.woge.spring-boot` registers
   `wogeDev` as a `JavaExec` task. It runs `woge-dev-gradle`, which composes the orchestrator, the
   Spring host and the SSE channel. The task writes one `session.properties` file with the build
   command, classpath, watch roots and port, so the launcher does not need Gradle APIs.
2. **The launcher runs normal nested Gradle builds.** For each change it runs the project's own
   `gradlew ... classes resolveMainClassName`. The Gradle Tooling API is not on Maven Central, and
   a plain process is easy to cancel. `wogeDev` does not depend on compile tasks, so a broken file
   shows up as a session diagnostic instead of stopping the command. Kotlin `e: file:line:col`
   lines become `KOTLIN-COMPILE-ERROR` diagnostics with project-relative paths. Other failures
   become `GRADLE-BUILD-FAILED`, and the raw output is only available through the token-protected
   details link.
3. **Change detection uses polling.** Source roots are polled by modification time and size. This
   works the same on macOS, Linux, Windows, containers and network folders. Build-file changes are
   reported, and the developer restarts `wogeDev`.
4. **The main class comes from Spring Boot.** Every build also runs `resolveMainClassName`. The
   Spring host is created on the first successful build, so a project that does not compile yet can
   still start a session.
5. **The client reaches every page through a guarded head hook.** `woge-core` writes all
   `DevelopmentHeadContribution` services at the end of each `head`, but only when the JVM runs
   with `-Dwoge.development=true`. The interface requires the opt-in `@WogeDevelopmentHook`.
   `woge-dev-client` provides the only implementation. It reads the channel address and IDs
   from the file named by `WOGE_DEV_CLIENT_FILE` and writes them with the typed DSL. Pages need no
   code changes, and no markup is built from strings.
6. **Development code is `developmentOnly`, and `check` proves it.** The plugin adds
   `woge-dev-spring-child` and `spring-boot-devtools` to Spring Boot's `developmentOnly`
   configuration, which `bootJar` excludes. `verifyWogeProductionArtifact` runs in `check` and fails
   if the jar contains `woge-dev-*`, DevTools or the head-contribution service file.
7. **The Spring readiness listener needs `kotlin-reflect`.** Spring 7 creates Kotlin
   `spring.factories` classes through Kotlin reflection. `woge-dev-spring-child` therefore brings
   `kotlin-reflect` at runtime, but only for development.

## Alternatives considered

- **Run the session inside the Gradle daemon.** This was rejected. Daemon lifetime, configuration
  cache and cancellation would control the app process, which conflicts with ADR 0041.
- **Gradle Tooling API.** This was rejected for now because it is not published to Maven Central.
- **Continuous build (`--continuous`).** This was rejected. Gradle would own the loop, and Woge
  could not order builds, restarts and browser events.
- **Ask developers to call `developmentClient` in their layout.** This was rejected. It is easy to
  forget, and it puts dev code in production templates.
- **Inject the script with a servlet filter or response rewriting.** This was rejected. It needs
  separate MVC and WebFlux code, and string-patches HTML.
- **Native file watching (`WatchService`).** This was deferred. Its behavior differs per platform,
  and polling is fast enough for M1 project sizes.

## Consequences

- `./gradlew wogeDev` works for WebFlux and MVC without Node.js. CI runs the full edit loop
  (`scaffoldDevSmoke`): start, edit, compile error with location, recovery and shutdown.
- `woge-core` contains a small ServiceLoader lookup that is inactive unless the system property is
  set. Production apps pay one property read per `head`.
- Applications with a strict Content Security Policy currently block the dev client. Dev-only CSP
  allowances are a follow-up.
- Restarting `wogeDev` is required after dependency or plugin changes.

## Follow-up

- Dev-only CSP allowances and nonce support for the injected client.
- KSP registration and stale-output detection inside the session.
- Optional Vite/Tailwind integration and versioned production assets.
- Machine-readable task help for coding agents.
