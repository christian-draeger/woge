# ADR 0082: Audit interactions through a shared web accessibility contract

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#44](https://github.com/christian-draeger/woge/issues/44), [#120](https://github.com/christian-draeger/woge/issues/120)
- Refines: [ADR 0048](0048-document-owned-accessibility-announcements.md), [ADR 0058](0058-shared-accessible-form-errors.md), [ADR 0073](0073-native-first-headless-ui-primitives.md), [ADR 0081](0081-cross-engine-fallback-conformance.md)

## Context

Valid patches and visually correct pages do not prove usable web interactions. Existing tests
already cover silent deferred content, configured validation focus, preserved controls and native
primitives. They do not count live-region writes or apply one semantic, keyboard and reflow contract
to the reference dashboard and all shipped primitives. A background board notice incorrectly used
`role="status"` despite the user never asking to follow it.

## Decision

Keep the existing document-owned announcement and native focus policies. Do not add accessibility
operations to Patch IR, a global announcer, or a custom widget controller.

Publish one accessibility contract for primitives and published component families: native
alternatives and useful HTML-only outcomes, semantic names/relationships, keyboard journeys,
independent focus and announcement checks, 320 CSS-pixel reflow, reduced motion and strict CSP.
Require new component families to supply fixtures against that contract before publication. The
registry catalog is still planned; this audit does not claim a future catalog has passed.

Reuse the existing browser/reference projects and CI jobs. A shared test helper checks semantic
relationships in both modes and runs test-only axe WCAG A/AA and best-practice scans in enhanced
pages. Native projects remain JavaScript-disabled throughout, with synchronous DOM inspection
instead of axe's asynchronous injected script. Count live-region DOM writes separately from
validation and navigation focus tests. Counts represent announcement opportunities, not speech.

Exercise all four shipped primitives on the real reference board. Stress the actual dashboard
styles/table with dense rows and labelled fields, without introducing a second demo application.
Keep table semantics and wrap long text; local scrolling remains available for genuinely
wide content. Remove the board's unrequested live role, add its missing viewport/main/skip link,
and preserve contrast in both color schemes.

## Alternatives considered

- **Rely only on a scanner:** rejected; scanners cannot prove keyboard flow, meaningful outcomes or
  screen-reader speech, and injected asynchronous checks cannot execute in JavaScript-disabled pages.
- **Add another CI browser matrix:** rejected; the current native/enhanced three-engine projects and
  multi-host reference job already provide the required execution surfaces.
- **Announce every live invalidation:** rejected; contradicts ADR 0048 and produces unsolicited speech.
- **Build custom table, dialog and popover widgets:** rejected; native semantics and browser behavior
  already meet the reference journeys. Application styling must not replace them.

## Consequences

The contract is executable and the manual audit checklist is public. The browser scanner is only a
dev dependency of the fallback client's test package; normal applications gain no Node requirement.
There are no public Kotlin API or protocol changes and no weakened CSP/Trusted Types headers.

Automation is not certification. Real screen-reader, zoom, forced-color and physical-device audits
still require recorded manual results. Dense test fields exercise CSS, semantics and keyboard flow;
actual form dispatch/validation continues to use the real board and adapter contracts.

## Follow-up

- [Accessibility guide](../guides/accessibility.md) publishes the contract, evidence map, native
  alternatives and manual checklist. Record browser/OS/assistive-technology results before release.
- `client/woge-fallback-client/test-support/accessibility-contract.mjs` is shared by native and
  enhanced reference audits; `browser-tests/action-accessibility.spec.mjs` counts outcome writes and
  tests validation/navigation focus independently.
- New registry/component families must extend the same native/enhanced fixtures, including their
  own styling and failure outcomes, rather than treating a primitive-level scan as their audit.
