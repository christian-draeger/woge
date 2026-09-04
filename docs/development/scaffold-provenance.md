# Repository scaffold provenance

The Woge scaffold was selectively derived from Christian Draeger's
[Kotlin library template](https://github.com/christian-draeger/kotlin-library-template) at commit
`33103bcaf6015f038266e41c2309e6f522ec00f8`. The template was treated as a source of proven build
patterns, not as a module blueprint.

## Adopted patterns

- a checked-in Gradle wrapper and centralized dependency versions;
- convention plugins for consistent Kotlin compiler, quality and test-report settings;
- explicit API mode for library modules;
- ktlint, Detekt and JUnit as one repeatable verification gate;
- Apache-2.0 licensing and reproducible archives.

## Deliberate Woge choices

- The scaffold starts with JVM conventions only. The MVP progressively enhances server-rendered
  HTML and does not require a Kotlin/JS or Wasm island runtime.
- Shared build behavior lives in the included `build-logic` build. Product modules are introduced
  separately with their intended dependency graph, rather than inheriting the template's example
  modules.
- JDK 21 runs the build while produced JVM bytecode targets Java 17.
- Template publishing credentials, automatic snapshots and release assumptions are omitted. They
  belong to the dedicated M3 release and supply-chain work.
- Executable documentation, generated source and architectural spikes have explicit ownership
  locations described in [Build and test Woge](build-and-test.md).

This record explains scaffold lineage only. Accepted architecture decisions remain authoritative in
the [ADR index](../adr/README.md), and the version catalog is authoritative for current tool versions.
