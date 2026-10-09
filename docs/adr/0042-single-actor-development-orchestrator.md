# ADR 0042: Run the development orchestrator as one single-threaded coordinator

- Status: Accepted
- Date: 2026-10-10
- Decision owners: @christian-draeger
- Related issues: [#147](https://github.com/christian-draeger/woge/issues/147)
- Builds on: [ADR 0038](0038-build-independent-development-lifecycle.md),
  [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md)

## Context

ADR 0038 describes the states and events of a dev session. ADR 0041 says Woge owns the session.
Something still has to turn file edits into those events: start builds, drop outdated results, restart
the app and fall back when a fast path fails. This must work the same for Gradle, Spring, Ktor and
tests, and it must stay correct when edits come in faster than builds finish.

## Decision

The module `woge-dev-orchestrator` contains the framework-neutral orchestrator. It knows only the
`woge-dev-model` and Kotlin coroutines. Gradle, Spring, Ktor and the browser are plugged in through
small adapter interfaces: build, host (the application child process), frontend (hot assets) and
manifest.

1. **One coordinator, no shared state.** A single coroutine owns all session state and reads one
   inbox. Adapter calls run in child jobs and report back through that inbox. File watchers may call
   `reportChange` from any thread; it only adds to a bounded, thread-safe buffer.
2. **A newer edit cancels a running build.** The result of an outdated build is never published.
3. **A running restart or hot reload is not cancelled.** New edits wait, then one newest build starts.
   This keeps the "which server generation is current" rules simple. The price: compiling cannot
   overlap with a restart.
4. **Reload level only goes up.** It is the safest of: what the build reported, what the changed files
   need (CSS → hot asset, Kotlin → server restart, build files → cold restart) and what the host can
   do now (no running server → at least a server restart; last restart failed → cold restart).
5. **Failed builds are not forgotten.** Their changed files are carried into the next build, so a CSS
   fix after a broken Kotlin edit still restarts the server.
6. **Fallback ladder.** A hot level the frontend refuses falls back step by step to a document
   refresh, then to a server restart. A server restart the host cannot do or fails escalates to a
   cold restart (new child process). A failing cold restart ends in the new phase `SERVER_FAILED`
   via the new event `ServerRestartFailed`. The next successful build then restarts cold.
7. **Every restart attempt uses a new generation number.** Gaps are fine; reuse is not.
8. **Commands are checked against the build.** `reload` and `restart` are accepted only for the
   latest successful build. Otherwise they are refused as stale. A command during active work is
   queued and dropped if a newer edit arrives.
9. **Errors are redacted.** Adapter exceptions become diagnostics with stable codes. The raw
   exception goes only to an internal listener.
10. **Clean shutdown.** `stop()` and cancelling the scope cancel work and shut the host down.
11. **A quiet period is a throttle, not a debounce.** It starts at the first change, so constant
    edits cannot delay builds forever.

## Consequences

- Tests run on virtual time and need no Gradle, Spring or processes.
- Spring DevTools stays a replaceable host adapter (issue #144). The SSE channel (issue #145)
  subscribes to `state` and `events`.
- The model grows by one phase (`SERVER_FAILED`) and one event (`ServerRestartFailed`). It records
  whether the previous application is still running.
- The API is still `@ExperimentalWogeDevelopmentApi`, internal and unpublished.

## Alternatives considered

- **Locks and shared mutable state.** Rejected: ordering bugs are hard to test and to reason about.
- **Cancel a running restart on every edit.** Rejected: it can leave the server in an unknown state
  and conflicts with the model's generation rules.
- **Debounce instead of throttle.** Rejected: constant edits could starve builds.

## Follow-up

- [#144](https://github.com/christian-draeger/woge/issues/144): Spring DevTools host adapter.
- [#145](https://github.com/christian-draeger/woge/issues/145): SSE browser channel.
- [#146](https://github.com/christian-draeger/woge/issues/146): concrete manifest schema.
