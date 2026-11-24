# ADR 0117: Publish checked main snapshots to Maven Central

- Status: Proposed
- Date: 2026-11-24
- Decision owners: Woge maintainers
- Related issues: [#285](https://github.com/christian-draeger/woge/issues/285)

## Context

Consumers need a public Maven repository to try Woge changes as soon as they pass the main build.
[ADR 0108](0108-github-packages-first-publication.md) established GitHub Packages as the initial
registry. Woge now has a verified Maven Central namespace with snapshots enabled.

## Decision

For each push to `main`, publish the validated artifacts for the push SHA after its Build jobs
succeed. Reuse the existing library and plugin publications, including all markers. Require the
`dev.woge` group and a snapshot version. Use the GitHub environment `maven-central-snapshots` with
a deployment rule for `main`. Supply the Central Portal token to the upload step through
Gradle's named credentials.

Queue the main Build runs to serialize uploads of the shared snapshot version. Keep the
GitHub Packages dispatch workflow and signed Central release process separate.

## Alternatives considered

- Manual snapshot dispatch adds work between every reviewed change and consumer testing.
- Publishing before CI validates the commit makes broken artifacts available to consumers.
- Queueing only the upload jobs allows different Build durations to reorder snapshot uploads.

## Consequences

Consumers can resolve the same changing version anonymously from the Central Portal snapshot
repository. Main builds wait for the preceding build and upload. Failed publication remains
visible in GitHub Actions. Snapshots use the registry's retention policy and remain mutable.

## Follow-up

Use the documented [publication](../development/publishing.md#automatic-maven-central-snapshots)
and [consumer](../guides/packages.md#consume-maven-central-snapshots) workflows to verify the
first live upload.
