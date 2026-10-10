# ADR 0068: Package one content-addressed production asset tree

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#170](https://github.com/christian-draeger/woge/issues/170), [#117](https://github.com/christian-draeger/woge/issues/117)
- Builds on: [ADR 0016](0016-standards-native-css-authoring.md), [ADR 0067](0067-safe-http-cache-defaults-and-conditional-pages.md)

## Context

Production CSS, scripts and images need stable content-hashed URLs and immutable caching. Normal
HTML/CSS applications should not install Node just to package files. Independently renaming CSS and
its referenced images would break ordinary relative `url(...)` and `@import` references unless
Woge introduced a CSS parser or an additional bundler.

## Decision

`dev.woge.application` registers the cacheable `wogeAssets` task; the Spring Boot plugin applies it
as part of the ordinary classes/JAR lifecycle. The default input is `src/main/resources/static`.
The output is a dedicated generated resource directory, not a modification of source files.

The task computes one SHA-256 identity for the complete tree. Sorted logical URL paths, explicit
path lengths and each file's SHA-256 content digest form an unambiguous input. File order, timestamps
and checkout location cannot change it. URL path segments are percent-encoded. Symlinks, external
files, overlapping input/output directories and the reserved Woge URL namespace fail explicitly.

Every file keeps its relative path under `/_woge/assets/<tree-hash>/`. Ordinary relative CSS imports
and image/font references therefore keep working without source rewriting. A change, addition,
removal or rename changes the tree hash. This deliberately invalidates all asset URLs together:
simpler and safer than inventing a dependency graph or silently breaking CSS.

Generated resources live under `META-INF/woge/assets/<tree-hash>/`. A deterministic
`META-INF/woge/assets.properties` maps logical paths to hashed URLs, with schema version and tree hash.
The generated resource directory is a source-set output, avoiding stale copy-task files from earlier
hashes. Original static files remain available at ordinary host URLs with the host's non-immutable
cache policy.

`AssetUrls` loads exactly one application manifest. `assets.url(applicationUrl("/styles.css"))`
returns a validated application URL and works with the normal HTML DSL. Missing or malformed
metadata and unregistered logical URLs fail explicitly; production never silently falls back to an
unversioned URL. With `woge.development=true`, the same registered URL resolves to its original path.
This is the existing wogeDev guard, not a dependency on development modules or spikes.

Spring Boot conditionally exposes the shared `AssetUrls` bean and registers ordinary MVC/WebFlux
resource handlers. Generated assets use `public, max-age=31536000, immutable`; unknown hashes or
files return 404. Spring's resource resolution owns traversal protection, content type, GET/HEAD,
range and conditional file responses. Woge does not install a proxy or implement a file server.

`verifyWogeProductionArtifact` compares the packaged manifest to the current generator output,
recomputes the actual packaged tree hash and rejects missing, modified or unregistered generated
assets. It still excludes development tooling. An empty static tree has a valid empty manifest.

## Alternatives considered

- **Require Vite/Node:** unnecessary for files that need no JavaScript transformation.
- **Rename each file separately:** breaks relative CSS URLs without parsing and rewriting dependencies.
- **Use only Spring's version resource chain:** does not provide a host-neutral build manifest or
  verify the packaged tree, and ties the URL API to Spring.
- **Use a timestamp/build number:** invalidates identical builds and is not a content identity.
- **Keep all old hash directories:** leaves stale packaged files and weakens manifest verification.

## Consequences

Humans and agents use the same Gradle tasks, manifest and typed URL API. Tailwind or Vite may
produce the input tree, but remain optional tools. The task never transforms CSS, JavaScript or images.
Tree-level invalidation can transfer unchanged files after one asset changes; this is an explicit
simplicity tradeoff for the initial API, not a claim of per-file optimality.

The Spring Boot scaffold uses the generated stylesheet URL and verifies real HTTP bytes, immutable
headers and unknown-hash 404s on both stacks. Unit tests cover deterministic ordering, changed content,
deleted assets, encoded names, unsafe inputs, URL resolution and packaged artifact tampering.

## Follow-up

Development CSS hot updates are decided in [ADR 0071](0071-in-place-stylesheet-updates-in-development.md); production hashing does not install a watcher.
Specialized frontend tools can own their own dependency-aware naming while feeding their final tree
to this manifest. Ktor applications may use the same task and typed URLs but configure their own
ordinary static resource handler. Dedicated proxy/CDN deployment evidence remains part of #117.
