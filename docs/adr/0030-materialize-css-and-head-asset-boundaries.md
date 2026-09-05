# ADR 0030: Materialize CSS and head asset boundaries without a styling runtime

- Status: Accepted
- Date: 2026-09-05
- Decision owners: Woge maintainers
- Related issues: [#78](https://github.com/christian-draeger/woge/issues/78), [#88](https://github.com/christian-draeger/woge/issues/88), [#77](https://github.com/christian-draeger/woge/issues/77), [#42](https://github.com/christian-draeger/woge/issues/42)

## Context

ADR 0016 chose standards-native CSS, external assets by default and optional future build-time
scoping. M1 now needs a concrete public API that keeps complete stylesheets, declaration lists, asset
URLs and active head contexts distinct. It must work with plain CSS, generated CSS Module names and
Tailwind classes while preserving Woge's streaming and strict-CSP goals.

Generic HTML primitives alone leave important mistakes easy: a complete stylesheet can be written to
an attribute, a string can bypass the URL boundary, an inline stylesheet can terminate its raw-text
element, or cross-origin SRI can be configured without CORS. A comprehensive CSS DSL would solve a
different problem and make the API lag the browser platform.

## Decision

`woge-core` publishes two small CSS source types:

- `CssStylesheet`, created by `stylesheet(...)`, represents complete CSS source;
- `CssDeclarations`, created by `declarations(...)`, represents one declaration list.

Their factory parameters carry JetBrains `@Language("CSS")` metadata. Declaration lists use a
synthetic selector prefix and suffix so IntelliJ can understand their context. The annotation library
is compile-only metadata. Both factories preserve unknown and modern CSS unchanged after rejecting
invalid scalar text; they do not parse properties or interpolate untrusted data safely.

`HtmlWriter.style(...)` accepts only `CssStylesheet` and rejects a case-insensitive HTML `</style`
sequence before output. `Attributes.styles(...)` accepts only `CssDeclarations`; repeated calls merge
trimmed, non-empty contributors in source order. Ordinary `attribute("style", ...)` is rejected.
Classes remain ordered strings and have no Tailwind-specific public type.

The head API writes standards-shaped HTML through these focused helpers:

- `stylesheet(...)` for external CSS;
- `style(...)` for an explicitly policy-approved inline sheet;
- `moduleScript(...)` for an external JavaScript module;
- `preload(...)`, `metadata(...)` and `propertyMetadata(...)` for common head values;
- `assetLink(...)` as the application-owned escape hatch for other link relations.

Asset references use `HtmlUrl`. CSP nonces and Subresource Integrity metadata have explicit validated
value types. A cross-origin URL with SRI requires an explicit `CrossOrigin` mode. Woge validates
syntax and output context; the application still generates nonce entropy, sends CSP and CORS headers,
hosts assets and chooses its browser/toolchain compatibility policy.

Styles and head assets load in the initial page head. Patch fragments do not carry `style`, `link`,
`meta`, `script` or other executable content. The fallback runtime preserves targeted outer-region
attributes and applies incoming classes, declarations and custom elements through normal DOM and
custom-element lifecycle behavior. Optional CSS scoping remains a future build-time adapter.

## Alternatives considered

- **Use only generic element and attribute calls:** rejected because active contexts and common
  security metadata deserve compiler-visible boundaries and fail-before-write checks.
- **Publish a property-by-property typed CSS DSL:** rejected because it duplicates an evolving web
  standard, delays new syntax and does not remove the need for raw CSS.
- **Add Tailwind or CSS Modules types to core:** rejected because generated class names and utilities
  already follow normal HTML class semantics and their tools should remain optional.
- **Inject or replace styles from streamed patches:** rejected because it complicates CSP, ordering,
  caching, no-JavaScript behavior and deterministic SSR/patch parity.
- **Ship the prototype CSS scoper with these source types:** deferred until selector, source-map,
  ownership and asset-manifest conformance work is complete.

## Consequences

### Positive

- Web developers keep using normal CSS, HTML attributes and browser asset semantics.
- Kotlin and IntelliJ distinguish two easily confused CSS output contexts.
- New CSS syntax, CSS Modules and Tailwind can work without a Woge release.
- URL, raw-text, CSP and SRI mistakes fail at focused boundaries before output.
- Initial HTML and streamed patches use one predictable styling model without a client styling runtime.

### Negative

- Woge cannot prove arbitrary runtime CSS safe or semantically valid.
- Applications remain responsible for asset builds, CSP headers, nonce generation and deployment.
- Inline style attributes may be unsuitable under a strict CSP.
- A future scoping compiler needs a separate ADR if its evidence changes these boundaries.

## Follow-up

- Use these APIs in the executable Spring Boot quick start in [#73](https://github.com/christian-draeger/woge/issues/73).
- Validate optional Tailwind extraction and production integration in [#77](https://github.com/christian-draeger/woge/issues/77).
- Keep any component asset manifest and distribution work aligned through [#76](https://github.com/christian-draeger/woge/issues/76).
- Run the branded IntelliJ and browser release evidence tracked in [#42](https://github.com/christian-draeger/woge/issues/42).
