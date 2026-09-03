# ADR 0016: Keep CSS standards-native with optional build-time scoping

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#88](https://github.com/christian-draeger/woge/issues/88), [#77](https://github.com/christian-draeger/woge/issues/77), [#76](https://github.com/christian-draeger/woge/issues/76), [#11](https://github.com/christian-draeger/woge/issues/11), [#42](https://github.com/christian-draeger/woge/issues/42)

## Context

Woge should feel native to web developers and keep pace with an evolving, modular CSS platform. Kotlin can add type boundaries and IntelliJ assistance, but a property-by-property DSL would duplicate browser standards, lag new syntax and teach a Woge-specific styling language.

Applications also need practical component-local styling and Tailwind compatibility. Any scoping design must survive server rendering, streamed replacement and source-distributed components without adding hydration, runtime style injection or unstable markup identity.

The [CSS authoring spike](../../spikes/css-authoring/evidence.md) exercised external, page and declaration CSS; current platform features in three browser engines; Kotlin interpolation and context errors; IntelliJ language-injection metadata; deterministic selector/keyframe rewriting; source maps; and Tailwind-like utility coexistence.

## Decision

Ordinary external CSS is Woge's default and zero-magic fallback. It requires no Kotlin or Woge preprocessing. Woge preserves selectors, declarations, custom properties and at-rules it does not understand and does not maintain a CSS property allowlist.

Woge may provide small context types for a complete stylesheet and an HTML declaration list. Candidate factory parameters carry JetBrains' supported `@Language("CSS")` metadata; declaration-list parameters additionally provide a synthetic rule through `prefix` and `suffix`. JetBrains Annotations is compile-only metadata. It is not a public runtime dependency, and external CSS remains fully usable in every editor.

The context types prevent a stylesheet from being sent to an attribute renderer and centralize output-context checks. They do not validate every CSS token. Style-block rendering rejects an HTML raw-text closing sequence, attribute rendering performs HTML attribute escaping, and untrusted values cannot be interpolated into CSS source. Narrow typed values may be added for proven security or correctness boundaries; Woge will not build a comprehensive typed CSS-property DSL.

Component scoping is an optional build-time tool, not part of the browser runtime or core rendering model. If shipped, it must:

- derive a stable generated scope from a stable qualified component identity rather than CSS contents;
- write the scope marker during normal SSR and every patch, with no hydration;
- guard local selectors with zero-specificity `:where(...)` and provide an explicit `:global(...)` escape;
- consistently scope local keyframes and animation references;
- preserve unknown CSS and emit useful source maps;
- output external CSS assets, deduplicated and deterministically ordered before patches use them;
- define parent/child scope ownership and pass a larger selector conformance corpus before becoming public.

Asset URLs use a separate content hash. This lets caches observe CSS changes without changing the markup identity shared by initial rendering and streamed patches.

Tailwind is an optional peer toolchain. Its adapter may scan or receive generated candidate metadata from Woge sources, but Tailwind classes remain ordinary HTML classes. The Woge component API, semantic markup, patch identity and plain-CSS path do not depend on Tailwind. Issue [#77](https://github.com/christian-draeger/woge/issues/77) owns extraction and production integration.

The production path prefers external same-origin styles under a strict CSP. Inline style blocks require a nonce or hash; style attributes may require a weaker `style-src-attr` policy and therefore are not the strict-CSP default. Minification happens after optional rewriting and must preserve unknown syntax and source maps.

## Alternatives considered

- **Only external application CSS:** retained as the default but insufficient as the sole API because small page/component styles benefit from colocation and Kotlin context types.
- **Unscoped annotated CSS strings only:** accepted as an option but does not address source-distributed component-local ownership.
- **Svelte-like build-time scoped CSS:** accepted as an optional direction with a lower-specificity guard and stable server-owned identity; the spike is not yet a production compiler.
- **Comprehensive typed Kotlin CSS DSL:** rejected because it duplicates a fast-moving web standard, delays new syntax, weakens transferable CSS knowledge and inevitably needs raw escape hatches.
- **Runtime style injection or CSS-in-JS:** rejected because it complicates CSP, caching, ordering, SSR/patch parity and no-JavaScript behavior.
- **Derive scope identity from CSS contents:** rejected because every style edit would invalidate server-rendered and patch markup identity.
- **Make Tailwind the Woge styling language:** rejected because it would make an optional third-party build tool part of the component and runtime contract.

## Consequences

### Positive

- Web developers use normal CSS concepts and new platform syntax immediately.
- IntelliJ can provide CSS assistance inside Kotlin without a runtime IDE dependency.
- External CSS, annotated strings, optional scoping and Tailwind can coexist.
- Scope identity remains stable across SSR, patches and asset revisions.
- CSP, cache and minification behavior follows normal web deployment practices.
- Compiler-visible context types improve human and AI-generated code without inventing a second styling language.

### Negative

- Kotlin cannot statically prove arbitrary interpolated CSS values safe or valid.
- Non-IntelliJ editors may offer less assistance inside Kotlin strings.
- A production scoper needs substantial selector, source-map and asset-pipeline conformance work.
- Supporting both plain and scoped assets requires deterministic manifest and ownership rules.
- Strict-CSP applications should avoid convenient style attributes and may avoid inline blocks.

## Follow-up

- Validate Tailwind extraction and production integration in [#77](https://github.com/christian-draeger/woge/issues/77).
- Decide the source-distributed component and asset model in [#76](https://github.com/christian-draeger/woge/issues/76).
- Materialize CSS context types and output-context tests with the M1 HTML API rather than publishing the spike package directly.
- Add the larger selector/scoping conformance corpus before creating a public CSS compiler module.
- Include a branded IntelliJ completion/inspection smoke and branded browser CSS smoke in release evidence.
