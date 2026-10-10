# ADR 0064: Compose a non-secret application manifest from compiler metadata

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#146](https://github.com/christian-draeger/woge/issues/146)
- Builds on: [ADR 0049](0049-generated-region-descriptors.md), [ADR 0051](0051-typed-page-routes.md), [ADR 0052](0052-typed-action-executors-and-registry.md)

## Context

Tools need to know which typed pages, actions, components and regions an application defines.
Terminal help describes commands, not the application. Parsing Kotlin source or inspecting live
objects would create a second interpretation of the public APIs and might expose runtime data.

## Decision

The existing KSP processor emits a structural catalogue and a typed `wogeDescriptors` list per
package, using the same validated models as descriptor generation. Catalogue output is aggregating:
KSP invalidates it when contributing sources change or disappear. Public IDs retain existing route,
action, component and region identities; a build's input order never determines output order.
An unsupported or conflicting declaration still fails compilation through the existing diagnostics.

The host-independent `dev.woge.application` Gradle plugin provides `wogeManifest`. The Spring Boot
plugin applies it automatically. The task composes compiler catalogues from the main compilation
and compile dependencies into `.woge/manifest.json`; it never scans source or loads application classes.
Duplicate public identities across modules and unknown catalogue shapes fail explicitly.

Version 1 contains Woge/Kotlin versions, the explicitly selected host, enabled capabilities, frontend
mode, canonical documentation links and structural descriptors. Build settings are explicit rather
than guessed from classpath presence. Capabilities are declared configuration, not security permission.
No source bodies, rendered HTML, cookies, secrets or request/session data belong in the catalogue.
The public serializable `ApplicationManifest` model reads the same JSON in-process.

Normal `build` generates the manifest; a direct `wogeManifest` invocation first compiles the application.
Main KSP/Kotlin/Java compilation invalidates the old file before work starts, including failed builds.
`clean` removes only this generated file. The manifest stays outside production resources by default.
Compiler catalogues and typed structural lists may travel with libraries so downstream build tools
can compose their descriptors; they are not live endpoint or action registries.

## Alternatives considered

- **Reflect over running Spring or Ktor objects:** requires a running host and couples discovery to
  runtime configuration, lifecycle and secrets.
- **Scan Kotlin text:** duplicates compiler rules and mishandles generated or incremental sources.
- **Generate everything inside KSP:** the compiler does not own host and frontend build configuration.
- **Add an HTTP discovery endpoint:** adds a public exposure boundary that tools do not need.

## Consequences

One compiler interpretation serves generated APIs, build tools, tests, humans and coding agents.
HTML/CSS-only applications need no Node.js. The manifest describes available declarations, not proof
that every declared route is bound or authorized. Developers supply the selected host, actual Kotlin
version and enabled capabilities through normal Gradle configuration.

Consumers require schema version 1 and reject missing required fields or unknown descriptor kinds.
They tolerate additional object fields within that version. Removing/changing field meaning or adding
an unsupported kind requires a new schema version; readers never silently fall back to empty output.

## Follow-up

The Spring scaffold demonstrates generated `HomeRoute`, the matching in-process list, both host
selections, clean/incremental determinism, deleted declarations, failed-compilation invalidation and
production exclusion. Future IDE/MCP integrations can use the same public model; no parallel runtime
discovery API is introduced.
