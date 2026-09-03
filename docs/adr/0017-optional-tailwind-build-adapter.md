# ADR 0017: Integrate Tailwind through an optional build adapter

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#77](https://github.com/christian-draeger/woge/issues/77), [#88](https://github.com/christian-draeger/woge/issues/88), [#76](https://github.com/christian-draeger/woge/issues/76), [#13](https://github.com/christian-draeger/woge/issues/13)

## Context

Woge applications need to support current industry styling workflows without making one of them the framework's component model. Tailwind must find Kotlin templates, generated descriptors and source-distributed components reliably; dynamic class construction must not become a production-only missing-style bug.

Tailwind v4 is CSS-first and offers both an npm CLI and official platform executables. Its scanner treats inputs as text, its compiler can transform CSS and its CLI initially emits checkout-specific inline source maps. A clean ports-and-adapters boundary must keep all of those build concerns out of Woge core and runtime artifacts.

The [Tailwind/Kotlin spike](../../spikes/tailwind-kotlin/evidence.md) tested explicit source roots, static variants and arbitrary values, theme variables, generated and distributed sources, dynamic-candidate diagnostics, Gradle production/watch tasks, plain-CSS byte preservation, source-map normalization, reproducible output and npm/standalone parity.

## Decision

Tailwind is a first-class optional build adapter. `woge-core`, HTML/component APIs, host ports, Spring/Ktor adapters and the browser patch runtime have no Tailwind dependency or Tailwind-specific public types. Utility classes remain ordinary HTML class strings.

The supported v4 configuration disables automatic source discovery and declares every root with CSS-first `@source`: application Kotlin, generated Woge descriptors, source-distributed component sources or manifests, and reviewed inline candidates. Descriptor generation runs before extraction. Issue [#76](https://github.com/christian-draeger/woge/issues/76) must define how a distributed package exposes its candidate roots or manifest without requiring runtime reflection.

Runtime state maps to complete static class tokens, for example with a Kotlin `when` or map. Dynamically assembled utility names are unsupported because Tailwind cannot observe their possible values. The build adapter may reject common interpolation/concatenation patterns with source-located diagnostics. Exceptional non-literal candidates use explicit `@source inline(...)`. Woge does not introduce a utility-class DSL or a Tailwind-specific CSS-property API.

Application-owned standards-native CSS and Tailwind input/output are separate assets. Plain CSS is copied unchanged; Tailwind processes only its own CSS-first input. Pages link both assets in deterministic order. This preserves the contract from [ADR 0016](0016-standards-native-css-authoring.md) and prevents Tailwind's optimizer from silently rewriting application CSS.

The Gradle adapter owns task dependencies, declared source/lockfile inputs, generated CSS/map outputs, stale-asset-free resource synchronization and diagnostics. Development watch is a build task. Browser reload/HMR remains a capability of the consuming application development server and is not added to the Woge production runtime.

Tailwind's locked npm CLI is the reference executor. An official standalone executable is a supported optional executor when a project otherwise needs no Node.js. Every standalone platform asset is pinned to an exact release and SHA-256; no normal build downloads `latest`. Both executors must pass the same output contract.

Source maps are external production artifacts with checkout paths normalized to stable project/package-relative paths. Production output is minified and content-hashed by the eventual asset pipeline; the Tailwind task itself is reproducible at a stable output path.

Woge records one exact supported Tailwind/CLI version per release. Patch updates pass extraction, golden-output, watch, map and executor-parity tests. Minor updates additionally review detection and output changes. Major updates require an explicit compatibility decision and migration guidance. Toolchain overrides outside the tested version are not covered by Woge's compatibility promise.

## Alternatives considered

- **Make Tailwind the default Woge styling language:** rejected because it would weaken plain CSS, add a third-party build/runtime assumption and couple component semantics to utility names.
- **Do not officially support Tailwind:** rejected because Kotlin/generated-source extraction and production diagnostics need a tested path for a common web workflow.
- **Wrap every utility in a typed Kotlin DSL:** rejected because it duplicates Tailwind, harms documentation transfer and still cannot represent plugins/new arbitrary syntax promptly.
- **Use automatic content detection only:** rejected because build directories, ignored package sources and monorepo working directories make discovery implicit and fragile.
- **Run application CSS through the Tailwind input:** rejected because Tailwind/Lightning CSS may rewrite syntax owned by the standards-native CSS contract.
- **Require Node.js unconditionally:** rejected because the official standalone CLI produced identical output and is useful for otherwise JVM-only projects.
- **Prefer standalone for every project:** rejected because its per-platform binary is substantially larger and npm provides familiar lockfile, audit and update workflows for web developers.
- **Add Woge browser HMR:** rejected because watched asset generation is sufficient at this boundary and application dev servers already own reload behavior.

## Consequences

### Positive

- Tailwind users keep its normal CSS configuration and class vocabulary.
- Plain modern CSS remains independent and byte-preserved.
- Kotlin, generated and distributed candidate sources have an explicit reproducible contract.
- Static candidate guidance and diagnostics serve both humans and coding models.
- JVM-only projects can choose a verified standalone executable without changing Woge application code.
- Build/source-map behavior is deterministic and absent from production runtime modules.

### Negative

- Tailwind adds a separate build toolchain and release compatibility matrix.
- A conservative dynamic-class check cannot prove every possible Kotlin expression.
- Source-distributed components need candidate metadata/source packaging work in #76.
- External maps and multiple CSS assets need later content hashing and deployment integration.
- Standalone support needs large per-platform downloads and checksum maintenance.
- Full browser HMR depends on the consuming development environment.

## Follow-up

- Define source-distributed candidate/style manifests with component packaging in [#76](https://github.com/christian-draeger/woge/issues/76).
- Materialize the optional Gradle adapter and content-hashed asset pipeline after the M1 scaffold in [#13](https://github.com/christian-draeger/woge/issues/13).
- Add Windows and Linux standalone parity to release infrastructure before promising those distributions.
- Keep dependency auditing and exact-version update tests in normal maintenance.
