# M1 consumer AI-DX run — 2026-10-09

This is the first consumer run for [#93](https://github.com/christian-draeger/woge/issues/93). Three
AI-assisted participants from different model families each started from the same published Spring
Boot scaffold. They used only the scaffold's `AGENTS.md`/`README.md` and the public documentation
pinned at commit `bf7696d`. Each section below is one participant record in the
[result template](README.md) format.

The runs are milestone evidence, not PR CI. No human control was run on this date; see
[Limitations](#limitations).

## Shared environment

- Corpus: [`corpus-v0.1.md`](../corpus-v0.1.md) at `d9af229`; M1 subset ADX-01, ADX-04 and ADX-08
  (plain-CSS half)
- Scaffold: `scaffolds/spring-boot` materialized with WebFlux; starting commit `8d0fa15` "Clean
  scaffold" in a fresh git repository per participant
- Woge `0.1.0-SNAPSHOT` from a build-local Maven repository (stand-in for Maven Central), Kotlin
  2.4.10, Spring Boot 4.1.1, build JDK 21, JVM target 17, Gradle 8.14.4
- Documentation commit: `bf7696d` (public GitHub)
- Operating system: macOS 27
- Workflow offered: `./gradlew wogeTasks`, `./gradlew wogeDev`, `./gradlew check`
- Rules: typed DSL only, no casts/suppressions/raw HTML, no invented APIs, no framework source
  checkout; report blocked work instead of working around it

## Participant A — Claude family (Sonnet 5.5)

| Task | Compile | Tests | Unsafe or inaccessible behavior | Invented APIs | Correction iterations | Added production files / nonblank lines | Notes |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| ADX-01 | pass | pass | none; escaping asserted with `Atlas <Beta> & Co` | 0 | 3 | 5 files / 300 lines (all tasks) | `attribute("href", …)` compiles but fails at runtime; needs `url(...)` |
| ADX-04 | pass | pass (server) | JavaScript-enabled browser stays on placeholders | 0 | 2 | — | Browser runtime artifact unavailable ([#174](https://github.com/christian-draeger/woge/issues/174)); one test hang from late header commit ([#175](https://github.com/christian-draeger/woge/issues/175)) |
| ADX-08 | pass | pass | none | 0 | 0 | — | `@layer`, nesting, logical properties, `:has()`, `color-mix()`; not checked visually |

## Participant B — GPT family (GPT-6.1 Sol)

| Task | Compile | Tests | Unsafe or inaccessible behavior | Invented APIs | Correction iterations | Added production files / nonblank lines | Notes |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| ADX-01 | pass | pass | none | 0 | 1 | 4 files / 266 lines (all tasks) | Fastest: about 4 minutes to first visible result |
| ADX-04 | pass | pass (server) | avoided: default navigation serves complete HTML because no runtime could load | 0 | 5 | — | Blocked on [#174](https://github.com/christian-draeger/woge/issues/174); `npm install @woge/fallback-client` returned 404 |
| ADX-08 | pass | pass (Chromium, JavaScript off) | none | 0 | 0 | — | Container queries, layers, visible focus, narrow-screen overflow checked |

## Participant C — Gemini family (Gemini 3.8 Flash)

| Task | Compile | Tests | Unsafe or inaccessible behavior | Invented APIs | Correction iterations | Added production files / nonblank lines | Notes |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| ADX-01 | pass | pass | none | 1 | 2 | 5 files / 467 lines (all tasks) | Guessed `dev.woge.host.PageEpoch` (actual: `dev.woge.protocol.PageEpoch`) |
| ADX-04 | pass | pass (server) | JavaScript-enabled browser stays on placeholders; a "complete page" button is the only way out | 2 | 2 | — | Self-reported "pass" is overstated: no browser runtime was loaded ([#174](https://github.com/christian-draeger/woge/issues/174)); test hang from late header commit ([#175](https://github.com/christian-draeger/woge/issues/175)) |
| ADX-08 | pass | pass | none | 0 | 0 | — | `light-dark()`, `color-mix()`, `@container`, layers |

Gemini's invented names: `dev.woge.host.PageEpoch`, `kotlinx.coroutines.test.runTest` (not on the
scaffold test classpath) and a direct `DeferredRegionExecutor` use that is internal.

## Deterministic gates

The maintainer re-ran the same gates on every final workspace:

- `./gradlew check`: exit 0 for all three (tests, `verifyWogeAgentGuidance`,
  `verifyWogeProductionArtifact`).
- Unsafe-pattern scan of `src/main` (`raw(`, `unsafe`, `!!`, casts, HTML strings): no findings. The
  only `@Suppress` is the scaffold's own `SpreadOperator` in `Application.kt`.
- Adapter matrix: WebFlux only. MVC and Ktor were not part of this run.
- Accessibility and no-JavaScript: all three pages have a skip link, a navigation landmark and one
  `h1`, and they work without JavaScript. With JavaScript on, ADX-04 cannot be completed (see below).

## Repeated failure patterns

| Pattern | Seen in | Kind | Follow-up |
| --- | --- | --- | --- |
| Browser runtime cannot be installed: `woge-fallback-client-assets` missing from the published artifacts, npm package unpublished | 3/3 | framework defect | [#174](https://github.com/christian-draeger/woge/issues/174) |
| Deferred patch endpoint commits headers only when the first region completes, so gated tests hang | 2/3 | framework defect | [#175](https://github.com/christian-draeger/woge/issues/175) |
| Hand-written page epochs, region IDs and companion routes (`?view=complete`, `/woge-patches`) | 3/3 | known M2 gap | M2 typed interactions [#25](https://github.com/christian-draeger/woge/issues/25)–[#30](https://github.com/christian-draeger/woge/issues/30) |
| Low-level `DeferredRegionExecutor` guide sections read before the high-level Spring handler | 1/3 | documentation | not repeated; watch in the next run |
| URL attribute passed to `attribute(...)` fails only at runtime | 1/3 | diagnostic | not repeated; candidate for a compile-time check if it recurs |

No participant used raw HTML, string concatenation for markup or a cast. Every participant found
the canonical page APIs from the public guides without reading framework source.

## Limitations

- **No human control.** #93 asks for one; it has to be done by a person and is still open.
- Each model family had one run. Durations are self-reported and only roughly comparable.
- Gemini worked in `/tmp`, the other two in a git-ignored folder inside the repository checkout.
  All three were told not to read anything outside their workspace; that rule is not technically
  enforced.
- ADX-04 must be re-run after [#174](https://github.com/christian-draeger/woge/issues/174) and
  [#175](https://github.com/christian-draeger/woge/issues/175) to measure the browser enhancement
  path.
- This is the no-MCP baseline for [#151](https://github.com/christian-draeger/woge/issues/151).
