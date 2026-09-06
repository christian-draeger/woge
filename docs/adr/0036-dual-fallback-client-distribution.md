# ADR 0036: Distribute one fallback client through npm and a JVM asset adapter

- Status: Accepted
- Date: 2026-09-06
- Decision owners: @christian-draeger
- Related issues: [#132](https://github.com/christian-draeger/woge/issues/132)

## Context

The fallback browser client was initially copied from repository source into the reference
application. That proved behavior but was not a versioned consumer contract. Applications with Vite,
esbuild or another frontend pipeline expect a normal ES module package. Spring Boot and Ktor
applications that otherwise need no JavaScript build should not be forced to install Node merely to
serve a small standards-native module.

Both installation paths must speak the exact protocol implemented by `woge-protocol`. A published
artifact assembled from different source, or a JVM and npm release with silent version skew, would
turn deployment order into browser failures.

## Decision

`client/woge-fallback-client/src` is the single source of the browser runtime. It has no framework,
hydration or CSS runtime dependency and exports `WOGE_PATCH_PROTOCOL_VERSION` with its public API.

Woge distributes that source in two forms:

- `@woge/fallback-client` is a public npm package for frontend asset pipelines. Its default export
  path is one minified ES2022 module with TypeScript declarations, an external source map and a
  machine-readable manifest containing package/protocol versions, byte counts, SHA-256 hashes and
  SRI integrity. `npm pack` includes only documented files.
- `dev.woge:woge-fallback-client-assets` is a framework-neutral JVM artifact. It places the canonical
  unbundled ES modules below `static/assets/woge`, where Spring Boot and compatible Ktor static
  resource setups can serve them from the classpath. Package metadata is retained below `META-INF`.
  Building or consuming this artifact does not execute Node.

The npm build, JVM asset check and shared tests compare the npm metadata, JavaScript constant,
`PatchProtocolVersion.CURRENT` and `PatchStreamV1.VERSION`. Different values fail the build. The
browser rejects an incompatible stream before changing the DOM.

Package verification builds twice, requires byte-identical tarballs, installs one tarball in a fresh
external project, imports and bundles only the public package entry, and executes that bundle in the
browser contract. The Spring Boot reference application consumes the JVM artifact through its public
runtime dependency instead of copying source in its build.

Publishing remains an explicit release action. `prepublishOnly` runs the complete browser package
gate, but ordinary repository checks never publish or require credentials.

## Alternatives considered

- **Require npm for every application:** familiar to frontend teams, but adds a second toolchain to a
  Spring/Ktor application whose only browser code is Woge's small adapter.
- **Publish only a JVM webjar:** convenient for server-centric applications, but awkward for normal
  bundlers, tree-shaking metadata and TypeScript tooling.
- **Maintain separate bundled and classpath sources:** easy to package initially, but permits behavior
  and protocol drift between consumers.
- **Load the runtime from a public CDN by default:** quick for a demo, but delegates availability,
  privacy, CSP and release selection to an external origin.
- **Hide protocol negotiation inside a generated bootstrap:** removes one visible constant, but makes
  deployment compatibility harder to inspect and test.

## Consequences

### Positive

- Frontend and JVM-centric teams each use a native dependency workflow.
- Spring Boot remains a first-class Node-free path without making Spring part of the browser runtime.
- Types, manifests, hashes and a fresh-consumer smoke make artifacts legible to IDEs, CI and coding
  agents.
- One source and one explicit protocol constant make version skew fail early.

### Negative

- Releases produce and verify two artifacts from one source tree.
- The JVM path serves multiple unbundled requests unless an application fingerprints or bundles them.
- Applications, not Woge core, still own public asset URLs and production cache headers.
- External source maps can expose readable source and need a deliberate production deployment policy.

## Follow-up

- Add release signing, provenance and registry publication in the public-release milestone.
- Exercise rolling-version compatibility and deployment ordering in
  [#118](https://github.com/christian-draeger/woge/issues/118).
- Add production cache/proxy evidence in [#61](https://github.com/christian-draeger/woge/issues/61).
