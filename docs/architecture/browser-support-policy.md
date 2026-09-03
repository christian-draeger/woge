# Browser support and progressive enhancement

This is the living compatibility matrix for Woge. It describes the intended contract before a usable Woge runtime exists; rows gain links to executable fixtures as the corresponding milestone lands.

Last policy review: 2026-09-03.

## Support tiers

| Tier | User-visible contract | Supported browsers | Required evidence |
| --- | --- | --- | --- |
| HTML baseline | Useful pages, navigation, reads and core mutations work with JavaScript disabled | Current and previous stable Chrome, Edge, Firefox and Safari; current Firefox ESR; current Chrome Android and Safari on iOS | Semantic HTML assertions on every PR; no-JavaScript journey in Chromium, Firefox and WebKit; branded/mobile smoke test before a release |
| Woge enhancement runtime | Eligible navigation, forms, deferred regions and live updates can use streamed patches | Same set as the HTML baseline | Playwright Chromium, Firefox and WebKit on every runtime change; exact browser/tool versions recorded in release evidence |
| Optional local islands | Only the explicitly enhanced interaction; the containing page and authoritative workflow keep their documented fallback | Declared by each island adapter and never broader than its toolchain supports | Adapter-owned browser matrix and fallback test |
| Experimental platform adapter | Progressive optimization such as native Declarative Partial Updates (DPU) | Only named browser/version configurations with runtime capability detection | Reproducible experimental fixture; fallback runtime remains the supported path |

“Current and previous” is evaluated on the release date, not hard-coded in library source. A release record names exact versions. Playwright's WebKit is a useful cross-engine signal but is not branded Safari, so it does not replace the release smoke test. Unsupported legacy browsers and embedded webviews may still receive the HTML baseline, but they are not compatibility targets until an adapter documents them.

The enhancement runtime may depend by default only on APIs available across the supported stable set. A narrowly missing API needs a tested fallback or a small replaceable polyfill; user-agent sniffing is not a substitute for capability detection.

## Behavior without JavaScript

| Capability | HTML-only behavior | Enhanced behavior |
| --- | --- | --- |
| Page navigation | A real `href` performs ordinary document navigation | Eligible links may request and apply a document/region response while preserving URL and history semantics |
| Form mutation | A real `form`, `method`, `action` and successful Post/Redirect/Get flow; invalid input returns a complete usable page | The same server validation and authorization may return focused validation or multi-region patches |
| Deferred region | The non-script response resolves required content before completing, or provides an ordinary link to it; loading placeholders are never the only route to information | Shell-first rendering may replace regions as work completes |
| Multi-target update | Redirected full page reflects every authoritative change | One response may update several explicitly addressed regions |
| Live update | Data remains reachable through navigation and manual refresh | SSE or a later transport can apply authorized, revision-checked patches |
| Dialog, drawer or popover | The task has an ordinary page or in-flow control | Browser APIs may enhance presentation and focus handling |
| Client-only sensor or continuous interaction | An explanation is shown; no fake server fallback is required for capabilities such as camera input or drag feedback | An explicit local island may own the interaction and submit standard data back to the server |

Authentication, authorization, CSRF validation and input validation are identical in both paths. An enhancement request is never a privileged alternate endpoint.

## Navigation, loading and focus

- Initial and fallback pages keep landmarks, skip links, one meaningful page heading and a logical document/focus order.
- A replaced region keeps focus when the focused element remains valid. If an action removes it, focus moves to the nearest meaningful owner or the page heading according to the component contract; it never resets to `body` silently.
- Validation places a linked error summary before field errors and moves focus predictably according to the form contract.
- Background loading does not move focus. Busy state uses semantic or ARIA state on the affected region and remains understandable without animation.
- Important patch results use an appropriately scoped status message so assistive technology can announce them without stealing focus.
- Navigation updates document title, URL/history and focus as a real navigation would. Back/forward behavior is part of the browser TCK.
- Motion respects `prefers-reduced-motion`; content and completion are not communicated by animation alone.

## CSS compatibility policy

CSS support is tracked per feature, because CSS is a collection of evolving modules rather than a single version. Woge accepts ordinary external stylesheets and CSS text. It preserves selectors, declarations, custom properties and at-rules it does not understand; there is no property allowlist or Woge translation layer.

Authors may use the normal cascade, fallback declarations, `@supports` and media queries. Woge preprocessing is never required for browser feature detection. A scoped-style tool, if accepted later, must preserve unknown syntax even when it rewrites selectors.

The initial reference-application matrix is:

| Feature | Woge documentation tier | Fallback expectation |
| --- | --- | --- |
| External CSS, custom properties and logical properties | Stable default | Normal cascade and physical-property fallback where product browser data requires it |
| User-preference queries such as reduced motion and color scheme | Stable default | Usable static presentation |
| Cascade layers | Stable default | Source order remains intentional for consumers that omit a layer |
| Container queries and query units | Stable default | Readable block layout without container-dependent enhancement |
| Native CSS nesting | Stable default | Production toolchain may flatten it for a deliberately older consumer target; Woge does not |
| Modern color functions including `oklch()` | Stable default | Earlier color declaration before the modern value where contrast or branding is critical |
| `@supports` and cascade feature fallbacks | Stable default | The fallback branch is itself tested |
| View-transition names and cross-document transitions | Experimental opt-in until the supported-browser matrix is green | Navigation works normally with no transition |
| Draft selectors, properties or at-rules without stable-set interoperability | Experimental opt-in | No core content, action or accessibility dependency |

The CSS authoring spike in [#88](https://github.com/christian-draeger/woge/issues/88) verifies this matrix against the then-current stable browsers and records the exact versions. A feature moving between Baseline stages can change documentation and examples without a Woge release if runtime code is unaffected. Removing a documented fallback or narrowing the supported browser set requires an ADR.

## Test and update process

1. Every browser-runtime change runs the reference journey with JavaScript enabled and disabled in pinned Playwright Chromium, Firefox and WebKit.
2. Release evidence records Playwright and browser versions plus branded desktop/mobile smoke results. A failure blocks the release or is published as a named exception.
3. Each browser-facing issue adds its behavior to a shared fixture instead of creating an isolated demo.
4. Compatibility changes update this page in the same pull request. A support-tier change also updates [ADR 0007](../adr/0007-browser-support-and-progressive-enhancement.md).
5. Feature choices consult [Web Platform Baseline](https://web.dev/baseline) and specifications, then verify actual behavior in the Woge fixture. Baseline “Newly available” means interoperable across its core browser set; “Widely available” adds 30 months of availability. Neither replaces product evidence.

## Primary references

- [Web Platform Baseline](https://web.dev/baseline)
- [Playwright browser coverage](https://playwright.dev/docs/browsers)
- [W3C CSS Snapshot 2026](https://www.w3.org/TR/css-2026/)
- [WCAG 2.2 understanding focus order](https://www.w3.org/WAI/WCAG22/Understanding/focus-order.html)
- [WCAG 2.2 understanding status messages](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html)
