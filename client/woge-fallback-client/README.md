# Woge fallback client

This directory owns Woge's small browser adapter for the versioned patch protocol. It is ordinary
standards-based JavaScript: no virtual DOM, hydration graph, component state store or CSS runtime.

The server still renders useful HTML. The fallback client only enhances an already active document
by decoding a Woge patch stream and replacing the children of a registered region.

## Page contract

A complete page declares one opaque epoch in its `head` and each legal patch target declares its
opaque region ID and current revision:

```html
<meta name="woge-page-epoch" content="epoch-a">

<main
  class="project-summary"
  data-woge-region="summary-1"
  data-woge-revision="7"
  data-woge-interaction-sequence="41"
>
  <p>The server-rendered fallback remains useful without JavaScript.</p>
</main>
```

Region IDs are registry keys, never CSS selectors. `data-woge-interaction-sequence` is optional for
the initial page/deferred-work sequence and defaults to `0`.

## Register latest intent and recover one region

Before starting an enhanced request, call `runtime.beginInteraction(["summary-1"])`. Its frozen
result contains the document epoch, a new interaction sequence and each target's known base revision.
Pass these values through the request's typed input and echo them in its resulting patches.
An older search or initial deferred response cannot overwrite a newer registered intent.

Lower sequences and duplicate/lower revisions are dropped silently in the DOM, with
`WOGE_STALE_PATCH` on a finished `stale` observer event. Other current targets in that stream still
apply. A forward gap instead classifies as `refetch-region`; it is not silently skipped.

`runtime.refetchRegion(target, load, { signal })` invokes your explicit **safe GET** loader with new
intent and current revision context. Return the response byte stream containing one authoritative
Replace for that target and context. The loader must authorize normally and render current domain
data; never use it to replay a POST. Recovery validates ordinary patch HTML/state rules and cannot
overwrite newer intent. One attempt is allowed per target revision, including failed attempts, with
at most 128 budget entries. Exhaustion fails closed; unknown removed targets need an explicit,
bounded full-navigation fallback rather than a guessed selector.

This primitive does not invent an HTTP endpoint or automatically change native action forms.
The typed server refresh integration remains part of #37.

## Apply a response body

The production entry point is an ES module:

```shell
npm install @woge/fallback-client
```

```js
import {
  createWogePatchRuntime,
  WOGE_PATCH_PROTOCOL_VERSION,
} from "@woge/fallback-client";

const runtime = createWogePatchRuntime(document);
const response = await fetch("/projects/42/summary", {
  headers: {
    Accept: `application/vnd.woge.patch-stream; version=${WOGE_PATCH_PROTOCOL_VERSION}`,
  },
});

if (!response.body) throw new Error("The response has no body");
await runtime.applyPatchStream(response.body);
```

The package ships TypeScript declarations for the runtime, completion value, error types and
lifecycle-event details, so JavaScript and TypeScript IDEs can autocomplete the small public API.
It also ships a manifest containing package/protocol versions, output hashes, sizes and SRI integrity.
There are no runtime dependencies and no CSS files.

Spring Boot and Ktor projects that do not otherwise need Node can consume the
`dev.woge:woge-fallback-client-assets` JVM artifact. It exposes the same canonical modules as
classpath resources below `/assets/woge/`. The
[installation guide](../../docs/guides/fallback-client-installation.md) compares both paths and
covers static deployment, caching, source maps, CSP and SRI.

## Enhance explicitly opted-in forms

`installWogeActionForms(document, runtime)` installs delegated submission handling for forms marked
`data-woge-action`. A form also names its existing `role="status"` and `role="alert"` elements through
`data-woge-status` and `data-woge-alert`, and supplies `data-woge-failure-message`.

Only same-origin UTF-8 URL-encoded POSTs targeting the current window are eligible. Browser validation,
successful controls, repeated fields and submitter overrides are preserved. Files, multipart and
image submitters stay native. Busy state is request-owned, focus stays in place, and `dispose()`
removes the listener and cancels owned requests.

The endpoint must explicitly return a versioned patch stream or a successful `Woge-Navigate` response
requesting a same-origin GET. The client never retries or natively replays an uncertain POST.
The [installation guide](../../docs/guides/fallback-client-installation.md#opt-in-to-action-form-enhancement)
shows the HTML DSL attributes and error event.

This browser foundation does not close [#31](https://github.com/christian-draeger/woge/issues/31):
typed action patch rendering and enhanced field-error presentation remain follow-up work. All three
host action bindings already negotiate application-owned 303 redirects into GET-only navigation.

## Handle failures

Every failure maps to exactly one reaction. `classifyWogeFailure` turns an error or a non-OK
`Response` into `{ code, category, outcome }`. The outcome is one of `fail-closed`,
`error-response`, `ignore-stale`, `refetch-region`, `reload-page` or `retry-safe`.
`createWogeRecoveryBudget` makes sure recovery cannot loop: one reload per page epoch and tab,
one retry per request.

```js
import { classifyWogeFailure, createWogeRecoveryBudget } from "@woge/fallback-client";

const budget = createWogeRecoveryBudget();

try {
  const response = await fetch(url, { headers: { Accept: "application/vnd.woge.patch-stream; version=1" } });
  if (!response.ok || !response.body) throw response;
  await runtime.applyPatchStream(response.body);
} catch (problem) {
  // Only an idempotent GET without side effects may be retried.
  const failure = classifyWogeFailure(problem, { safeRequest: true });
  if (failure.outcome === "retry-safe" && budget.tryRetry(url)) {
    // fetch once more
  } else if (failure.outcome === "reload-page" && budget.tryReload(pageEpoch)) {
    location.reload();
  }
  // Otherwise keep the server-rendered content; it still works without JavaScript.
}
```

The [failure and recovery model](../../docs/architecture/failure-and-recovery.md) lists every case.

## Lifecycle events

Immediately before and after a valid replacement, the target emits bubbling
`woge:before-replace` and `woge:after-replace` `CustomEvent`s. An application can attach one listener
to `document` and delegate mount/update/dispose work from there. The target element itself remains in
place; its classes, custom-element instance and application-owned state are not rewritten.

Open descendant dialogs and popovers are closed after the before-event and before their subtree is
removed. Custom elements inside the old/new child subtree receive their normal disconnected/connected
callbacks.

## Verify locally

```shell
cd client/woge-fallback-client
npm ci
npx playwright install chromium firefox webkit
npm run check
```

`npm run check` builds the minified ES module, verifies protocol alignment, packs two byte-identical
artifacts, installs one into a fresh external consumer, runs decoder and browser tests in
Chromium/Firefox/WebKit and reports source, minified, gzip and Brotli sizes. Browser tests attach and
print module-load/parse/evaluation and patch-application timings. `npm publish` runs the same gate
before it can publish.

See the [browser runtime guide](../../docs/guides/browser-replace-runtime.md) and
[ADR 0025](../../docs/adr/0025-browser-replace-runtime-and-lifecycle.md) for the complete boundary.
