# ADR 0090: Keep document navigation native by default

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#51](https://github.com/christian-draeger/woge/issues/51)

## Context

Document navigation changes the URL and document identity. Region actions are POST commands within
one document epoch; they must never acquire history entries or become a client-side router.
The core browser asset has a fixed 36,000-byte ceiling and must not grow for navigation.

## Decision

Recommend complete server-rendered multi-page navigation first. Cross-document View Transitions
(`@view-transition { navigation: auto; }`) can animate native navigation; Speculation Rules can
prefetch/prerender real documents. These platform features preserve document lifecycle and browser
expectations without a Woge navigation runtime. Use reduced-motion CSS to disable transitions.

Offer a separate, explicitly installed ES module for applications needing a route-region swap or
persistent browser-owned shell/islands. Every URL remains a real GET route returning complete HTML.
Only marked same-origin anchors are intercepted; their href is never rewritten. Targets, downloads,
modified clicks, hash-only links, external URLs, non-HTTP schemes and opt-outs stay native.

Exactly one route region is declared. Its name, document language/direction, head assets, base URL,
protocol version and application compatibility token must agree before a swap. Copy the title and
explicitly marked metadata, not arbitrary scripts or styles. Missing/incompatible declarations,
non-HTML/error responses and cross-origin redirects escape through location.assign, without retries.
Inline executable content and new assets require native navigation; scripts are never evaluated.

Navigation is not a patch and cannot migrate a managed page's region registry across epochs.
If either document declares render-session metadata, registered/live/deferred regions or enhanced
actions, use full navigation. This deliberately conservative boundary closes existing requests and
SSE on real pagehide and starts the new document's runtime normally. No synthetic pagehide, shared
registry mutation, POST replay or second runtime is introduced. A persistent shell must not contain
managed regions either. Applications can opt into navigation independently of actions, forms or SSE.

Use existing browser-state primitives for keyed dirty controls, file nodes and islands inside an
eligible route. Conflicts escape to full navigation; outside-route state remains browser-owned.
Navigation owns heading focus (h1 or data-woge-heading), title announcement and document scroll;
it does not restore old input focus. History stores only URL and scroll, never HTML or form values.
Set history.scrollRestoration to manual while installed and restore its previous setting on disposal.
Forward navigation scrolls to the fragment or top; traversal restores the saved position. Reload,
deep links and no-JavaScript navigation always render the complete document.

## Alternatives considered

- **Native MPA plus View Transitions/Speculation Rules:** recommended default; cannot independently
  keep a route shell alive or replace only a declared region.
- **Client router, partial-response protocol or HTML cache:** unnecessary second route model and
  risks stale security/session state. Fetch full authoritative HTML instead.
- **Migrate active action/SSE registries:** requires new document lifecycle semantics and core bytes;
  choose the already complete native escape path instead.
- **Bundle into the core:** violates the fixed byte budget and makes an optional capability mandatory.

## Consequences

The optional file is separately measured and gated, including its classpath delivery. Shared browser
state code is imported without modifying the core graph. Native navigation remains the universal
fallback. Managed documents deliberately do not receive faster route swaps in this first version.

## Follow-up

Measure native platform features on shipping browsers. If managed document swaps are needed, design
an explicit disposable document-runtime lifecycle before extending eligibility. Keep that work out
of action patch semantics and retain the full-navigation path. See the
[enhanced navigation guide](../guides/enhanced-navigation.md).
