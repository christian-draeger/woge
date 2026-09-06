# Development lifecycle and reload model

Woge treats the development loop as a small state machine, not as text printed by Gradle or a server.
That gives a browser, command-line tool, Gradle task and future IDE integration the same answer to
questions such as “is the current build valid?” and “does this edit need a refresh or a restart?”.

The implemented model lives in the internal `woge-dev-model` module. It contains no Spring, Ktor,
Gradle, Vite, browser or MCP types. Those systems will be adapters around the model.

## What happens after an edit

1. A watcher reports `BuildStarted` with a new monotonic `BuildId` and semantic changes.
2. A newer save supersedes the in-flight build. Its changes are coalesced into the newest build.
3. `BuildSucceeded` selects the cheapest reload level that has been proven correct, or
   `BuildFailed` publishes structured diagnostics.
4. Browser-facing updates end with `ReloadApplied`; server-facing updates use
   `ServerRestarting` and `ServerReady` with a new monotonic `ServerGeneration`.
5. Events for an older build or server generation cannot mutate current state.

A compile failure does not tear down the last valid application. The failed build and its diagnostics
become visible while the previous ready server generation and URL remain available. A late success
from a cancelled or superseded build is rejected.

## Reload levels

The order is a correctness fallback chain:

1. `HOT_ASSET` updates an independently safe asset such as CSS.
2. `HOT_FRONTEND_MODULE` replaces an eligible frontend module while retaining page state.
3. `DOCUMENT_REFRESH` asks the browser for a new document.
4. `SERVER_RESTART` starts a new application generation in the existing development host.
5. `COLD_RESTART` recreates the complete development process boundary.

Tooling may move to the right whenever a cheaper action is unsupported or uncertain. It may move to
the left only with evidence that the edit is safe at that level. This makes reload an optimization;
correct behavior never depends on hot updating successfully.

The terms used by Woge are deliberately precise:

- **Live Reload** rebuilds and then refreshes the document. Browser state may be lost.
- **Hot Restart** replaces the running server application generation and synchronizes the browser;
  it does not redefine loaded JVM classes.
- **HMR / Hot Update** replaces a proven-safe asset or frontend module without a full document load.
- **True JVM Hot Reload** redefines classes in a live JVM. It is not part of the first Woge release.

## One capability boundary

`WogeDevelopmentCapabilities` exposes structured operations for status, bounded await-build,
diagnostics, reload, restart, the application manifest and local development URLs. CLI output and a
browser overlay render this model; they do not scrape each other. Gradle, Spring Boot, Ktor, optional
Vite, MCP, tests and future IDE support remain replaceable adapters.

The application manifest has only a minimal interface here. Its concrete, versioned and non-secret
schema belongs to [issue #146](https://github.com/christian-draeger/woge/issues/146).

## Security and production isolation

- Development URLs accept loopback HTTP(S) hosts only and cannot contain credentials, query values or
  fragments.
- Diagnostics contain stable codes, redacted one-line summaries and optional repository-relative
  positions. Raw terminal output, exceptions, environment values and absolute workstation paths are
  not model fields.
- A capability implementation is privileged. Any network adapter must bind locally by default and
  authenticate and authorize callers before exposing reads or invoking reload/restart commands.
- `woge-dev-model` is internal and unpublished. The module graph rejects dependencies from production
  roles, and the Spring Boot starter verifies that no `woge-dev-*` artifact reaches its runtime classpath.
- Production packaging must contain no development endpoint, watcher, token, manifest endpoint or
  tooling resource. A disabled runtime switch is not sufficient isolation.

Node/Vite is optional, not a requirement for Kotlin-and-CSS applications. A stable public MCP API is
also deferred until the underlying capabilities have real adapter experience.

The durable decision and rejected alternatives are recorded in
[ADR 0038](../adr/0038-build-independent-development-lifecycle.md).
