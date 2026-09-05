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
./gradlew :woge-core:checkKotlinAbi
./gradlew :woge-protocol:checkKotlinAbi
./gradlew :woge-host-spi:checkKotlinAbi
./gradlew :woge-core:jmh
```

`ktlintFormat` changes source files; the other commands are checks. Test reports are written below each project's `build/test-results` and `build/reports/tests` directories. Detekt writes machine-readable XML/SARIF and an HTML report below `build/reports/detekt`.

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

The Spring Boot reference application has a separate browser gate. Install its pinned Node and
Playwright dependencies once, then invoke the same Gradle task as CI:

```shell
cd client/woge-fallback-client
npm ci
npx playwright install chromium firefox webkit
cd ../..
./gradlew referenceBrowserSmoke
```

The task starts the compiled WebFlux example and verifies deferred enhancement plus the normal
no-JavaScript navigation in Chromium, Firefox and WebKit. Failure traces and screenshots are written
below `client/woge-fallback-client/test-results/reference-application`; the HTML report is below
`client/woge-fallback-client/playwright-report/reference-application`. GitHub Actions uploads those
paths and all JVM test reports even when a gate fails.

Public declarations in `woge-core`, `woge-protocol` and `woge-host-spi` are tracked by Kotlin's
built-in ABI validator. `checkKotlinAbi` compares current declarations with the committed dump. Run
the corresponding module's `updateKotlinAbi` task only after reviewing and accepting an intentional
public API change.

## Source ownership

- Hand-written production Kotlin belongs in a module's `src/main/kotlin` directory.
- Hand-written tests belong in `src/test/kotlin`.
- Generated Kotlin belongs below `build/generated`; do not copy it into `src/main` or edit it manually.
- Executable documentation applications and snippets belong below [`examples`](../../examples/README.md) and participate in the root build once introduced.
- Architecture experiments remain isolated below [`spikes`](../../spikes/README.md). Production code must not depend on them.

The version catalog owns tool and dependency versions. Convention plugins in `build-logic` own shared
compilation, quality and reporting behavior. A module build file should describe only that module's
role and dependencies. The production projects and allowed edges are documented in the
[M1 module and consumer graph](../architecture/module-graph.md).

The scaffold contains no publishing credentials, automatic snapshots or template-specific release assumptions. Publication is added with its own threat and release review in the M3 release work.
Its selectively adopted template patterns and deliberate omissions are recorded in
[Repository scaffold provenance](scaffold-provenance.md).
