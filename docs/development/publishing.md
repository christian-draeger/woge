# Publish Maven packages

The root build reuses `scaffoldPublicationModules` and `scaffoldPluginModules`. The same publications
feed the build-local consumer tests, GitHub Packages and Maven Central snapshots.
`wogeGroup=dev.woge` and `wogeVersion` in `gradle.properties` are the defaults.
`-PwogeVersion=...` selects one version for libraries, development tools, plugin implementations,
markers and bundled browser metadata. Test-support projects and examples are not published.

## Verify without publishing remotely

```shell
./gradlew verifyPublicationContract testSpringBootScaffold
./gradlew ktlintFormat
./gradlew check
```

These commands need no GitHub credentials. Artifacts land in `build/scaffold-maven-repository`.
The contract checks sources, actual documentation, Apache-2.0/owner/SCM metadata, Gradle module
variants, plugin markers, coherent project dependencies and the versioned classpath assets.
It rejects development-tool dependencies in production libraries. The external Spring Boot
acceptance test compiles WebFlux/MVC consumers and checks their production Boot JARs too.

## Automatic Maven Central snapshots

The Build workflow publishes the `wogeVersion` from `gradle.properties` after the checks for each
push to `main` pass. Main builds queue so their snapshot uploads run one at a time. The publication
job checks out the push SHA and verifies the complete local publication contract before uploading
the same JVM modules and plugin markers to the [Central snapshot repository](https://central.sonatype.org/publish/publish-portal-snapshots/).
See [ADR 0117](../adr/0117-main-snapshots-to-maven-central.md) for the publishing boundary.

The GitHub Actions environment `maven-central-snapshots` is restricted to `main`. Set
`MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` there to the two parts of a
[Central Portal user token](https://central.sonatype.org/publish/generate-portal-token/).

For a manual snapshot upload, provide those token parts locally through
`ORG_GRADLE_PROJECT_mavenCentralSnapshotsUsername` and
`ORG_GRADLE_PROJECT_mavenCentralSnapshotsPassword`.

```shell
./gradlew publishMavenCentralSnapshot -PwogePublishCentralSnapshot=true --no-configuration-cache
```

The snapshot target requires `dev.woge` and a version ending in `-SNAPSHOT`. The build uses
the dedicated snapshot endpoint. [Configure a snapshot consumer](../guides/packages.md#consume-maven-central-snapshots).

## Explicit snapshots and releases

For a local upload, use the environment credentials described in the
[consumer guide](../guides/packages.md), but give the token `write:packages` and repository access.
The Gradle named credentials provider handles authentication. The preflight reports missing
environment names without printing tokens. Never use `--debug`, put tokens in Gradle properties
files or capture credentials in diagnostic logs.

```shell
# These commands upload remotely. Run them only after explicit release approval.
./gradlew publishGitHubPackagesSnapshot \
  -PwogeVersion=0.1.0-SNAPSHOT -PwogePublicationChannel=snapshot --no-configuration-cache

./gradlew publishGitHubPackagesRelease \
  -PwogeVersion=0.1.0 -PwogePublicationChannel=release --no-configuration-cache
```

The channel is required, including for direct subproject GitHub publishing tasks. Snapshots must
end in `-SNAPSHOT`; releases must not. Release versions may include a prerelease suffix.
All publication tasks run the shared local contract and preflight before any upload.
The release preflight authenticates and checks **every** implementation artifact (main, source,
documentation, module metadata and POM) and plugin-marker POM. Even an incomplete upload with only
a JAR present blocks reuse; an existing coordinate or an authentication/network failure stops
the whole publication.
Snapshots are explicitly mutable and may be replaced.

GitHub Packages is not an atomic multi-module transaction and this check does not magically make
the registry immutable. Do not race local uploads with Actions. A partially uploaded release is
not retried or deleted by automation: inspect the package state and choose a new release version.
Never overwrite or delete/recreate a release to pretend it was immutable.

## Dispatch-only Actions

Use **Publish GitHub Packages → Run workflow**, from `main`. Supply the exact current main HEAD,
a version and the snapshot/release channel. The gate requires all four Build jobs on that commit,
all other reported checks and legacy status contexts to be successful. Pending, missing, skipped
or failed checks block publication. It runs `check` with the selected version, then rechecks main
and the remote checks before granting the publication job access.

The workflow has no push or pull-request trigger. Only the publication job has `packages:write`;
the gate has read-only contents/check/status access. Publication is serialized, and checkout does
not persist Git credentials. No tag, GitHub Release, npm publish or Maven Central upload is created.
Main can still advance after the last gate: the upload remains pinned to the already verified SHA,
never to newly fetched unverified code.

## Prepare a Maven Central bundle

Verify `dev.woge` in the [Central Portal](https://central.sonatype.org/register/namespace/) and
set up a local [GPG signing key](https://central.sonatype.org/publish/requirements/gpg/).
Set `WOGE_CENTRAL_SIGNING_KEY` to that key's public fingerprint. Use your normal GPG
agent for passphrase entry.

```shell
./gradlew prepareMavenCentralBundle -PwogeVersion=0.1.0-beta.1
```

The task verifies the complete local Maven repository and writes a signed bundle to
`build/maven-central/woge-0.1.0-beta.1.zip`. It includes implementation artifacts, Gradle metadata,
sources, documentation and plugin markers with the required checksums. Use the
[Central Portal upload workflow](https://central.sonatype.org/publish/publish-portal-upload/)
for the separately approved validation and release.

## Licenses and notices

Woge-owned code is Apache-2.0; its POM license is **not** a license claim about dependencies.
There is no complete dependency-license report in the current build. Maven dependencies are not
shaded into Woge JARs and keep their own upstream POMs, licenses and notices. Before redistribution,
review your resolved runtime dependencies with `./gradlew dependencies` and inspect their published
license/notice files; record exact versions rather than assuming all dependencies use Apache-2.0.

The generated HTML tag dataset has separately pinned MIT provenance and the actual upstream notice
in [config/html-elements](../../config/html-elements/README.md). `woge-core` ships that notice and
provenance in its main/source JARs. The source-owned component registry retains the checked-in
per-recipe LICENSE.txt files inside the Gradle plugin artifact. Browser classpath modules are
checked-in Woge source, not an npm dependency bundle. Build/test-only tools are not included in
production JARs by this publication setup.

## Follow-up

- Review the complete resolved dependency license inventory before a public release; do not treat
  this limited notice inventory as legal clearance.
- Consider generated Kotlin API documentation separately; current documentation JARs contain
  real maintained guides rather than empty Java Javadoc output.
