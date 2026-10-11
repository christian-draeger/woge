# ADR 0098: Install source-owned components from an offline versioned registry

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#81](https://github.com/christian-draeger/woge/issues/81), [#88](https://github.com/christian-draeger/woge/issues/88)
- Builds on: [ADR 0016](0016-standards-native-css-authoring.md), [ADR 0018](0018-hybrid-headless-and-source-owned-components.md), [ADR 0030](0030-materialize-css-and-head-asset-boundaries.md), [ADR 0073](0073-native-first-headless-ui-primitives.md)

## Context

Applications need useful visual recipes without a styled runtime library or hidden code generation.
They must own the Kotlin and CSS, know where each file came from, and keep their edits during updates.
The first binary primitives cover disclosure and live feedback, not yet button or field behavior.

## Decision

The opt-in `dev.woge.components` Gradle plugin packages an offline registry. It is independent of
Spring, Ktor, JavaScript tools and application builds. Nothing is downloaded or evaluated at install
time. An explicitly configured local registry directory can supply a trusted, reviewed mirror.

[`registry/schema.json`](../../registry/schema.json) is the version-1 JSON Schema (Draft 2020-12).
The installer validates every index entry against the bundled schema, checks file hashes, dependency
cycles, target paths and declared style assets, before installing. Each item records its immutable
version, description, item and Woge-module dependencies, primitives, design tokens, Woge version
interval, minimum Kotlin version, hydration policy, SPDX license and repository/path/revision
provenance. Revision kinds distinguish a Git commit from a SHA-256 of the sorted source tree. The
initial catalog uses content hashes, so it does not claim an uncommitted source has a release commit.
Mirrors are a trust boundary: hashes prove integrity within the manifest, not publisher authenticity.

Each explicit `css` or `tailwind` variant describes installable sources and separate compile-tested
contract fixtures. Plain CSS is ordinary external `.css`; Tailwind uses complete static class tokens
in ordinary Kotlin. Style assets declare `global`, `scoped` or `tailwind` ownership. Scoped recipes
use authored `:where([data-woge-scope="woge-<item>"])` selectors and the cascade layer
`woge.components.<item>`. These identities depend on the item name, never file contents, package,
version or an install counter. This is explicit selector ownership, not a new CSS rewriting compiler.
No CSS transformation means IDE diagnostics and line locations remain the original editable source.
Assets load in the initial page head, not through patches. Tailwind owns its normal `utilities` layer.

The initial recipes are `disclosure-card` (CSS and Tailwind) and `status-message` (CSS). Both compose
public `woge-ui-headless` APIs, work without JavaScript and preserve escaping and native semantics.

`wogeComponents` lists the catalog. `wogeComponentAdd --name=... --variant=css --preview` shows
paths, hashes, license and required modules; without `--preview` it installs normal source and license
files plus `woge-components.lock.json`. `--package` substitutes a valid Kotlin package. The plugin
never edits the application build script or wires installation into compilation. Existing targets
are rejected even when their contents match. `--preview` is deliberately distinct from Gradle's
built-in `--dry-run`, which only shows a task graph without executing a task action.

The lock records the schema version, application package, selected variant, upstream item metadata
and each installed file's pristine content and SHA-256. Source snapshots are intentionally readable.
`wogeComponentUpdate` checks base/local/new values without writing. `--apply` replaces only locally
unchanged files, preserves edits when upstream is unchanged, recognizes already-applied updates and
handles upstream additions/removals. Any conflict defers the whole item and leaves its lock unchanged;
base/local/new snapshots go under `build/woge-components/update`. No conflict markers enter compilable
sources. After a manual merge, `--name=... --apply --accept-local` explicitly advances the pristine base
while retaining the reviewed local customization. Variant/package switches are not update operations.
A changed immutable release without a version bump is rejected.

Task inputs use Gradle providers. Explicit source-mutating tasks are neither cached nor up-to-date
skipped, and configuration-cache reuse is covered by Gradle TestKit. Symlinked targets, traversal,
Windows drive paths, corrupt lock snapshots and shared targets are rejected. The tool does not
promise a cross-file transaction if the operating system fails mid-write; keep application sources
and the lock in version control.

The [`woge-component-registry`](../../testing/woge-component-registry) test-support module materializes
**every** item/variant and its published contract tests into generated Kotlin source sets. Normal
`check` compiles them against public primitives and runs their tests. It also checks canonical
registry Kotlin formatting with ktlint. Installer tests validate the complete packaged registry and
exercise local edits, deletions, conflicts, immutable releases, dependencies and offline CLI/cache
reuse. No nested compiler daemon or browser suite is added for these simple recipes.

## Alternatives considered

- **A runtime component resolver:** rejected; installed code must compile and run after removing the plugin.
- **Remote installation and automatic dependency edits:** deferred; unnecessary trust and build-script complexity.
- **Textual automatic merges:** deferred; explicit snapshots and reviewed acknowledgement avoid a hidden merge engine.
- **Only template compilation:** insufficient; each published variant also carries an executable semantic contract.
- **A CSS scoper:** deferred by ADR 0016; authored ordinary selectors cover this small catalog.

## Consequences

### Positive

- Small, offline tooling; application-owned, IDE-editable Kotlin and CSS.
- Deterministic, licensed sources with visible metadata, dependency requirements and update baselines.
- Safe customization and the same source/test contract for humans and coding agents.
- No runtime registry, mandatory hydration, Node installation or host-specific code.

### Negative

- Applications must add the declared binary dependencies and load styles themselves.
- Conflicts need human review; source-tree hashes are not signatures.
- Multi-file writes are preflighted but not an operating-system transaction.
- Registry authors must version releases and refresh hashes after formatting source changes.

## Follow-up

- Add more recipes as the headless primitive catalog grows; keep every variant's compile and contract tests.
- Add signed publisher/release provenance before accepting unreviewed remote catalogs.
- Measure cross-platform installation on Windows in release evidence; unsafe Windows paths are tested today.
- Revisit transactional writes, package/variant migrations and browser evidence when recipes need more behavior.
