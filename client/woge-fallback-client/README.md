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

## Apply a response body

The production entry point is an ES module:

```js
import { createWogePatchRuntime } from "@woge/fallback-client";

const runtime = createWogePatchRuntime(document);
const response = await fetch("/projects/42/summary");

if (!response.body) throw new Error("The response has no body");
await runtime.applyPatchStream(response.body);
```

The package ships TypeScript declarations for the runtime, completion value, error types and
lifecycle-event details, so JavaScript and TypeScript IDEs can autocomplete the small public API.

Fetch/form interception is intentionally not part of this module yet. Issue
[#31](https://github.com/christian-draeger/woge/issues/31) will add that progressive-enhancement
policy without changing the decoder or DOM sink.

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

`npm run check` builds the minified ES module, runs decoder tests, runs the browser contract in
Chromium/Firefox/WebKit and reports source, minified, gzip and Brotli sizes. Browser tests attach and
print module-load/parse/evaluation and patch-application timings.

See the [browser runtime guide](../../docs/guides/browser-replace-runtime.md) and
[ADR 0025](../../docs/adr/0025-browser-replace-runtime-and-lifecycle.md) for the complete boundary.
