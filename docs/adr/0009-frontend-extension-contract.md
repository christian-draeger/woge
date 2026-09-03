# ADR 0009: Layer frontend extensions over semantic server HTML

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#4](https://github.com/christian-draeger/woge/issues/4), [#5](https://github.com/christian-draeger/woge/issues/5), [#21](https://github.com/christian-draeger/woge/issues/21), [#43](https://github.com/christian-draeger/woge/issues/43), [#75](https://github.com/christian-draeger/woge/issues/75), [#76](https://github.com/christian-draeger/woge/issues/76), [#77](https://github.com/christian-draeger/woge/issues/77), [#80](https://github.com/christian-draeger/woge/issues/80), [#88](https://github.com/christian-draeger/woge/issues/88)

## Context

Woge must support polished dashboards, dense forms, data tables, overlays, live feedback and application-specific interactions. A server-driven framework can fail this goal either by forbidding normal browser techniques or by adding a second opaque client framework that owns the DOM, styling and state.

The intended audience already knows HTML, CSS and JavaScript. Tailwind and reusable component systems should be first-class options, but neither may define Woge's component semantics. Local client state is sometimes appropriate for continuous interaction, while core navigation and mutations still need useful HTML-only paths.

## Decision

Frontend extension is layered over semantic server HTML through four additive lanes: standards-shaped HTML, standards-native styling, small lifecycle-managed browser behavior and explicit local islands. The detailed ownership rules and complex-screen gates live in the [frontend extensibility contract](../architecture/frontend-extensibility.md).

Every HTML element remains open to arbitrary classes, custom properties, `style`, `data-*`, `aria-*`, custom attributes and custom elements. Applications load normal stylesheets and external JavaScript modules. Woge does not create a comprehensive typed CSS-property DSL, a typed Tailwind-utility catalog or an internal CSS feature allowlist.

Patch identity is independent from presentation. Generated rendered-region references, page epochs and revisions address updates; class names and arbitrary CSS selectors do not. Styling tools may change classes/assets without changing route, action, patch or progressive-enhancement semantics.

Plain CSS is the zero-tooling baseline. Tailwind, CSS Modules-style generated names and optional component-local scoping are replaceable build/authoring adapters. Current and unknown valid CSS is preserved according to the browser policy. Exact IDE-injection and scoping mechanics are decided by [#88](https://github.com/christian-draeger/woge/issues/88).

Small DOM behavior uses standard APIs through explicit mount/update/dispose ownership around patches. Native HTML capabilities such as dialog and popover are preferred when they satisfy the interaction. Core runtime and headless behavior require neither a virtual DOM nor an application-wide hydration graph.

A local island is an explicit subtree for interaction that genuinely benefits from client rendering/state. Its adapter owns that subtree and documents serialization, lifecycle, browser support, CSP, bundle cost and fallback. The surrounding document, server-authoritative workflow and unrelated components remain independent. No Kotlin/JS, Kotlin/Wasm or JavaScript framework becomes mandatory through this escape hatch.

The project operations screen and measurable matrix in the contract are the common acceptance fixture for CSS, Tailwind, component distribution, headless primitives and islands. A component demo alone cannot establish complex-application support.

## Alternatives considered

- **Server HTML plus no client extension points:** rejected because it cannot cover rich local interaction or established frontend tooling.
- **Virtual DOM/hydration as the default:** rejected because it duplicates server state and asks web developers to adopt a client-framework ownership model for core workflows.
- **Compose-style Kotlin UI abstraction:** rejected because it hides standard HTML/CSS concepts and does not match the target audience's browser knowledge.
- **Tailwind as Woge's component API:** rejected because utility classes are a presentation choice and dynamic extraction/build rules would leak into portable semantics.
- **A comprehensive typed Kotlin CSS DSL:** rejected because it would lag the CSS platform, multiply spellings and weaken existing IDE/browser documentation. Narrow typed design tokens remain possible.
- **Arbitrary JavaScript mutating any Woge region without ownership:** rejected because patches, dirty controls, focus and third-party lifecycle would become nondeterministic.
- **Require every interaction to have an equivalent no-JavaScript animation/widget:** rejected because some sensor/continuous interactions are inherently client-only; the containing task needs an honest fallback, not a fake equivalent.

## Consequences

### Positive

- Web developers retain normal CSS, DOM, custom-element and module skills.
- Tailwind and future styling tools remain optional and removable.
- Complex client behavior has an explicit escape hatch without turning the whole application into an island.
- Patch identity and accessibility behavior remain stable across visual themes.
- One representative screen produces comparable evidence for component decisions.

### Negative

- Woge must specify patch/controller/island ownership and test their lifecycle carefully.
- Plain CSS, Tailwind and optional behavior paths increase the integration-test matrix.
- A source-owned component model may require update tooling and provenance metadata.
- Some applications will deliberately cross out of portable server components and must own the extra browser dependencies.

## Follow-up

- Decide standards-native CSS literals and optional scoping in [#88](https://github.com/christian-draeger/woge/issues/88).
- Run the Tailwind extraction/build spike in [#77](https://github.com/christian-draeger/woge/issues/77).
- Compare source-owned, packaged/headless and hybrid component distribution in [#76](https://github.com/christian-draeger/woge/issues/76).
- Implement the framework-neutral asset/attribute contract in [#78](https://github.com/christian-draeger/woge/issues/78).
- Establish numeric runtime/CSS/interaction budgets from measured evidence in [#46](https://github.com/christian-draeger/woge/issues/46).
- Keep local-island implementation outside the initial module graph until a later spike proves one concrete interaction and boundary.
