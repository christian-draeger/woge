# ADR 0100: Compose native navigation, overlay and feedback components

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#83](https://github.com/christian-draeger/woge/issues/83)
- Builds on: [ADR 0098](0098-source-owned-component-registry.md),
  [ADR 0099](0099-native-form-components-and-validation.md),
  [ADR 0048](0048-document-owned-accessibility-announcements.md),
  [ADR 0073](0073-native-first-headless-ui-primitives.md),
  [ADR 0090](0090-opt-in-document-navigation.md)

## Context

Applications need consistent navigation and transient interaction without requiring an SPA,
mandatory hydration or a JavaScript component framework. The core browser bundle has no room for
new component behavior. Source-owned plain CSS and Tailwind recipes must keep the same semantics.

## Decision

Publish three cohesive registry items with `css` and reference `tailwind` variants:

- `navigation-components`: breadcrumb, pagination, URL tabs, navigation menu and disclosure dropdown.
- `overlay-components`: dialog, modal drawer, native auto popover and explicit help tooltip.
- `feedback-components`: static alert, native progress, decorative skeleton, persistent toast and
  stable announcement region.

All markup uses the typed HTML writer. Reuse headless disclosure, dialog, popover and live-region
primitives without expanding their public API. URL tabs are ordinary links with `aria-current`, not
an ARIA tablist promising client-side panel switching. Navigation dropdowns are disclosure-wrapped
links, not application menus promising arrow-key navigation. Applications omit unavailable page
links rather than render fake disabled anchors.

Dialog and drawer triggers retain real fallback URLs. Only pages needing modal opening load the
existing `/assets/woge-ui/dialog.js` module and call `installWogeDialogs`. Native dialogs own focus
containment, Escape and close buttons. Modal outside clicks intentionally do not dismiss; native auto
popovers do light-dismiss. Tooltip help uses explicit keyboard/touch activation and a description
association; it is not a hover-only replacement for essential inline instructions. No new core
client code, client package export, runtime dependency or budget increase is needed.

Overlay IDs connect triggers and titles. The existing patch runtime closes removed dialogs and
popovers. Replacing a dialog's contents while retaining its region root may preserve that open root;
replacing a containing region removes and closes the old overlay. The optional dialog primitive
returns focus to a replacement trigger. Do not move overlays into `body` or a portal.

Render announcement roots once, empty, outside repeatable content. Each has a unique ID, registered
region name and `aria-atomic`. Patch its text, not the root. The runtime rejects duplicate registered
regions and ignores stale replayed revisions. Static alerts and toast bodies have no live role, so
repeated content cannot multiply announcement regions. Toasts do not auto-dismiss or steal focus.
Applications own translation, dismissal actions and announcement urgency.

Scoped CSS uses logical properties, shared reference-theme tokens and reduced-motion rules. Tailwind
uses static utility candidates and equivalent state hooks. An empty live region remains in the
accessibility tree; neither variant hides it with `display:none`.

## Alternatives considered

- Client-controlled ARIA tabs and application menus: defer until a concrete need justifies their
  full keyboard and native-navigation fallback contracts.
- Portaled overlays or custom focus traps: reject; native top-layer semantics already handle these.
- Toast timers and per-toast live regions: reject; they can lose actionable information or announce
  duplicate content after patches.
- New headless types or browser modules: unnecessary for these recipes.

## Consequences

All variants compile with shipped semantic tests. Cross-variant contracts strip only classes and
compare all thirteen patterns. Generated browser fixtures come from the real Kotlin recipes, not a
second handwritten implementation. The Chromium gate and shared three-engine suite exercise focus return/trapping, Escape,
outside interaction, RTL drawer placement, reduced motion, native fallback links, optional
navigation, patch disposal, stable announcements and automated accessibility checks. Tailwind is
compiled only for verification; plain CSS applications still need no Node.

## Follow-up

- Perform manual screen-reader and touch audits, especially tooltip descriptions, live-region timing,
  native dialog focus cycling and native popover support on shipping browser versions.
- Consider hover/focus tooltips and in-page ARIA tabs only with complete keyboard and fallback tests.
- Keep registry hashes, variant contracts and browser fixtures aligned when extending the bundles.
