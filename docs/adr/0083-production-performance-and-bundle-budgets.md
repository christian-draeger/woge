# ADR 0083: Gate production performance and bundle growth separately from safety and development feedback

- Status: Proposed
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#46](https://github.com/christian-draeger/woge/issues/46), [#122](https://github.com/christian-draeger/woge/issues/122), [#153](https://github.com/christian-draeger/woge/issues/153)
- Builds on: [ADR 0009](0009-frontend-extension-contract.md), [ADR 0065](0065-request-owned-resource-admission.md), [ADR 0073](0073-native-first-headless-ui-primitives.md), [ADR 0081](0081-cross-engine-fallback-conformance.md)

## Context

A small browser-runtime promise needs executable regression checks, not a historical bundle-size
claim. Host rendering, browser transfer, component behavior, CSS and Spring Boot dependencies are
different costs. Timing on a shared CI runner is noisy. Resource-exhaustion limits protect requests;
they are not performance targets. Development save-to-visible latency is another contract entirely.

## Decision

Introduce the proposed MVP gates and committed measurements in
[the production budget guide](../performance/budgets.md). Normal Gradle `check` runs deterministic
per-adapter rendering allocation checks and a separate Spring Boot starter dependency-footprint report.
Raw/gzip/Brotli asset inventory checks run in the fallback-client CI job and via `./gradlew performanceBudgets`. All byte encodings must pass both their fixed
ceiling and their recorded baseline plus 10%. No new runtime asset may go unregistered.

Measure the bundled browser runtime and shipped unbundled classpath modules separately. Measure
all shipped headless component scripts, generated/minified reference CSS, an optional theme, and
locked Tailwind output against a fixed plain-CSS service-card recipe. Tailwind's global reset and
compiler setup need an explicit bounded allowance in addition to a 2x equivalent-CSS allowance.
Require self-contained runtime graphs with no package dependencies or external module imports.
This restriction applies to framework/component assets, not explicitly application-owned islands.

Use one six-region, 102-row HTML fixture over real Spring MVC, Spring WebFlux and Ktor HTTP servers.
Count allocated bytes on the thread actually executing each render, including writes to the host
sink. Warm up with 30 requests, measure 50, and compare median render allocation with that adapter's
baseline plus 50% and a shared 524,288-byte ceiling. Never raise that portable ceiling implicitly
because another adapter is slower. Report serial HTTP throughput, but do not gate it on shared CI.

Reuse the current Chromium projects for cold-navigation first shell, first applied patch, action
round-trip and patch application smoke checks. Use 4x CPU throttling, 80 ms network latency,
1.5 Mbit/s download and 0.75 Mbit/s upload. Gate medians of three samples at generous fixed ceilings;
record every sample. Apply the same policy to each real reference host. Keep the existing Firefox
and WebKit conformance projects, without pretending that CDP produces an equivalent physical-device
benchmark for them. Do not add a timing matrix or hard throughput target.

The update switch refreshes measurements only after checking fixed ceilings. A release-blocking
regression requires a fix or an explicitly reviewed budget/fixture change with fresh evidence and
caveats. CI never automatically rewrites or accepts baselines. Dependency-footprint and throughput
changes need review, but are informational rather than timing failures.

[ADR 0065](0065-request-owned-resource-admission.md) and the
[resource-budget guide](../guides/resource-budgets.md) remain authoritative for #122 safety limits.
This proposal neither changes their defaults nor recommends increasing them to meet a performance
target. Development save-to-visible feedback measurement and release gating belong separately to
[#153](https://github.com/christian-draeger/woge/issues/153); this change does not implement them.

## Alternatives considered

- **Hard CI throughput or percentile latency gates:** rejected for now; a shared runner cannot
  reliably distinguish scheduler, JIT and neighboring workload noise from product regressions.
- **One combined runtime/theme/component total:** rejected; it hides opt-in costs and discourages
  plain HTML/CSS applications by making optional dependencies appear mandatory.
- **Use the slowest adapter as the portable target:** rejected; each adapter retains its own baseline
  while every adapter must stay within the same reviewed absolute contract.
- **Treat exhaustion limits as performance ceilings:** rejected; a request can be safe yet too slow.
- **Automatically bless regenerated baselines:** rejected; it would turn regressions into new targets.

## Consequences

Gradle `check` stays Node-free; byte budgets need Node and the locked test/build tools, ordinary plain
HTML/CSS applications still do not. Tailwind is a measurement-only dev dependency of the browser test package, not a
browser import or starter dependency. The neutral starter and autoconfigure footprint is reported
independently from browser bytes and selected MVC/WebFlux/Ktor host dependencies.

The allocation counter covers the rendering window, not all coroutine/HTTP/background allocations.
Throughput includes loopback HTTP client work. Synthetic browser tests isolate full patch decoding
and application; real reference tests additionally gate host transport and mutations. These results
are regression evidence for the fixtures, not a user-device SLA or a universal application budget.

## Follow-up

- [Budget guide](../performance/budgets.md) publishes results, reproduction commands, variance,
  release consequences, fixture scope and known caveats.
- Review the proposed ceilings before accepting this ADR; keep measurements and policy changes
  visible in code review rather than quietly treating a refreshed baseline as approval.
- Extend the inventory and equivalent plain-CSS fixture when a new component family or theme ships.
- Establish isolated deployment benchmarks before adding hard throughput/tail-latency gates.
- Keep #153's development feedback gate separate from these production checks.
