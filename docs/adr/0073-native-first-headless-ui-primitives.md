# ADR 0073: Start headless UI primitives from native HTML elements

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#80](https://github.com/christian-draeger/woge/issues/80), [#76](https://github.com/christian-draeger/woge/issues/76), [#79](https://github.com/christian-draeger/woge/issues/79), [#46](https://github.com/christian-draeger/woge/issues/46)
- Builds on: [ADR 0009](0009-frontend-extension-contract.md), [ADR 0018](0018-hybrid-headless-and-source-owned-components.md), [ADR 0046](0046-development-client-under-strict-csp.md), [ADR 0048](0048-document-owned-accessibility-announcements.md)

## Context

ADR 0018 created `woge-ui-headless`: typed, unstyled building blocks that own meaning,
accessibility and safe output, while applications own the looks. The module was still empty. #80
asks for a first set of three to five primitives that work without JavaScript, under a strict
Content-Security-Policy, across patches, and with both plain CSS and Tailwind.

Browsers now ship the hard parts of common widgets: `details` for show/hide, `dialog` for modals
(focus trap, inert background, Escape) and the `popover` attribute for small overlays (light
dismiss, Escape, focus return). Re-implementing those in JavaScript would be bigger, slower to fix
and usually less accessible.

## Decision

The first release has four primitives, picked from the reference task board:

| Journey | Primitive | Built on | JavaScript |
| --- | --- | --- | --- |
| Disclosure | `disclosure(summary) { … }` | `details` + `summary` | None |
| Overlay, blocking | `dialogLink(id, fallback)` + `modalDialog(id, title)` + `dialogCloseButton` | `a href`, `dialog`, `form method="dialog"` | Optional module, 663 B gzip |
| Overlay, small | `popoverButton(id)` + `popoverPanel(id)` | `popovertarget` + `popover` | None |
| Feedback | `Attributes.liveRegion(LiveRegion.STATUS / ALERT)` | `role="status"` / `role="alert"` | None |

**Native first.** A primitive only writes the native element with the right attributes. The browser
handles keyboard, focus and screen-reader state.

**Useful without JavaScript.** A dialog opens from an ordinary link to a fallback page that shows
the same content. Disclosure, popover and live regions are fully native. The fallback is a typed
route, so it cannot drift from the link.

**Small, opt-in behavior.** The only script is `/assets/woge-ui/dialog.js`, served from the
`woge-ui-headless` jar. `installWogeDialogs(root)` adds one delegated click listener, so links that a
patch adds later work too, and returns a function that removes it again. A plain left click opens
the modal; modifier clicks still open the fallback in a new tab. When the dialog closes, focus
returns to the link, or to its replacement if a patch swapped it. There is no hydration, no virtual
DOM and no inline script or style, so it runs under `script-src 'self'; style-src 'self'`. A unit
test records its gzip size (`[woge-metrics] behavior=dialog …`) and fails above 1 KiB.

**Typed, styling-neutral API.** `UiId` connects a trigger to its target and only allows ids that
are also safe CSS selectors and URL fragments. State (`open`) and kind (`LiveRegion`) are typed.
Every primitive accepts ordinary `attributes` for classes, so plain CSS and Tailwind recipes differ
only in class names. Titles are escaped text. Each primitive marks itself with `data-woge-ui` for
styling and tests, never for patch identity.

**Proven in the same journeys.** The reference task board uses all four with plain CSS. The
Tailwind example uses the same four with Tailwind classes. Browser tests cover keyboard use, focus
return (also to a replaced link), state across board patches, no JavaScript and a strict CSP, in
Chromium, Firefox and WebKit.

The public Kotlin API is checked with ABI validation.

## Alternatives considered

- **Invoker commands (`command="show-modal"`) instead of a script:** opens a dialog without
  JavaScript, but the link would no longer have a real fallback URL, and support is too new for the
  "current and previous" browser policy. Revisit once it is widely available.
- **ARIA menu, tabs or combobox first:** these need arrow-key handling and roving focus in
  JavaScript. They belong to M7 once the controller lifecycle has more evidence.
- **One controller per element with mount/dispose on every patch:** more code and more lifecycle
  bugs. Delegation already survives patches.
- **Styled components:** rejected by ADR 0018.

## Consequences

### Positive

- Very little code to maintain; browsers fix most accessibility bugs.
- Every primitive works without JavaScript and under a strict CSP.
- Plain CSS and Tailwind stay interchangeable.

### Negative

- A dialog needs a fallback page; that is extra server work for each dialog.
- Popover placement uses the browser default (centered) until CSS anchor positioning is widely
  available; applications position it with CSS.
- The popover's expanded state is exposed natively, but Playwright's ARIA snapshot does not show
  it, so tests check visibility and focus instead.

## Follow-up

- Add keyboard-heavy patterns (tabs, menu, combobox) and the component registry in M7.
- Feed the measured module size into the frontend budgets in [#46](https://github.com/christian-draeger/woge/issues/46).
- Reconsider invoker commands once they are widely available.
