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
To run the same sequence locally with Docker available:

```shell
WOGE_TCK_UPLOAD_DIAGNOSTICS=true ./gradlew clean check --no-build-cache --stacktrace
docker pull nginx:1.28-alpine
python3 testing/diagnostics/multipart-ci.py
```

Multipart failures print a bounded, request-correlated in-memory timeline in the test output.
`WOGE_TCK_UPLOAD_DIAGNOSTICS=true` also prints successful timelines into JUnit XML reports. WebFlux
fixtures include Netty connection IDs, body subscription/demand/completion/cancellation, coroutine
decoder entry/exit, response completion and temporary-file lifecycle. Events contain timings, byte
counts and exception types, without request bodies, header values, filenames or temporary paths.
Client body publication proves delivery to the JDK client, not that bytes reached the network.
Passing attempts establish only that the original timeout was not reproduced; they do not resolve
[#278](https://github.com/christian-draeger/woge/issues/278).

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
[explicit GitHub Packages publication](publishing.md); Maven Central remains later work.
Its selectively adopted template patterns and deliberate omissions are recorded in
[Repository scaffold provenance](scaffold-provenance.md).
