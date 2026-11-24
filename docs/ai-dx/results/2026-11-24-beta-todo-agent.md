# Todo beta agent run — 2026-11-24

One project-context Codex participant ran the [Todo corpus](../todo-corpus-v0.1.md) against the fixed
Woge beta candidate. The starting application and framework artifacts were pinned before the tasks.
This run supplies technical evidence for [#272](https://github.com/christian-draeger/woge/issues/272)
and [#50](https://github.com/christian-draeger/woge/issues/50). It leaves the independent model repeat,
[human control](https://github.com/christian-draeger/woge/issues/181) and design-partner trials open.

## Environment

- Application and documentation source commit `7bf1e3e53aa8f4332828eec9231882aae1aee275`.
- Standalone Spring Boot WebFlux Todo application. The isolated project pins `0.1.0-beta.1` in
  its Gradle properties and application guidance. The pilot package supplies the corresponding
  local Maven artifacts. No GitHub Packages credentials were used.
- Kotlin 2.4.10, KSP 2.3.12, Spring Boot 4.1.1, Gradle 8.14.4 and JVM target 17. Builds ran on
  macOS 27.2.1 arm64 with JDK 21.0.6. The browser replay ran on Linux arm64 with Temurin JRE
  21.0.12.1 and Node 24.18.1.
- Playwright 1.62.1 with Chromium 151.0.7922.34, Firefox 153.0 and WebKit 26.5.
- One model session had prior project context. The run reused dependency caches. No cold-start,
  participant-time or cost comparison was recorded.

## Results

| Task | Isolated commit | JVM | Linux browser | Production lines added / removed |
| --- | --- | --- | --- | --- |
| Baseline | `8104557` | 6 passed | 24 passed, 12 expected skips | |
| TDX-01 | `f4a275a` | 6 passed | 24 passed, 12 expected skips | 16 / 5 |
| TDX-02 | `bf0d7b8` | 6 passed | 24 passed, 12 expected skips | 5 / 4 |
| TDX-03 | `b521406` | 6 passed | 24 passed, 12 expected skips | 4 / 3 |
| TDX-04 | | Blocked until two suitable releases are available | | |

All four tested states built a production JAR. The three tasks introduced zero new production files,
dependencies, Woge declarations or handwritten host bindings.

- **TDX-01.** The optional note persists through completion and filtering. Literal HTML markup
  appears as text in both submission modes. A rejected title preserves the note for correction.
- **TDX-02.** The Kotlin command property is now `text`. `@SerialName("title")` and Woge's documented
  `serializedName` field argument preserve the HTML field name and accept existing native POSTs.
  The label, validation link and generated descriptors still target the same control.
- **TDX-03.** The server and repository share a three-character minimum after trimming. The focused
  HTTP test saw 303 before the change and 400 afterwards. Native and enhanced browser tests preserve
  the submitted text and note and show the same message in the summary and field description.
- **TDX-04.** The beta artifacts used here are locally distributed and the Central snapshot is
  mutable. Repeat the upgrade task when two suitable releases can be installed.

## Deterministic gates

- On each isolated commit, ran `./gradlew --no-daemon --console=plain check bootJar` using the same
  pilot Maven repository. The server tests include the cross-origin rejection and real patch codec.
- Replayed each production JAR on Linux with its corresponding browser suite. Ran
  `npm ci --ignore-scripts` and `npm run test:browser -- --global-timeout=100000`. Each engine ran
  native and enhanced modes. Live-update cases are skipped by design in the native projects.
- The local macOS Firefox browser failed during startup on the untouched baseline. The separate
  Linux replay completed in Firefox on all four commits.
- A replay of the three task patches on a fresh baseline reconstructed the tested final Git tree.

## Corrections and follow-up

There were zero compiler failures, invented APIs or requested human interventions. The one failing
behavior assertion was the intentional pre-change HTTP check in TDX-03. The test harness needed one
correction to read the assertion from XML. Local Firefox required a Linux rerun. The accessibility
observations cover the named controls, validation links and keyboard focus in these fixtures.

The same version-pinned baseline is available for the unaided human control. Keep its results
separate from this engineering run, repeat the corpus with another model and record the upgrade
when two releases are available.
