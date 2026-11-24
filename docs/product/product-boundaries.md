# Content and browser-owned work

Reviewed **2026-11-25** for the [product-boundary study](https://github.com/christian-draeger/woge/issues/296).
Use [the product-fit guide](choosing-woge.md) for the server-owned business workflows and
[the comparison contract](../performance/comparison-contract.md) for what each result covers.

## Choose by what owns the work

- **Choose Woge for server-owned Kotlin pages and actions.** Keep ordinary HTML navigation,
  validation and domain writes on the server. Add an optional browser module where a report
  benefits from one local visual control. [Local islands](../guides/local-islands.md)
- **Consider Woge build-time rendering for a small documentation site maintained in a Kotlin
  repository.** Markdown, reviewed source snippets and static file publishing fit this
  workflow. A personalized component needs a separately owned request boundary.
  [Woge documentation generator](../../site/generator/README.md)
- **Choose a content-first generator for a content team that needs content schemas, richer
  authoring integrations or independently deployed content.** Astro is a plausible choice
  when static articles need a small number of server-rendered personalized regions.
  [Astro content collections](https://docs.astro.build/en/guides/content-collections/) ·
  [Astro server islands](https://docs.astro.build/en/guides/server-islands/)
- **Choose a client-owned stack for an application whose dominant workflow is rich local editing.**
  Evaluate serialization, downloads, restoration, keyboard behavior and server reconciliation
  for the actual task. When a team already uses Vue, Svelte or Kotlin/JS, those ecosystems offer
  implementation routes for the client work. A chart alone can stay an integration, and
  URL-backed pagination can precede an external grid.
  [Complex inputs and displays](../guides/data-display-and-complex-inputs.md)

## Capability and adoption matrix

The table reports documented approaches available in the named lines as of the review date.
The two cases below verify specific artifacts. Other rows are architecture inferences from the
linked upstream documentation, with adoption work identified so teams can check it directly.

| Approach and release boundary | Suitable work and adoption cost | Sources |
| --- | --- | --- |
| Woge source and fixed pre-1.0 beta pilot | Kotlin server-owned HTML, action validation, region updates and optional islands. The docs generator is project tooling. Native declarative updates have a separate experimental browser boundary. Select a verified server adapter and pin source or kit artifacts. | [Woge compatibility](../guides/compatibility-and-upgrades.md), [browser boundary](../guides/browser-support.md#experimental-native-dpu) |
| templ released Go components | Compile HTML components into Go for SSR or static output. Good for a Go team that wants explicit templates and hosts. Add the appropriate Markdown, browser interactivity and deployment tools for the application. | [templ SSR and static rendering](https://templ.guide/) |
| Vaadin Flow 25 and Hilla 25 | Flow provides a JVM-owned component UI and server interaction. Hilla provides React screens and generated clients for Java endpoints. An existing Grid and form tooling justify an evaluation for a dense editable grid. Check client tooling, session or API ownership, authorization and component terms. | [Flow](https://vaadin.com/docs/latest/flow), [Hilla](https://vaadin.com/docs/latest/hilla), [Grid](https://vaadin.com/docs/latest/components/grid) |
| Phoenix LiveView 1.2 | Starts with an HTTP document and upgrades to a server process delivering diffs over a connection. Consider it for server-owned realtime UI on Elixir/BEAM. Budget for connection/state operation and explicitly verify the native workflow and recovery. | [LiveView lifecycle](https://hexdocs.pm/phoenix_live_view/Phoenix.LiveView.html) |
| Astro 7.4.13 and Node adapter 11.1.14 tested here | Markdown/content collections, static pages and on-demand rendering. Server islands move request-specific data behind an independent fetch. Actions support native forms. Keeping those dynamic features requires an adapter/runtime and explicit private cache policy. | [Content collections](https://docs.astro.build/en/guides/content-collections/), [server islands](https://docs.astro.build/en/guides/server-islands/), [actions](https://docs.astro.build/en/guides/actions/) |
| Qwik and Qwik City released line | Resumability supports a client UI with deferred activation. Qwik City includes routes, loaders and actions. Assess serialization boundaries, local interactivity and the optimizer/toolchain. Track the Qwik v2 next channel separately from the released guide. | [Qwik resumability](https://qwik.dev/docs/concepts/resumable/), [Qwik City](https://qwik.dev/docs/qwikcity/) |
| Kobweb 0.25.1 | Pre-1.0 released Kotlin/JS and Compose HTML framework with browser-owned state, static export and optional API routes. Consider it when Compose skills or Kotlin source sharing matter. Check browser bundle, JS interop and Kotlin/Compose compatibility. | [Kobweb project](https://github.com/varabyte/kobweb), [0.25.1 release](https://github.com/varabyte/kobweb/releases/tag/v0.25.1) |
| Next.js App Router 16 | React server and client component boundaries, server routes and a static export option. Assess React/Node adoption, deployment mode, caching and which features require runtime server support. The standalone Todo comparison is tracked separately. | [Server and client components](https://nextjs.org/docs/app/getting-started/server-and-client-components), [static export](https://nextjs.org/docs/app/guides/static-exports), [#277](https://github.com/christian-draeger/woge/issues/277) |
| SvelteKit 3.4.0 tested here | Route-level prerender, SSR and client rendering. A Svelte component can own local state and load a widget on demand. Static adapter works for the measured read-only report. Server actions need a supporting deployment and explicit serialization boundary. | [Page options](https://svelte.dev/docs/kit/page-options), [adapters](https://svelte.dev/docs/kit/adapters) |
| Nuxt 4 | Vue SSR, client rendering and route-level hybrid modes. Consider for a Vue team that needs a rich local app alongside public content. Choose runtime target, hydration, serialized data and caching per route. | [Nuxt render modes](https://nuxt.com/docs/4.x/guide/concepts/rendering) |
| ASP.NET Core Blazor Web Apps | Static SSR supplies server HTML. Interactive Server uses a server connection, WebAssembly runs in the browser, and Auto can switch on subsequent visits. A shared C# language leaves those distribution, server resources and serialization choices in place. | [Blazor render modes](https://learn.microsoft.com/en-us/aspnet/core/blazor/components/render-modes?view=aspnetcore-10.0) |
| KVision 9.7 | Released Kotlin/JS component framework. Consider when Kotlin browser ownership and its widget integrations fit the team. Account for the JS build, Kotlin/JS runtime and interop, and choose the API and deployment boundary. | [KVision guide](https://kvision.gitbook.io/kvision-guide/) |
| Thymeleaf 3.1, `kotlinx.html`, jte/KTE | Direct server template or Kotlin HTML alternatives on existing JVM hosts. The team owns HTTP/action semantics and picks a compatible enhancement layer. Compare the complete workflow on the same host when deciding a migration. | [Thymeleaf](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html), [Kotlin HTML DSL](https://github.com/Kotlin/kotlinx.html), [jte and Kotlin templates](https://jte.gg/) |
| htmx and Datastar released integrations | htmx requests and swaps fragments. Datastar offers server patches and client-side signals. Both can integrate with an existing JVM renderer. Test native fallbacks, security and connection behavior on the application rather than treating fragment primitives as the complete workflow. | [htmx documentation](https://htmx.org/docs/), [Datastar guide](https://data-star.dev/guide/getting_started) |

## Public article and reading preference

**Evidence type: matched article and preference workflow.** The Woge source checkout
`b2b15200b92e63e6c2058df6ffc58bc049062ff2` contains the static publishing work from
[#330](https://github.com/christian-draeger/woge/pull/330). Astro 7.4.13 used its released
Node adapter 11.1.14, a prerendered article, `server:defer` and a real Astro form action.
The Woge comparator kept its existing static renderer and used a small application-owned
Ktor preference route with Woge HTML and a JavaScript module to read the topic.

Both renders used the same Markdown, source snippet and stylesheet source. Chromium verified
the article text, five heading anchors, escaped code, title and description from an ordinary
static server with JavaScript disabled. The two renderers have different page wrappers,
Markdown defaults and CSS processing. Astro used a fenced copy of the same reviewed Kotlin
file and disabled smart punctuation for the comparison. The test suite checked these
authoring inputs and outputs. It did not measure authoring effort.

Alongside each live runtime, the settings link worked with JavaScript disabled. The native
form changed the allowlisted topic and returned 303 to a settings page. Reloading retained the
topic, and an invalid value returned 400 without changing it. A new Chromium context read
the default topic, while the original preference appeared in the optional region on the
same public article. The anonymous and personalized article bodies had identical hashes within
each approach. Both personalized endpoints responded with `private, no-store`.

| Observed output | Woge export and Ktor | Astro and Node |
| --- | ---: | ---: |
| Live article HTML body bytes | 6,610 | 11,985 |
| Browser resource body bytes through personalized article | 14,387 | 17,427 |
| Private topic response body bytes | 20 JSON | 41 HTML |
| Live public article cache header | `public, max-age=60` | `public, max-age=0` |

The measurements include the actual page markup. Woge's page includes its documentation
navigation and copy module, while Astro's contains inline server-island code and syntax
highlighting. The Woge harness configured its public cache header explicitly. On the generic
static server neither export had a cache header from the app. The Astro export still requested
its server island, which returned 404 without the runtime. The public article remained useful.
Use the static export alone for public reading and the corresponding runtime for personalization.
Production cache configuration and a CDN deployment were outside the study.

For a small Kotlin documentation site, keep reviewed Markdown and the static Woge export.
For a content-first site that needs structured content or several dynamic islands, evaluate
Astro's own content and deployment model. The case supports documenting the Woge/runtime
composition as an application integration. It creates no need for a content platform in Woge core.

## Local warehouse backlog chart

**Evidence type: matched read-only widget aspect.** The Woge chart was the exact
[#340 head](https://github.com/christian-draeger/woge/commit/1741e20f9df2a4221fb274df8f6a8ad15d54dfbb)
using the fixed Woge beta kit at `7bf1e3e`. The comparator was a SvelteKit 3.4.0 prerendered screen
with its static adapter 4.0.1. Both used Chart.js 4.6.2, the same 14 day rows, series, axes, CSS
source, canvas dimensions and local button behavior. The SvelteKit implementation copied the
chart drawing/local control code and removed the Woge-specific parent patch bridge.

With JavaScript disabled, Chromium verified every table cell and the absence of visible chart
controls on both screens. In three rotated fresh-context runs each, neither screen downloaded a
chart module above the fold. Scrolling to the chart loaded one chart chunk. Keyboard activation
of the last-four-days and packing-visibility controls changed the accessible pressed state and
the painted canvas without a network request.

| Observed browser payload | Woge+Chart.js | SvelteKit+Chart.js |
| --- | ---: | ---: |
| JavaScript before chart activation (decoded body bytes) | 74,724 | 98,758 |
| Incremental chart module (decoded body bytes) | 143,634 | 141,221 |
| All bodies through chart readiness (decoded body bytes) | 222,662 | 244,139 |
| Scroll to chart UI (descriptive median ms) | 61.1 | 56.3 |
| Keyboard to frame, last four days (descriptive median ms) | 8.9 | 10.1 |
| Keyboard to frame, packing visibility (descriptive median ms) | 9.1 | 11.5 |

These are loopback Chromium 151 observations on macOS arm64 with no artificial network or CPU
constraints. The local interval includes browser frame scheduling and the scroll interval
includes the automation. The three observations per side are too small to choose a latency
ordering. Application startup, CPU/heap, physical touch and assistive-technology performance
were outside the measured scope.

Woge's Kotlin route owns the native dates, table values, validation and parent updates. Its
public versioned props cross into a small JavaScript module. The Svelte prototype prerenders
the same snapshot and its component owns the lazy mount and cleanup. The test compares local
control ownership and bytes. Woge's native date form, enhanced parent replacement, keyed state,
failed import and deliberate reset are covered by the separate Chart.js recipe. A matched
SvelteKit server form and shared-rule change were outside this slice.
[Complex display guidance](../guides/data-display-and-complex-inputs.md) ·
[Widget integration study](https://github.com/christian-draeger/woge/issues/288)

Keep the chart an external integration where the visual trend matters. For an application that
mostly edits locally, evaluate the client-owned choice on its actual editing, undo and
reconciliation tasks. Validate an external grid when editing or virtualization requirements
exceed ordinary tables and URL-backed pagination. The present evidence supports no core
expansion into a SPA, offline data layer, broad component suite, content platform, ORM or identity
system.

## Evidence and remaining comparisons

The archive contains the predeclared criteria, actual source/toolchain and artifact manifest,
runner, two comparator implementations, retained failures and raw browser results. The
Woge content test used a small harness for the settings API and a study-only preference overlay.
The chart local comparison depends on the pinned #340 source and the fixed beta artifact kit.
[Reproduction and evidence](../../testing/performance/evidence/2026-11-25-product-boundaries.tar.gz)

The Next.js/JVM packaged Todo study is owned by [#277](https://github.com/christian-draeger/woge/issues/277)
and remains open. Its missing full-deployment results prevent a whole-application comparison here.
The [change-safety and participant work](choosing-woge.md#criteria-for-the-next-decision) owns
evidence about developer change effort and usability. Revisit this guide when those results or
the chosen deployment and ownership boundaries change.
