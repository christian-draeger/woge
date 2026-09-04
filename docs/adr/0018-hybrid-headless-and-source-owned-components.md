# ADR 0018: Combine binary headless primitives with source-owned component recipes

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#76](https://github.com/christian-draeger/woge/issues/76), [#77](https://github.com/christian-draeger/woge/issues/77), [#80](https://github.com/christian-draeger/woge/issues/80), [#88](https://github.com/christian-draeger/woge/issues/88), [#13](https://github.com/christian-draeger/woge/issues/13)

## Context

Woge needs a substantial component catalog without making web developers learn a mobile UI model or surrender normal HTML and CSS. Components must support complex responsive screens, plain modern CSS and Tailwind, SSR, partial replacement and progressive enhancement. Accessibility, output safety and form behavior need centrally deliverable fixes, while visual composition and theming need application-level freedom.

The [component distribution spike](../../spikes/component-distribution/evidence.md) rendered one real project board through binary headless, source-owned, styled binary and hybrid models. It tested Kotlin diagnostics, stable instance and patch identity, semantic parity between CSS modes, source provenance, local edits, update conflict planning, packaged resources and artifact/source sizes.

## Decision

Woge's primary component model is a hybrid: stable binary headless primitives plus optional source-owned visual recipes.

`woge-ui-headless` is a public binary module depending only on `woge-core`. It owns typed state/variants, semantic HTML defaults, accessibility behavior, output-context escaping, form/action integration and stable patch identity. Its public Kotlin API follows semantic versioning and binary API validation. Documented semantic markup and no-JavaScript behavior are contract tests, not implementation accidents.

The Woge registry distributes Kotlin composition and ordinary CSS or complete Tailwind class candidates as source. Registry releases are immutable. A versioned manifest records SPDX license, repository/revision/path provenance, Kotlin/headless compatibility, hydration requirement, Tailwind candidate roots and every file hash. Installation writes a lock file. Update tooling performs a three-way hash comparison and reports safe replacements, preserved local edits and merge conflicts; it never silently overwrites customized source.

Registry recipes compose headless primitives by default. They may add application structure, variants and styling, but do not copy security-sensitive behavior when a primitive already owns it. A fully source-owned component is allowed when no reusable behavioral primitive exists, with the explicit cost that the application must merge accessibility and security fixes.

Plain CSS is the reference styling path and remains directly IDE-editable. Tailwind is an optional build adapter and scans registry-declared Kotlin candidate files containing complete static utility tokens. Moving a recipe between plain CSS and Tailwind may change classes/assets and presentation only; HTML meaning, links, form requests, region identity and patch protocol remain the same.

Styled binary components are not the primary catalog. Woge may later ship a narrow optional starter theme when its selector, layer, custom-property, asset and compatibility contracts are explicit. Core components cannot require that theme, Tailwind, JavaScript or hydration.

Registry source/catalog and installer/update tooling are build concerns, not server/browser runtime dependencies. Spring MVC, Spring WebFlux and Ktor use the same component APIs above the framework-neutral host port. This decision extends the initial graph in [ADR 0006](0006-initial-module-boundaries.md) after the deferred M0 component spike.

## Alternatives considered

- **Source-owned components only, similar to shadcn:** offers the most direct customization and transparent code. Rejected as the sole model because every application forks semantics, escaping and accessibility and must merge urgent fixes itself.
- **A packaged styled library similar to MUI:** centralizes fixes and makes initial setup small. Rejected as the primary model because deep customization creates a large variant/slot API or fragile CSS overrides, and DOM/CSS become broad compatibility surfaces.
- **Binary headless primitives only:** centralizes behavior and leaves CSS open. Rejected as the complete developer experience because every application would still rebuild common visual composition and state recipes.
- **Generate opaque HTML/CSS from a registry at build time:** can reduce checked-in code. Rejected because application ownership, IDE navigation, diffs and AI edits become less transparent.
- **Tailwind-specific components:** can produce a coherent utility-first catalog. Rejected because plain CSS is first-class and component semantics cannot depend on one styling tool.

## Consequences

### Positive

- Web developers customize checked-in Kotlin, HTML structure and CSS with familiar tools.
- Accessibility, output safety and patch behavior can be fixed centrally in headless primitives.
- Source diffs, hashes, license and provenance make installation and upgrades auditable.
- Plain CSS and Tailwind coexist without separate business or patch behavior.
- Typed variants and compiler diagnostics guide humans and coding models without reflection-heavy conventions.
- Components stay host-framework-neutral and require no application-wide client runtime.

### Negative

- The project must maintain both binary compatibility and an immutable source registry/update format.
- Some updates require a human-reviewed merge of locally owned files.
- Recipe and primitive versions need a compatibility matrix and contract tests.
- A hybrid catalog is more infrastructure than either a JAR-only or copy-only library.
- Fully source-owned exceptions can drift from upstream accessibility/security fixes.
- Optional packaged themes need their own CSS compatibility and asset budgets if introduced.

## Follow-up

- Add `woge-ui-headless` and component-manifest/build-tool boundaries to the M1 scaffold in [#13](https://github.com/christian-draeger/woge/issues/13).
- Define the first accessibility-focused primitive set and shared TCK in [#80](https://github.com/christian-draeger/woge/issues/80).
- Turn the spike manifest and update planner into supported tooling only after schema, signing/release provenance and Windows path tests are designed.
- Apply the frontend performance budgets from [#46](https://github.com/christian-draeger/woge/issues/46) to each catalog component and optional asset.
