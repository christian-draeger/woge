# ADR 0097: Register opt-in Kotlin/JS island assets from production output

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#242](https://github.com/christian-draeger/woge/issues/242), [#54](https://github.com/christian-draeger/woge/issues/54)
- Refines: [ADR 0093](0093-kotlin-js-local-islands.md), [ADR 0094](0094-lazy-local-island-lifecycle.md)

## Context

Kotlin/JS island authors currently copy production modules and hand-write their `IslandModule` URL,
version and mount export. That is error-prone and makes deploying the compiled dependency graph hard
to reproduce. Most Woge applications are JVM-only or use ordinary HTML/CSS and must not acquire a
Kotlin/JS or Node build requirement.

## Decision

Provide the opt-in `dev.woge.kotlin-js-islands` application plugin. Its `wogeIslands.kotlinJs`
configuration selects one referenced Kotlin/JS IR project and explicitly maps each generated factory
to a production output filename and named mount export.

The task waits for `jsProductionExecutableCompileSync`, copies the complete `.js`/`.mjs` production
graph under a SHA-256 content-addressed same-origin directory, and generates Java factory methods
which build `IslandModule` values for application-owned `IslandType`s. The shared graph hash is both
the immutable path segment and API asset version. The application still owns the type and registry;
no reflection, runtime manifest discovery, or handwritten URL/hash is introduced. The generated source
keeps the declared export name so the runtime's mount contract remains explicit.

The task reports raw, gzip and Brotli byte counts per JavaScript module. Gzip uses the JDK;
Brotli uses the Node.js runtime already required to build a Kotlin/JS target. No Node or JS plugin is
applied automatically. Projects not opting in retain their existing JVM-only/HTML-only build path.

## Alternatives considered

- Keep hand-written `Sync` tasks and `IslandModule` entries: simple initially, but every application
  must duplicate output copying, immutable versioning, and measurements.
- Generate a runtime-discovered JSON manifest: weakens compile-time typing and makes application
  `IslandType` ownership implicit.
- Make Kotlin/JS part of the application plugin: imposes Node and JS compilation on ordinary JVM
  applications, violating the opt-in boundary.
- Reuse a heavyweight compression dependency: unnecessary when the Kotlin/JS path already has Node.

## Consequences

Opted-in applications receive stable URLs and generated type-aware registration factories while keeping
all production module chunks together. The application must name its entry file and mount export, and
must ensure that export implements the synchronous island mount contract. Changing any graph file
changes the URL hash. Brotli reporting requires the configured system Node executable to be available.
The reference's existing DOM wrapper and rules-only Kotlin export are not yet a direct Kotlin mount
entry, so their old example-specific copy task remains until that adapter is converted to the mount
contract. Existing asset ceilings are unchanged; application-owned module graphs remain separately
measured.

## Evidence and cost

Focused plugin tests verify copied graphs, generated `IslandModule` factories, version changes, and
raw/gzip/Brotli report fields. The generated task has declared inputs/outputs and is cacheable. Root
CI continues to exercise the Kotlin/JS example already present, while other projects do not invoke this
plugin. No production module depends on `spikes/`.

## Follow-up

Migrate the reference note island when its Kotlin/JS output provides a production entry whose named
export implements the DOM mount contract; do not register its current rules-only export as a mount.
Keep the plugin narrowly scoped to Kotlin/JS IR output. Other frontend bundlers should continue to use
their own explicit adapters, and existing performance ceilings must not be raised to accommodate an
application bundle.
