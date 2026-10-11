# Browser support and conformance

Woge starts with ordinary HTML, links and forms. JavaScript adds streamed patches, enhanced forms
and live notices; it must not be the only way to read data or submit a core form.

## Which versions are supported?

The support window is the **current and previous stable** Chrome, Edge, Firefox and Safari, plus
current Firefox ESR, Chrome Android and Safari on iOS. Evaluate “current” on the release date.
This is the existing [browser support policy](../architecture/browser-support-policy.md), not a new
minimum-version promise. Legacy browsers and embedded webviews are not tested compatibility targets.

Per-change automation uses Chromium, Firefox and WebKit from the exact Playwright version pinned in
[`package.json`](../../client/woge-fallback-client/package.json) and its lockfile (currently 1.62.1).
Install that version's default browser builds without experimental flags. Do not silently run a local
system browser or a floating Playwright version instead. Dependency upgrades update the pin and run
the same conformance suites in all three engines before merging.

These are engine tests, not a claim that Playwright WebKit is shipping Safari or Chromium is every
Chrome/Edge release. Before releasing, record exact Playwright and browser versions and smoke results
for branded desktop browsers, Firefox ESR and the named mobile browsers. Current/previous branded
versions and mobile OS integration remain release checks; CI alone does not prove them.

## What runs in CI?

The existing jobs are reused; no per-browser job matrix is required.

| Evidence | Existing job and suite |
| --- | --- |
| Replace/Append/Remove, framing, epochs, stale revisions and latest intent | `browser-contract`: `browser-tests/runtime`, `collections`, `ordering` |
| Successful controls, submitters, validation, duplicate-submit suppression and navigation | `browser-contract`: `browser-tests/actions` |
| Dirty controls, selection, focus, overlays and silent background updates | `browser-contract`: `browser-tests/state`, `accessibility` |
| Explicit fallback despite native DPU capability, under strict CSP | `browser-contract`: `browser-tests/fallback-mode` |
| HTML-only native form submission | `browser-contract`: separate `*-no-javascript` projects |
| Real deferred patches, board actions/revisions/focus and SSE from another visitor | `reference-browser-smoke`: `reference-browser-tests/enhanced` on WebFlux, MVC and Ktor |
| Complete-page navigation and native POST/303/GET, with no replay on refresh | `reference-browser-smoke`: separate `*-no-javascript` reference projects on all hosts |

All rows run in Chromium, Firefox and WebKit. The adapter TCK's `test-native-forms.mjs` additionally
checks multipart, validation and authorization in Chromium; it is not the three-engine conformance
suite. Development SSE/overlay checks and the component recipe contracts run in all three engines in the parallel `example-browser-smoke` job.

“No JavaScript” means the Playwright project sets `javaScriptEnabled: false`, not that a test happens
to avoid calling JavaScript. Native forms still validate and navigate normally. Live data remains
available by navigation/manual refresh; HTML-only pages do not promise automatic SSE notices.

## Experimental native DPU

Chrome 150+ can patch a page natively with Declarative Partial Updates (DPU): `<?start name="…">` /
`<?end>` marks a range, and a later `<template for="…">` replaces it while HTML is loading.
Woge offers an experimental, off-by-default original-document route through `deferredHtmlPage`.
It is Chrome-only and not a beta requirement.

Native DPU only works while the browser parses the original navigation response. The new typed
document streams its shell and replacements on that one response in MVC, WebFlux and Ktor.
Existing post-shell deferred fetches always remain framed. Spring's `woge.native-dpu.mode` and
Ktor's `nativeDpuMode` select `off`, `auto`, `force-native` or `force-fallback`; forced modes are
experimental test overrides and still enforce eligibility. The page Kotlin is unchanged between modes.

What the native forms mean:

- **Replace / range:** a range can contain useful loading HTML; the template replaces the whole range at once.
- **Append:** there is no native append. Each update must write the marker again, or later updates have nowhere to go. Woge does not encode append natively.
- **Not eligible:** raw HTML, scripts, event-handler attributes, `srcdoc`, `javascript:` URLs and inline styles always use the fallback, because the native parser runs active markup.

The optional `dpu.js` module records a short-lived `woge_dpu` capability cookie. Automatic mode
requires one affirmative signal; absent, expired or ambiguous signals fall back. Loading and
completed HTML are checked independently. A rejected result stays framed inside an inert HTML
envelope on the original response, decoded by the optional module through the production DOM sink.
Responses are no-store and vary on Cookie. Ordinary complete-HTML links remain available without
JavaScript. Woge never guesses support from the User-Agent.
See the [initial-document guide](native-deferred-documents.md) and
[ADR 0106](../adr/0106-stream-typed-deferred-documents-with-native-dpu.md).

## Force the fallback in a test

The ordinary production patch runtime is explicitly imported and installed; it does not choose native
DPU. The separate optional module advertises capability for the typed initial-document route, which
remains off by default under
[ADR 0015](../adr/0015-limit-native-dpu-to-initial-document-optimization.md).

For the local browser-contract fixture, navigate to `/strict?runtime=fallback` (or
`/?runtime=fallback`). Its test-only bootstrap unconditionally installs the published JS runtime as
`globalThis.forcedFallbackRuntime`. The conformance test runs with
`HTMLTemplateElement.prototype.htmlFor` both present and absent and checks actual patch application
and stale-revision rejection. Existing runtime tests also import the fallback directly, and the
reference application explicitly installs it, so none depend on the browser advertising DPU.

This selector exists only in `browser-tests/fixture-bootstrap.mjs`, served by the test server. It is
not shipped in `dist`, does not alter a server response policy, and cannot bypass CSP, authorization,
CSRF or inert-HTML validation. Do not add a production query/header security bypass for testing.

## Run and diagnose locally

From `client/woge-fallback-client`:

```sh
npm ci
npx playwright install chromium firefox webkit
npm run test:browser
npm run test:reference
```

`test:browser` builds and tests the package before using it. For reference-only runs, first run
`npm run build`; the default host is WebFlux. Set `WOGE_REFERENCE_HOST=spring-mvc` or `ktor` to repeat
against another host. To focus locally, append `-- --project=chromium --project=webkit` to the browser
command. Add `--project=chromium-no-javascript --project=webkit-no-javascript` for HTML-only coverage;
reference enhanced projects are named `chromium-enhanced` and `webkit-enhanced`.

Every Playwright configuration retains traces on failure and screenshots only on failure. Reports
and evidence are written under `playwright-report/` and `test-results/`; reference runs are separated
by host and development runs have their own directory. The existing artifact upload steps run even
when a test fails: `fallback-client-evidence`, `reference-browser-evidence` and `example-browser-evidence` include these paths.
Download the failing run's artifact, then use `npx playwright show-trace path/to/trace.zip` or
`npx playwright show-report path/to/report`. Keep the pinned tool/browser versions with bug reports.

Some local macOS Firefox launches may time out. Report that limitation rather than calling the run
three-engine evidence; Linux CI still must run Firefox. Do not hide failures with retries or remove
an engine from the gate. See [ADR 0081](../adr/0081-cross-engine-fallback-conformance.md).
