# ADR 0007: Guarantee an HTML baseline before browser enhancement

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#3](https://github.com/christian-draeger/woge/issues/3), [#4](https://github.com/christian-draeger/woge/issues/4), [#11](https://github.com/christian-draeger/woge/issues/11), [#75](https://github.com/christian-draeger/woge/issues/75), [#77](https://github.com/christian-draeger/woge/issues/77), [#88](https://github.com/christian-draeger/woge/issues/88)

## Context

Woge promises a web-native programming model, but “progressive enhancement” is too vague unless navigation, forms, deferred content, live updates and optional client-side behavior have explicit fallback rules. Browser support also affects the patch runtime and which CSS features can appear in official examples.

Declarative Partial Updates are experimental. Current CSS, on the other hand, evolves continuously across independent modules. Treating either as one fixed platform version would make compatibility claims misleading and could cause Woge to hide browser capabilities from web developers.

## Decision

Woge has four browser-support tiers: HTML baseline, supported enhancement runtime, optional local islands and experimental platform adapters. The exact family coverage, behavior matrix, accessibility rules and feature-level CSS matrix live in the versioned [browser support policy](../architecture/browser-support-policy.md).

Core navigation and mutations start as real links and forms. The no-JavaScript response completes a useful workflow through ordinary HTTP. Enhancements intercept only eligible interactions and preserve URL, history, validation, authorization and CSRF semantics. Deferred rendering has a complete or linked HTML-only representation; live data remains available by navigation or refresh.

Inherently client-only capabilities may use an explicit local island and provide an honest explanation when unavailable. They may not make the containing page, server-authoritative state or unrelated controls depend on hydration. Every island documents its own browser matrix and fallback.

Native DPU is an optional experimental encoder/adapter selected through capability detection. It never shapes the public Kotlin API, and a supported cross-browser patch path remains available.

The supported stable set covers current and previous stable Chrome, Edge, Firefox and Safari, current Firefox ESR, and current Chrome Android and Safari on iOS. Per-change CI uses Playwright's pinned Chromium, Firefox and WebKit. Release evidence also records branded desktop and mobile smoke tests because an engine build is not identical to every shipping browser.

CSS has no Woge version and no Woge property, selector or at-rule allowlist. External CSS and CSS literals preserve unknown syntax. Official examples classify features individually using stable-browser interoperability, specifications and executable fixtures. Newer features remain usable immediately with the normal cascade, `@supports` and explicit fallbacks; limited-availability features are opt-in when core behavior does not depend on them.

Dynamic updates preserve meaningful focus order, announce important status without unnecessary focus movement, expose localized busy state and retain real navigation semantics. The same accessibility contract applies to native and enhanced flows.

## Alternatives considered

- **Require JavaScript for all Woge applications:** rejected because links, forms, HTTP navigation and server rendering already solve core workflows robustly.
- **Promise “all modern browsers” without a matrix:** rejected because it cannot drive tests or release decisions.
- **Support only Chromium:** rejected because it conflicts with a standards-native library and would hide portability bugs.
- **Treat Playwright WebKit as complete Safari evidence:** rejected because the projects are valuable engine coverage but do not reproduce every branded browser and operating-system integration.
- **Expose native DPU as the primary protocol:** rejected while the feature is experimental and not cross-browser.
- **Allow only a Woge-approved CSS subset:** rejected because an internal catalog would lag CSS, break valid future syntax and discard web-developer knowledge.
- **Compile all CSS to a lowest common denominator:** rejected as a framework requirement; application build tools may still target older browsers deliberately.

## Consequences

### Positive

- Core workflows survive failed, blocked or intentionally disabled JavaScript.
- Runtime compatibility has named browser families and executable evidence.
- Native browser improvements can become optimizations without redesigning Kotlin APIs.
- Web developers retain ordinary CSS feature detection, cascade behavior and tooling.
- Accessibility expectations cover the transition between full documents and patches.

### Negative

- Native and enhanced paths both need end-to-end tests.
- Branded desktop and mobile release checks require more infrastructure than engine-only CI.
- Some live and local interactions cannot offer equivalent no-JavaScript behavior and must document that honestly.
- Official examples may need CSS fallback declarations even when Woge itself can pass newer syntax unchanged.

## Follow-up

- Build the shared browser matrix and no-JavaScript fixtures with the fallback runtime in [#4](https://github.com/christian-draeger/woge/issues/4).
- Record native DPU's exact experimental ceiling in [#3](https://github.com/christian-draeger/woge/issues/3).
- Verify the CSS feature matrix, IntelliJ injection and source preservation in [#88](https://github.com/christian-draeger/woge/issues/88).
- Apply the same tiers to Tailwind integration and local islands in [#77](https://github.com/christian-draeger/woge/issues/77) and [#75](https://github.com/christian-draeger/woge/issues/75).
- Revisit the exact support window before the first public release using recorded consumer and CI evidence.
