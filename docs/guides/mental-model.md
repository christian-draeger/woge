# One HTML and HTTP mental model

Start with a complete page, normal links and a form. JavaScript may improve the interaction; it does
not define a second application. The [quickstart](quickstart-spring-boot.md) uses one action for both
POST/Redirect/GET and enhanced submission.

| Concept | Think of it as | What Woge adds |
| --- | --- | --- |
| Page | An HTML document returned by a GET | A typed page input and safe HTML writer |
| Region | An ordinary HTML element whose children can change | A generated descriptor for the HTML function and an opaque target |
| Action | A POST command, with server validation and authorization | A typed decoder and generated action URL; native success uses 303 then GET |
| Patch | New HTML for a region, not a new page | Checked Replace, Append or Remove operations; the region's outer element stays yours |
| Transaction | Several region replacements shown together | Explicit `.atomic()` grouping; animation is optional and never correctness |
| Revision | A region's update counter | Rejects stale/out-of-order updates; **not** an ETag, database version or authorization token |
| Enhancement | Optional browser behavior over working HTML | Typed form updates, deferred content and SSE-driven refresh |
| Recovery | Getting back to authoritative server HTML | Bounded safe GET/refetch or full navigation; never automatic POST replay |

Document navigation stays a GET to a real server route. Plain MPA navigation with native
View Transitions and Speculation Rules is the recommended default. The separate
[enhanced navigation helper](enhanced-navigation.md) can swap an explicitly declared route region;
it is not an action patch or a client-side router. Managed action/live/deferred documents keep
the complete native navigation path.

A simple page needs no region. A simple native form needs no patch protocol or revision handling.
For enhanced forms, `request.context.render` supplies the render session; `metadata`, `target` and
`updates` keep identity bookkeeping out of application code. The [compiled notes page](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt)
is the canonical example. Complete imports live there, not in a second documentation-only API.

## Browser modes and recovery

- **HTML-only:** links navigate and forms submit normally. Deferred placeholders must link to a
  [full-page GET](deferred-regions.md); a loading message alone is not a no-JavaScript fallback.
- **Enhanced:** the shipped module updates known regions. The server still validates and authorizes
  every request. Live regions use SSE notices followed by authorized GETs, not streamed mutations.
- **Degraded:** if enhancement cannot load, keep native navigation. If a patch fails or is stale,
  do not guess at DOM repairs. Check diagnostics, refetch only safe reads within the recovery budget,
  or navigate to the page. Some earlier valid patches may already be visible. An explicit
  [atomic unit](typed-actions.md#atomic-visual-updates) validates all members first; a whole stream
  remains non-atomic.

A POST failure may mean "not committed" or "committed but response lost". Woge cannot infer which.
The client never retries it; read current state before a deliberate resubmit. See
[troubleshooting](troubleshooting.md) and [explicit mutation reservations](typed-actions.md#reserve-a-mutation-explicitly)
when a domain needs durable replay protection.

## Development and production

The page/action model is the same on Spring MVC, WebFlux and Ktor. Hosts own HTTP/security integration;
your application owns data, permissions, markup and CSS. Use standard [HTTP caching](http-caching.md):
ETag/Last-Modified and 304 validate a representation, independently of region revisions.

`wogeDev` coordinates compilation, application restart and an SSE browser channel. It does not
redefine JVM classes. Production serves the built application and version-matched assets without
that channel or overlay. Configure `woge.render-identity.secret` from a secret store, shared by all
instances serving enhanced pages; Boot owns the render session. See [production configuration](spring-boot-starter.md#render-identities-for-enhanced-pages)
and [precise reload terms](development-loop.md#what-updates-mean).

## Optional layers

1. **HTML and plain CSS first.** Use normal elements for normal tasks; no library is required for a
   heading, link, labelled input or layout. Scripts, custom elements and platform APIs stay available.
2. **Unstyled primitives** add native semantics/keyboard behavior for dialogs, disclosures, popovers
   and announcements. [UI recipes](ui-primitives.md) expose attributes, classes and content slots.
3. **Tailwind** is optional class authoring, not a different component model or runtime.
4. **Source-owned components** are HTML functions you keep and customize in your app. They can use
   either CSS approach; this is not a mandatory published component library.
5. **Islands** are optional browser-owned widgets, such as a canvas editor. Keep ordinary navigation
   outside them and declare [ownership/preservation](browser-owned-state.md); Vite is optional.
   Use the [lazy local-island API](local-islands.md) for typed manifests and root-local mount/dispose.
   See the [compiled island example](../../examples/complexity-gradient/src/main/kotlin/dev/woge/examples/gradient/t09island/SketchPage.kt).

[Native DPU](browser-support.md#experimental-native-dpu) is an **experimental internal** initial-document
encoder, not an application switch or requirement. Deferred requests use the shipped fallback runtime;
follow-up [#230](https://github.com/christian-draeger/woge/issues/230) tracks further integration.

## Developer-experience doctrine

- Web concepts stay recognizable: HTML, HTTP, forms, CSS and browser tools.
- The shortest path is safe: escaped text, bounded input and checked unsafe requests.
- Complexity is progressive: a page first; regions, live updates and islands only when needed.
- The compiler beats runtime magic: generated typed references, not reflective dispatch.
- Framework coordination is integrated: hosts and `wogeDev` own lifecycle details.
- Escape hatches stay explicit: normal HTML/CSS/JS and host-local integrations remain yours.

Humans and coding agents use these same public APIs. The [versioned machine-readable index](../ai-dx/woge-framework-index.json)
points both to the same guides and compiled sources; no AI-only API exists.
