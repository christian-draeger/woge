# Stream deferred results in the original HTML document

This is an **experimental Chrome-only optimization**, not a beta requirement. Normal HTML, framed
deferred fetches, actions and live updates continue to work without it. Start with the
[deferred-region guide](deferred-regions.md) to declare typed regions and authorized complete-page links.

## Declare a document, not raw parser markup

Use the same region declaration with a typed initial document:

```kotlin
val result = deferredHtmlPage(
    regions = listOf(summary),
    head = {
        request.context.render.metadata(this)
        moduleScript(
            assets.url(applicationUrl("/assets/woge/dpu.js")),
            async = true,
        )
    },
    shell = {
        element("h1") { text("Project overview") }
        element("p") { text("Results appear as they become ready.") }
    },
    tail = {
        element("a", attributes = { url("href", ProjectRoute.url(completeInput)) }) {
            text("View the complete project")
        }
    },
)
```

`summary` is the same `deferredRegion(target = page.target(ProjectSummaryRegion), ...)` used by a
normal deferred page. Its loading HTML must contain an ordinary link to authorized complete HTML,
or the document must offer one elsewhere. Do not put the only useful result behind permanent loading.

The runtime opens the document, writes the head and visible shell, adds independent body-level
region wrappers and the tail, then resolves regions in completion order and closes the document.
It never waits for the data before flushing the shell. No strings containing HTML, unmatched tags,
unsafe HTML blocks or shell rewriting are needed. Use the supported framed fetch route for regions
nested in tables, selects or other parser-sensitive layouts.

Bind this `PageUseCase` through your existing `handlers.page(...)` route. Authentication,
authorization, request observation, HEAD, response budgets and disconnect cancellation stay in
the normal page handler. Do not return native templates from `handlers.deferred(...)`: that
separate fetched response always remains framed.

## Choose a mode

Spring MVC and WebFlux use the same property:

```properties
woge.native-dpu.mode=auto
```

Ktor accepts `WogeKtorHandlers(nativeDpuMode = NativeDpuMode.AUTO)`, or
`woge.native-dpu.mode = auto` when constructing handlers from `ApplicationConfig`.

| Mode | Initial-document behavior |
| --- | --- |
| `off` | Default: all replacements use framed HTML envelopes and the optional module |
| `auto` | Native only with one affirmative capability cookie and eligible region content |
| `force-native` | Experimental tests: bypass cookie negotiation, never content eligibility |
| `force-fallback` | Experimental tests: always use the framed envelope reader |

Ordinary page results and existing deferred fetch endpoints are unaffected. The same application
Kotlin runs in every mode; only the host policy changes.

## Install the optional module

The classpath `woge-fallback-client-assets` artifact includes `/assets/woge/dpu.js` and its imported
modules. Woge application build plugins include these in the hashed asset manifest; resolve the URL
through `AssetUrls` as above. Plain Spring/Ktor applications need no Node installation. For a
bundled frontend, use the npm entry `@woge/fallback-client/dpu`. Serve it early as an **async module**
so fallback replacements can run while the response is still loading, not only after parsing ends.

This entry installs itself. Do not also initialize a managed action/live runtime in the middle of
an experimental native stream: the parser can replace region wrappers. Initial documents are for
read-only deferred results; use the supported action/live routes for interactive state.

The module records `woge_dpu=1` when the browser exposes native DPU, otherwise `0`. The cookie is
host-only, Path=/, SameSite=Lax, expires after one day and is Secure on HTTPS. The first automatic
navigation usually falls back; a later navigation can select native. Missing, expired, conflicting,
duplicated or unsupported values fall back. Clearing cookies never prevents the page from working.
No User-Agent sniffing is used. A forced native test on an unsupported browser can leave loading
in place; never use it as a production compatibility switch.

## Understand per-region fallback

Native ranges carry useful loading HTML. A generated native template replaces the region wrapper
and advances its revision. Native templates are only sent on the original navigation response, not
parsed by a detached fetch or DOM fragment parser.

Loading and completed content are checked separately. Raw/unsafe HTML provenance, executable
elements, handlers, unsafe URLs, inline styles and parser-sensitive markup cannot use native DPU.
Custom elements, customized built-ins and foreign SVG/MathML contexts also stay on the framed path.
If loading is ineligible, that region gets a normal placeholder. If the completed result is rejected
later, its already visible loading remains until the normal framed decoder applies the result.
Other eligible regions can still finish natively.

Rejected results are encoded with the existing Woge patch protocol, base64 inside inert HTML
templates, followed by a readiness marker. The optional module waits for that marker, decodes the
complete frame and uses the same validated DOM sink as a fetched patch. It never executes inline
scripts, refetches the work or removes eligibility checks. HTML unsafe provenance can fall back when
the ordinary inert sink accepts its actual content; active content that the sink also rejects leaves
loading and complete-page links intact and emits `woge:dpu-error` with a safe error code.
`woge:dpu-complete` signals a validated terminal frame. Native replacement itself does not emit the
normal JavaScript patch lifecycle events or preserve dirty controls.

All these responses send `Cache-Control: no-store` and `Vary: Cookie`; do not override them at a CDN.
Total response bytes include envelopes and native templates, and patch count/byte limits apply to
both transports. Region count, concurrency and timeouts use the normal deferred policy.

## Run the real-adapter experiment

After building the client asset and installing the existing Playwright Chromium browser:

```sh
./gradlew :woge-spring-mvc:nativeDpuBrowserTest \
  :woge-spring-webflux:nativeDpuBrowserTest :woge-ktor:nativeDpuBrowserTest
```

This dedicated gate uses real original-document navigation on all three adapters. It compares
native and fallback text, accessibility and revisions, verifies cookie negotiation and late
eligibility rejection, and checks ordinary complete-HTML navigation without JavaScript. Native
conformance disables the module so fallback JavaScript cannot hide a broken parser path. It does not
join root `check` or the default browser configuration.

Details: [ADR 0106](../adr/0106-stream-typed-deferred-documents-with-native-dpu.md).
