# ADR 0108: Publish the existing Maven artifacts to GitHub Packages first

- Status: Accepted
- Date: 2026-10-12
- Decision owners: Woge maintainers
- Related issues: [#48](https://github.com/christian-draeger/woge/issues/48)

## Context

External applications already consume build-local Maven publications. Maintainers chose GitHub
Packages as the initial registry, before considering Maven Central. Browser assets are already
available as a JVM classpath artifact; npm is not required for this delivery.

## Decision

Reuse the existing scaffold library publications and Gradle plugin publications, including their
markers. Add GitHub Packages as a second repository, not a second publication model. Keep one
version across JVM modules, plugin resources and bundled browser metadata. Ship actual sources,
maintained Markdown documentation, Apache-2.0 metadata, ownership and SCM connections.
Retain the separately evidenced Webref notice and source-registry license files.

Require explicit snapshot/release selection and environment credentials through Gradle's named
credentials provider. Fail releases before any upload if any implementation/marker coordinate
already exists or cannot be checked. Do not claim an atomic or immutable registry transaction.

Use only manual workflow dispatch from main, selecting its exact current SHA. Require all reported
checks/statuses and the complete Build gate to be successful, run the selected version's local gate,
then check main and remote checks again. Only the upload job receives package-write permission.
No PR-triggered upload, automatic branch snapshots, tag, GitHub Release or npm upload is added.

## Alternatives considered

- **Maven Central now:** adds namespace/signing/staging work before the chosen initial delivery.
- **Independent release publications:** risks different POMs and dependencies from consumer tests.
- **Empty Javadoc JARs:** hides that the implementation is Kotlin; actual maintained guides are useful.
- **Automatic snapshots on every branch:** gives unreviewed code a publication path.
- **Claim all dependencies use Apache-2.0:** unsupported by the current limited notice inventory.

## Consequences

Consumers need GitHub authentication even for public Maven packages and configure both Gradle
plugin and dependency repositories. Build-local acceptance remains credential-free. A failed
multi-module release needs manual inspection and a fresh version, not blind overwrite/retry.
Documentation JARs contain guides, not generated Kotlin API HTML.

## Follow-up

Maintain a complete resolved dependency license inventory before public releases. Consider Maven
Central and generated API reference documentation in separate decisions.
See [publication instructions](../development/publishing.md) and the
[consumer repository recipe](../guides/packages.md).
