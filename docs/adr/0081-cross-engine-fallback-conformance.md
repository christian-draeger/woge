# ADR 0081: Keep fallback conformance in the existing three-engine gates

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#39](https://github.com/christian-draeger/woge/issues/39)
- Builds on: [ADR 0007](0007-browser-support-and-progressive-enhancement.md), [ADR 0015](0015-limit-native-dpu-to-initial-document-optimization.md)

## Context

The fallback client already has pinned Chromium, Firefox and WebKit contract projects. The reference
browser gate runs enhanced and separate JavaScript-disabled journeys on WebFlux, MVC and Ktor,
including actual SSE invalidation, action revisions and focus. Recreating these as a large CI matrix
would duplicate evidence and slow the gate. The contract configuration lacked retained traces and
screenshots, and its HTML-only form test created a manual context inside an enhanced project.

## Decision

Reuse `browser-contract` and `reference-browser-smoke`. Move the contract HTML-only form journey to
separate JavaScript-disabled projects on each engine, preserving the existing real-host native
navigation and POST/303/GET projects. Keep the full runtime, action, ownership and SSE journeys in
their existing suites. All Playwright configurations retain failure traces and screenshots; existing
artifact steps upload contract, reference and development evidence even after failure.

Provide an explicit fallback selector only in the browser-contract fixture bootstrap. It installs
the published JS runtime directly; test both present and absent native DPU reflection under strict
CSP. Production already explicitly installs the fallback, so do not introduce production capability
negotiation or a test header/query bypass. Native initial-document optimization remains a separate,
off-by-default concern under ADR 0015.

Keep the browser/version window of ADR 0007 unchanged. The exact Playwright dependency and lockfile
pin reproducible engine builds, without experimental flags. Stable branded current/previous,
Firefox ESR and mobile release evidence remains distinct from engine conformance. The
[browser guide](../guides/browser-support.md) describes commands, evidence locations and limitations.

## Alternatives considered

- **Add another browser CI matrix:** duplicates the existing three-engine jobs and host journeys.
- **Only test Chromium or emulate another browser's user agent:** cannot prove engine equivalence.
- **Disable JavaScript inside an enhanced test's manual context:** conceals the configuration boundary
  and prevents normal page-fixture failure capture for that context.
- **Add a production force-fallback query parameter:** unnecessary while installation is explicit and
  risks confusing security policy with runtime selection.
- **Treat WebKit as complete Safari/mobile evidence:** does not reproduce shipping OS integration.

## Consequences

The gate stays small and failures become diagnosable with retained evidence. No production API or
security behavior changes. Tests prove core semantics across engines, not identical DOM internals or
all shipping browser versions. HTML-only workflows use navigation and refresh instead of live SSE.
The adapter native-form helper remains additional Chromium coverage; real-host reference projects
supply the all-engine form/navigation evidence. Local Firefox startup limitations are not CI skips.

## Follow-up

Run all engines when upgrading Playwright and record exact versions and branded/mobile smoke results
before release. If native capability negotiation ships, extend parity tests to cover actual selection
and safe fallback before enabling the optimization. Preserve artifact collection for new suites and
keep local launch failures explicit rather than weakening the supported-engine gate.
