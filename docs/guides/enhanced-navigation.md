# Enhanced navigation, only when you need it

**Start with ordinary multi-page navigation.** Every URL should answer a normal GET with complete
HTML. Links, reload, deep links and forms then work with or without JavaScript on MVC, WebFlux and
Ktor. Enhanced navigation is separate from pages, forms, actions and SSE; none requires it.

## Prefer the browser first

Cross-document View Transitions animate ordinary same-origin navigation in supporting browsers:

```css
@view-transition { navigation: auto; }
@media (prefers-reduced-motion: reduce) {
  ::view-transition-group(*) { animation: none; }
}
```

Speculation Rules can prefetch or prerender complete documents before a click. They are browser
hints, not a router. Only speculate safe GETs; consider bandwidth, authentication and privacy.
Unsupported browsers still follow the original anchors. These two native features plus plain MPA
navigation are the recommended default. See the platform documentation for
[View Transitions](https://developer.mozilla.org/en-US/docs/Web/API/View_Transition_API) and
[Speculation Rules](https://developer.mozilla.org/en-US/docs/Web/API/Speculation_Rules_API).

Use Woge's optional helper only when you need to keep a browser-owned shell alive or swap one route
region instead of the whole document. It fetches the real URL's complete HTML and extracts that
region. It does not install a client-side router or turn server routes into an SPA.

## Mark the HTML explicitly

The helpers below are imports from `dev.woge.html`. They only write HTML attributes:

```kotlin
nav(attributes = { enhancedNavigation() }) {
    a(attributes = { url("href", applicationUrl("/notes")) }) { text("Notes") }
    a(attributes = {
        url("href", applicationUrl("/account"))
        enhancedNavigation(false)
    }) { text("Account: full navigation") }
}
main(attributes = { navigationRegion("route") }) {
    h1 { text("Notes") }
    p { text("The complete server-rendered page content goes here.") }
}
```

An anchor can call `enhancedNavigation()` itself instead of inheriting it from a container.
The nearest marker wins, so `false` opts out. Keep exactly one route region with the same name
across compatible pages. This is **not** an action patch target. Put persistent controls/islands
outside it, or follow the existing [keyed browser-state rules](browser-owned-state.md) inside it.
The [compiled DSL tests](../../modules/woge-core/src/test/kotlin/dev/woge/html/EnhancedNavigationTest.kt)
exercise these markers without changing `href`.

In every compatible page's head, use `metadata("woge-patch-protocol-version", "1")` and
`metadata("woge-navigation-version", "your-release-token")`. The first matches the shipped
`WOGE_PATCH_PROTOCOL_VERSION`; the second is your explicit shell/assets compatibility token.
Change it when deploying incompatible HTML/assets. Missing or different values mean full navigation.
See [version alignment](compatibility-and-upgrades.md).

The title updates automatically. To update a particular named meta element, use the normal DSL:

```kotlin
meta {
    attribute("name", "description")
    attribute("content", "Notes for this project")
    navigationMetadata()
}
```

Unmarked head assets/metadata, language and direction must match. New scripts, styles, a different
base URL or executable content in the route region require native navigation. Scripts from fetched
HTML are never evaluated. Keep asset URLs absolute or use an identical explicit base URL.

## Install only on opted-in pages

The Node-free JVM asset serves `/assets/woge/navigation.js` alongside its existing shared modules.
Use `moduleScript(applicationUrl("/assets/navigation-bootstrap.js"))` in the head and put this small
ordinary JS file in your application's static assets:

```js
import { installWogeNavigation } from "/assets/woge/navigation.js";
const navigation = installWogeNavigation();
// An application removing this capability can call navigation.dispose().
```

For npm builds, import from `@woge/fallback-client/navigation`. Static npm deployments copy the
whole `dist` directory: `woge-navigation.js` uses its adjacent state helpers. The optional entry
is not imported by the core runtime. No Node or Vite is needed for plain HTML/CSS applications.
The separate file, shared files and complete dependency graph have
[fixed byte budgets](../performance/budgets.md).

## Browser ownership and escape paths

- Only marked, unmodified, primary-button, same-origin HTTP(S) anchors are eligible. Downloads,
  targets (including `_self`), external links, `ping`, non-HTTP schemes, same-page fragments and
  opt-outs retain native behavior. `href` is never rewritten.
- Successful swaps update the region, title, marked metadata and history together. Same-origin
  redirects use the final URL. Back/forward refetch authoritative HTML; no HTML/form cache is kept.
- Navigation owns focus: the new `h1` or `[data-woge-heading]` gets focus without scrolling. Give the
  shell an initially empty `role="status"` element with `data-woge-navigation-status` to announce the
  new title politely. Input focus preservation is for region actions, not document navigation.
- While installed, the helper owns `history.scrollRestoration="manual"`. New pages go to the top or
  URL fragment; traversal restores saved document scroll. Disposal restores the previous browser
  setting. No animation is added, including with reduced motion. Only scroll positions are retained
  in a bounded, in-memory map; dirty values/HTML are never persisted.
- Both documents must be isolated from managed Woge actions, registered regions, live and deferred
  work. Such pages deliberately use **full navigation**: real `pagehide` closes their existing SSE
  and action work, and the destination's normal bootstrap starts its own runtime. Keeping a managed
  document registry alive across a new epoch is not supported in this version.
- Network errors/timeouts, non-HTML/non-2xx replies, cross-origin redirects, state conflicts or
  version mismatches call `location.assign` once. No automatic GET retry or POST replay occurs.
  Reload/deep links and JavaScript-disabled navigation always remain full document GETs.

`woge:before-navigation` and `woge:after-navigation` are document events for application-owned
controllers, not patch lifecycle events. They contain no HTML, form values or authorization data.
The boundary and its deliberate managed-document limitation are recorded in
[ADR 0090](../adr/0090-opt-in-document-navigation.md).
