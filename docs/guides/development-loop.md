# Develop with one command

From your [Spring Boot application](../../scaffolds/spring-boot/README.md), run:

```sh
./gradlew wogeDev
```

Open the URL printed after `Ready`. Save Kotlin, CSS or static assets and leave the command running.
Use `./gradlew wogeDev --port=9000` if another application uses port 8080. Stop with Ctrl+C; Woge
closes its browser channel and stops its application child. Do not kill unrelated Java processes.

Plain HTML/CSS applications need a JDK and the Gradle wrapper, **not Node**. [Vite](vite.md) and
[Tailwind](tailwind.md) are optional adapters, not prerequisites. Vite owns its own module updates;
Woge owns compilation, session identity and application lifecycle. There is no development proxy.

## Hosts and operating systems

Spring Boot MVC and WebFlux are the primary beta hosts and release-gated by `scaffoldDevSmoke` in
Build CI. [Ktor](ktor-adapter.md) uses the same command and is in that smoke gate too; every Kotlin
change starts a new Ktor JVM. Ktor does not have a fast-restart adapter.

Linux has CI evidence; macOS has local benchmark evidence. These are the beta-supported development
systems. The launcher uses portable polling and selects `gradlew.bat` on Windows, but Windows is
**evaluation-only** until an equivalent shutdown/reconnect/port/crash-loop run is recorded. The Bash
benchmark harness requires Bash, Python 3, curl and a JDK; it is not a native Windows release gate.

For a multi-module build, run `./gradlew :app:wogeDev` from its root (use `wogeDev` when the root is
the app). Woge watches main source/resource folders and build files across the build, ignores generated
output, and builds the selected app plus its Gradle dependencies. Multi-module Spring applications
use full child restarts: dependency JARs are not safely replaceable by Spring DevTools. Restart the
command after changing dependencies, plugins, included modules or source-set configuration; these
can change the classpath and watch roots captured when the command starts.

## What updates mean

| Term | What happens here |
| --- | --- |
| Live Reload | The browser refreshes the document after a successful change. Woge sends the notification over SSE; ordinary CSS can instead swap its stylesheet in place. |
| Hot Restart | Woge compiles first, then restarts the application. Single-module Spring can use DevTools; a full child restart is the correctness fallback. |
| HMR / Hot Update | Optional Vite replaces supported frontend modules without refreshing the document (HMR). Woge can deliver CSS as an in-place hot update. Neither redefines Kotlin/JVM classes. |
| True JVM Hot Reload | Class redefinition in a running JVM. **Woge does not provide this.** |

Spring DevTools Hot Restart recreates the application context/classloader; it is not JVM class
redefinition and does not promise to retain in-memory application state. Woge restarts only after
successful compilation, then refreshes the browser. [ADR 0041](../adr/0041-orchestrator-owned-spring-reload-and-sse-channel.md)
records this boundary.

The browser channel uses SSE and monotonic build/generation IDs. Reconnecting tabs receive the latest
snapshot, not a replay of stale restarts. [State handoff](../../docs/adr/0061-development-full-refresh-state-handoff.md)
is explicit and bounded, not a promise to preserve every field across every refresh.

For buffering, recovery, version skew and KSP setup, use the [request troubleshooting guide](troubleshooting.md).

## Troubleshooting

- **Compile error:** the last valid application keeps serving. Fix the located source error and save.
  Generated-code errors get one full-regeneration retry; rejected declarations still need a source fix.
- **Application will not start:** read the terminal startup diagnostic. The session and its diagnostic
  channel live outside the child, so they remain available while the app is down.
- **Occupied application or Vite port:** stop the known owner or choose another port. Woge does not
  evict processes. Vite also requires its optional Node installation and dependencies.
- **Repeated crashes:** the host reports a crash-loop diagnostic and does not endlessly restart.
  Repair the startup problem, then save or restart the command. Raw application output stays local.
- **Stale page after reconnect:** check the terminal's current `Ready` URL and browser network/SSE
  connection. A compiler failure deliberately leaves the old page running; this is not HMR failure.
- **Dependency edit ignored:** restart `wogeDev` to capture the new classpath, as above.
- **Shutdown interrupted:** identify the process belonging to this application's
  `build/woge-dev/session.properties` before stopping that PID. Never use blanket Java process kills.

Structured compiler diagnostics keep relative source locations and stable rule IDs; quoted values,
credential assignments, bearer values and HTTP URLs are redacted before terminal/SSE/MCP summaries.
This is a conservative boundary, not detection of every possible secret in arbitrary compiler prose.
Do not put secrets in source identifiers or filenames. Raw compiler details and child logs can contain
secrets: only inspect them locally, and remove sensitive text before sharing. Production has neither
the diagnostic server nor the client/overlay or relaxed development CSP.

The [experimental MCP adapter](development-mcp.md) remains **incubating**, opt-in with `--mcp`, and
outside the stable beta API. [ADR 0085](../adr/0085-development-loop-beta-evidence.md) records the review.

## Repeatable feedback and production-isolation check

Run from the Woge framework checkout, not your application. The lightweight report contracts can
be checked with `PYTHONDONTWRITEBYTECODE=1 python3 scripts/test-development-loop-benchmark.py`.

```sh
./gradlew publishScaffoldArtifacts
WOGE_DEV_BENCHMARK_REPORT="$PWD/build/reports/development-loop/local.json" \
  bash scripts/test-spring-boot-scaffold-dev.sh "$PWD/build/scaffold-maven-repository" webflux
```

Replace `webflux` with `mvc` for the other Spring host. This optional path needs no Node. It runs one
multi-module session, one warm-up plus three measured saves for each feedback path: stylesheet,
plain JavaScript static asset, Kotlin implementation in a dependency module, and generated KSP region
add/rename. The JSON includes each build/generation ID and elapsed time from writing the source until
a READY build and its changed HTTP content are observable. CSS must keep its generation. This is
save-to-ready-HTTP feedback, **not browser paint latency or Vite HMR timing**; browser SSE/overlay
contracts are separately tested in the existing browser gate.

After shutdown it builds and verifies the production boot JAR, starts it without development flags,
and checks absent dev markup/endpoints, unchanged strict CSP and closed development listeners.

[Policy](../../testing/performance/development-loop-policy.json) gates P50/P95 by both fixed ceilings
and the [WebFlux baseline](../../testing/performance/development-loop-baseline-webflux.json) or
[MVC baseline](../../testing/performance/development-loop-baseline-mvc.json) plus 50% and
2 seconds of scheduler allowance. P50 is the nearest-rank median; P95 of three samples is the maximum.
Small samples detect large regressions; they are not a statistically precise tail-latency claim.
Compare on an idle, warmed machine with the same JDK/Gradle and adapter. First compilation, downloads
and initial server startup are excluded. Do not run another build concurrently with the measurement.

To propose a refreshed baseline, add `WOGE_DEV_BENCHMARK_OPTIONS=--update-baseline` to the command.
Fixed ceilings still apply. Review the fixture, environment and raw samples before accepting a change;
never automatically bless a regression. This manual gate adds no time or Node requirement to `check`
and no extra CI job. The report also verifies configuration-cache reuse on the final incremental build. For a longer repeatable multi-module session, set `WOGE_DEV_BENCHMARK_OPTIONS="--samples 30"`
(120 measured saves); values up to 1000 allow a multi-hour soak without restarting the command.
The same budgets apply, and the full JSON preserves every sample. Before beta release, record both
Spring hosts and a sustained application session;
a short fixture run alone is not evidence of hours of production-sized development.
