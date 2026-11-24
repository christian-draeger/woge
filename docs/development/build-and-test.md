# Build and test Woge

Woge uses the checked-in Gradle wrapper. A fresh clone needs a JDK 21 installation and network access for the first dependency download; produced JVM libraries target Java 17.

Run the complete local gate from the repository root:

```shell
./gradlew check
```

The gate compiles the convention plugins, verifies explicit API mode, runs tests with XML and HTML reports, checks Kotlin formatting with ktlint, analyzes Kotlin with Detekt, validates ADRs and module boundaries, and checks local documentation/snippet links.

## Focused commands

```shell
./gradlew test
./gradlew detekt
./gradlew ktlintCheck
./gradlew ktlintFormat
./gradlew validateDocumentation
./gradlew validateAdrs
./gradlew validateModuleBoundaries
./gradlew testModuleBoundaries
./gradlew testSpringBootScaffold
./gradlew scaffoldBrowserSmoke
./gradlew :woge-core:checkKotlinAbi
./gradlew :woge-protocol:checkKotlinAbi
./gradlew :woge-host-spi:checkKotlinAbi
./gradlew :woge-m1-api-corpus:check
./gradlew :woge-core:jmh
```

`ktlintFormat` changes source files; the other commands are checks. Test reports are written below each project's `build/test-results` and `build/reports/tests` directories. Detekt writes machine-readable XML/SARIF and an HTML report below `build/reports/detekt`.

The M1 API corpus compiles complete public examples and then invokes the pinned Kotlin compiler on
deliberately invalid HTML, URL, unsafe-value, protocol and portable-host fixtures. Its second test
keeps the machine-readable framework index aligned with module, npm, Kotlin, Spring, Ktor and patch
protocol metadata.

`testSpringBootScaffold` materializes clean WebFlux and MVC applications outside the repository build,
resolves Woge through a build-local Maven repository and runs both real-server tests. It is part of
`check`. `scaffoldBrowserSmoke` additionally validates the WebFlux starter in Chromium with JavaScript
disabled; its setup and evidence paths are documented in
[Maintain the Spring Boot application scaffold](spring-boot-application-scaffold.md).

`scaffoldDevSmoke` checks edits, compiler-error recovery and generated regions in both Spring hosts.
It also proves incremental typed action addition, ID changes, duplicate-ID rejection and removal of
obsolete generated descriptors and registries.
CI runs this smoke test in `example-browser-smoke`, alongside the scaffold browser test, rather
than extending the clean `check` job. Both remain required parts of the pre-merge gate.

The JMH command is an explicit performance measurement rather than a pass/fail check. `check` compiles
the benchmark fixture but does not execute it. HTML sink results are written below
`modules/woge-core/build/results/jmh` and interpreted in the
[recorded baseline](../performance/html-sinks-baseline.md).

## Reproduce the clean CI gate

Pull requests and pushes to `main` remove previous outputs and disable Gradle's task-output cache:

```shell
./gradlew clean check --no-build-cache
```

This keeps dependency and configuration reuse available while proving that generated files and
compiled fixtures can be rebuilt from the checkout. The gate includes positive example compilation,
the deterministic negative compiler fixtures, JVM tests, formatting, static analysis, ABI checks,
module-boundary checks, ADR metadata and documentation/snippet-link validation. It never calls an AI
model.

Generated Kotlin is compiled and tested, but excluded from ktlint and Detekt: formatting checks
apply to hand-written sources, not compiler output.

The `Multipart diagnostics` workflow investigates intermittent upload timeouts on native Linux amd64
with Temurin 21. After the clean root gate it runs three parallel and three serial three-host TCK
attempts behind nginx. Each attempt explicitly reruns the test tasks without task-output caching,
has a 180-second ceiling, and retains separate logs and reports. The investigation has a six-minute
total budget and stops on the first failure; it never retries a failed assertion into a pass.
The serial control uses `--max-workers=1`: [Configuration Cache](https://docs.gradle.org/8.14.4/userguide/configuration_cache.html)
can still run tasks in parallel with `--no-parallel`. The runner checks actual test-suite time
windows and fails if parallel suites do not overlap or serial suites do overlap; `summary.json`
retains those windows.
To run the same sequence locally with Docker available:

```shell
export WOGE_TCK_UPLOAD_TRANSPORT_DIAGNOSTICS=true
WOGE_TCK_UPLOAD_DIAGNOSTICS=true ./gradlew clean check --no-build-cache --stacktrace
docker pull nginx:1.28-alpine
python3 testing/diagnostics/multipart-ci.py
```

Multipart failures print a bounded, request-correlated in-memory timeline in the test output.
`WOGE_TCK_UPLOAD_DIAGNOSTICS=true` also prints successful timelines into JUnit XML reports. WebFlux
fixtures include Netty connection IDs, body subscription/demand/completion/cancellation, coroutine
decoder entry/exit, response completion and temporary-file lifecycle. Events contain timings, byte
counts and exception types, without request bodies, header values, filenames or temporary paths.
The dedicated workflow enables `WOGE_TCK_UPLOAD_TRANSPORT_DIAGNOSTICS` for WebFlux socket reads and
HTTP framing. Before the next request header is decoded, incoming socket bytes are attributed to
the preceding fixture ID on the same connection. Compare those arrivals with request framing and
handler entry to locate a delay. These events add only counts, connection IDs and loopback ports.
Client body publication proves delivery to the JDK client, not that bytes reached the network.
Passing attempts establish only that the original timeout was not reproduced; they do not resolve
[#278](https://github.com/christian-draeger/woge/issues/278).

For a focused check of the gap between client publication and Netty reads, enable
`loopback_kernel_control` when manually dispatching the **Multipart diagnostics** workflow.
The ordinary PR and manual diagnostics paths retain their existing behavior. The opt-in job
runs the same WebFlux shared TCK on native Linux amd64, with a dedicated namespace containing
only loopback. It excludes browser, Docker proxy and concurrent host fixtures.

Run the control from a fresh Linux checkout as an unprivileged user with passwordless sudo,
JDK 21, Node 24, Python 3, `iproute2` and `tcpdump`. Prepare the test runtime outside the
namespace first, so Gradle can resolve the pinned dependencies and build the test classes.

```shell
./gradlew --no-daemon --no-build-cache --console=plain \
  :woge-spring-webflux:test --tests '*WebFluxSecurityFormTest'
python3 testing/diagnostics/multipart-kernel-control.py
```

The script creates a unique namespace, brings up only `lo`, and starts
the capture before launching the test. Gradle runs as the invoking user with the
prepared JDK and dependency cache, offline and without reusing an outside daemon.
There are at most three attempts, each with a 75-second test deadline and a
10,000-packet ceiling. On exit the script interrupts the capture to flush counters,
stops remaining fixture processes and deletes its namespace.

The capture retains only timestamp, loopback ports, TCP flags, sequence and acknowledgment
numbers, and TCP payload length. Payload contents and raw packet captures are excluded.
`build/multipart-loopback` retains each attempt's test log, fresh JUnit XML when
available, filtered TCP text, capture counters and the existing
[correlation report](../../testing/diagnostics/multipart-loopback.py). An incomplete,
dropped or capped capture cannot support a correlation. The run stops on the
first failed test or evidence check.

Compare client-to-server byte counts and server ACKs with the `netty.socket` and
`netty.frame` events. An early packet and ACK followed by a late Netty read places
the gap after kernel delivery. A late first packet leaves the gap before that boundary.
If case 6 never decodes a request header, the report can only attribute packets to
the case 5 socket. Use the timeline for large gaps and account for wall-clock
adjustment when comparing close events. A passing single-host control leaves
the original parallel-host failure and the separate Ktor symptom open.

Each build CI job has a 15-minute limit, including setup and smoke tests. A timeout is a failure,
not a reason to rerun indefinitely or increase the limit. Locally, use `./gradlew check --console=plain`
to see progress. If a task stops progressing well beyond its usual duration, inspect its process and
logs, stop that build, and fix the cause before retrying.

On macOS, system sleep pauses local builds and inflates their elapsed time. For an unattended run,
`caffeinate -i ./gradlew check --console=plain` prevents idle sleep only while that command runs.
Keep the lid open; this does not override lid-close sleep or change permanent power settings.

The multi-host reference application has a separate browser gate. Install its pinned Node and
Playwright dependencies once, then invoke the same Gradle task as CI:

```shell
cd client/woge-fallback-client
npm ci
npx playwright install chromium firefox webkit
cd ../..
./gradlew referenceBrowserSmoke
```

The task starts WebFlux, MVC and Ktor in sequence and runs the same deferred-enhancement and normal
no-JavaScript journeys in Chromium, Firefox and WebKit against each host. Failure traces, screenshots
and HTML reports are grouped by host below `client/woge-fallback-client/test-results/reference-application`
and `client/woge-fallback-client/playwright-report/reference-application`. GitHub Actions uploads
those paths and all JVM test reports even when a gate fails.

The fallback client also proves the artifact a consumer will receive, rather than importing its
repository source directly:

```shell
cd client/woge-fallback-client
npm run test:package
```

That task creates two byte-identical tarballs, installs one into a fresh external project, imports
its public entry point, bundles the consumer, and writes `build/package-test-result.json`. The normal
browser gate then loads that generated consumer bundle in a real browser. `npm publish` runs the full
client gate through `prepublishOnly`; publishing credentials and release authorization remain
outside ordinary builds.

Public declarations in `woge-core`, `woge-protocol` and `woge-host-spi` are tracked by Kotlin's
built-in ABI validator. `checkKotlinAbi` compares current declarations with the committed dump. Run
the corresponding module's `updateKotlinAbi` task only after reviewing and accepting an intentional
public API change.

## Source ownership

- Hand-written production Kotlin belongs in a module's `src/main/kotlin` directory.
- Hand-written tests belong in `src/test/kotlin`.
- Generated Kotlin belongs below `build/generated`; do not copy it into `src/main` or edit it manually.
- Executable documentation applications and snippets belong below [`examples`](../../examples/README.md) and participate in the root build once introduced.
- New-repository starter source belongs below [`scaffolds`](../../scaffolds/spring-boot/README.md) and must pass its external consumer gates.
- Architecture experiments remain isolated below [`spikes`](../../spikes/README.md). Production code must not depend on them.

The version catalog owns tool and dependency versions. Convention plugins in `build-logic` own shared
compilation, quality and reporting behavior. A module build file should describe only that module's
role and dependencies. The production projects and allowed edges are documented in the
[M1 module and consumer graph](../architecture/module-graph.md).

The scaffold contains no saved publishing credentials, automatic snapshots or template-specific release assumptions.
The root build now reuses its local Maven publications for
[the GitHub Packages and Maven Central publishing workflows](publishing.md).
Its selectively adopted template patterns and deliberate omissions are recorded in
[Repository scaffold provenance](scaffold-provenance.md).
