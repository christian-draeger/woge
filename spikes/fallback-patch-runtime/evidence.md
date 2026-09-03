# Cross-browser fallback patch runtime evidence

Recorded on 2026-09-03 with Node.js 24, Playwright 1.62.1 and the version-1 framing from [ADR 0013](../../docs/adr/0013-length-prefixed-patch-framing.md).

## Question and boundary

This spike asks whether Woge can apply an action/deferred-response patch when a browser has no native Declarative Partial Updates (DPU) implementation. It does not choose the native initial-document streaming shape; that remains [#3](https://github.com/christian-draeger/woge/issues/3).

The prototype is intentionally one small classic script. It consumes a normal Fetch `ReadableStream<Uint8Array>`, incrementally decodes Woge frames, validates patch metadata and replaces the children of a registered region through an inert `<template>`. The page remains ordinary server-rendered HTML:

```html
<meta name="woge-page-epoch" content="epoch-a">
<main data-woge-region="summary-1" data-woge-revision="0">
  <p>Original content still works without enhancement.</p>
</main>
```

No application component is hydrated and the runtime does not interpret component state, CSS classes or custom elements.

## Executable result

Playwright ran the same six scenarios against its pinned engines:

| Engine | Installed test build | Result |
| --- | --- | --- |
| Chromium | Chrome for Testing 151.0.7922.34 | 6 passed |
| Firefox | Firefox 153.0 | 6 passed |
| WebKit | WebKit 26.5 | 6 passed |

The 18 passing cases prove:

- a `replace` patch works when the complete stream is delivered as individual one-byte chunks;
- every representative split before, inside and after the preamble/header/content regions produces the same DOM;
- scripts, inline `on*` handlers, `srcdoc`, blocked active elements and active `javascript:`, `vbscript:` or `data:` URLs are rejected before replacement, including scripts nested in a template;
- an unknown target, stale revision, duplicate region, remote error and truncated frame fail with stable error codes;
- wrong preambles, oversized metadata, non-ASCII content types, malformed JSON and bytes after a terminal frame fail without changing the tested region.

Run the evidence with:

```shell
cd spikes/fallback-patch-runtime
npm ci
npx playwright install chromium firefox webkit
npm test
npm run measure
```

The CI workflow repeats the matrix on Linux for every change to the spike.

## Chunk and mutation semantics

The decoder retains incomplete preamble, header and body bytes across reads. It uses declared byte lengths, never a network read boundary, delimiter or decoded character count. A complete frame is parsed and semantically validated before its payload reaches the DOM.

Previously accepted valid frames may already be visible if a later frame or the transport fails. This is intentional streaming behavior, not transactionality across the response. The terminal completion/error frame tells Woge whether the stream ended cleanly; a missing terminal is truncation.

Version 1 resolves an opaque `data-woge-region` value through a page-local registry. Patch metadata must match the document epoch and exactly advance the target revision. Metadata never becomes a CSS selector. The M0 prototype supports child replacement only; append, preservation, focus restoration and live-update scheduling remain later protocol/runtime work.

## Security boundary

Parsing through a detached `<template>` prevents parser-time execution. The prototype additionally rejects the executable elements, attributes and URL schemes named by [WOGE-XSS-002](../../docs/security/threat-model.md). This is defense in depth, not a general HTML sanitizer. Woge's primary guarantee remains context-aware server rendering: ordinary application strings are escaped and explicit trusted/raw HTML is auditable.

The runtime needs no `eval`, dynamic code construction, inline event handler or application-supplied selector. A production build can load as a same-origin external script under a strict Content Security Policy. Trusted Types integration and the complete hostile-markup corpus remain [#42](https://github.com/christian-draeger/woge/issues/42).

## Runtime size

The measurement script reads exactly `runtime.js` and applies Node's level-9 gzip and default Brotli compressors:

```text
runtime_source_bytes=8050
runtime_gzip_bytes=2706
runtime_brotli_bytes=2284
```

These are reproducible prototype source-transfer measurements, not a minified production budget. The runtime includes frame decoding, limits, metadata/identity validation, active-content rejection, DOM replacement and stable error types.

## Existing alternatives

### `template-for-polyfill` 0.1.0

The Chrome team publishes [`template-for-polyfill`](https://github.com/GoogleChromeLabs/template-for-polyfill) for the emerging DPU `<template for>` syntax. Its npm browser distribution is 2,604 bytes before transfer compression. This is attractive for native-syntax parity and should be exercised by the separate DPU spike.

It is not a substitute for this fallback path. The Chrome documentation states that the polyfill buffers rather than streams, and its documented MutationObserver/parser limitations differ from native parsing. It also does not decode Woge's framed action responses, enforce Woge page epochs/revisions, distinguish completion from truncation or apply Woge's protocol limits. Adopting it as the only runtime would either discard those contracts or require a second Woge layer anyway.

### htmx 2.0.10

[`htmx`](https://htmx.org/docs/) is a mature dependency-free browser library with request triggers, target selectors, swap styles, response headers, history, events and out-of-band swaps. It remains a useful interoperability target and reference for web-native ergonomics.

Using it as Woge core would introduce a second public request/target/synchronization model. Its flexible CSS-selector and response conventions do not directly encode Woge's typed region, epoch, revision, binary framing or terminal-error semantics. An optional htmx adapter can map a deliberately smaller compatible subset later without making every Woge application depend on htmx.

## Compatibility constraints and production gaps

- The fallback targets the browser policy in [ADR 0007](../../docs/adr/0007-browser-support-and-progressive-enhancement.md), not legacy browsers. It requires standard modules or classic scripts plus `ReadableStream`, typed arrays, `DataView`, fatal UTF-8 `TextDecoder`, template fragments, private class fields and `replaceChildren`.
- Playwright WebKit provides repeatable engine coverage; it is not a substitute for release-candidate Safari/iOS device testing before a public release.
- Each HTML payload is buffered up to the reviewed 8 MiB frame ceiling before one atomic replacement. Streaming occurs between frames, not inside one fragment.
- The clear prototype concatenates byte arrays and is intentionally inefficient under one-byte input. Production uses a bounded cursor/ring buffer and backpressure.
- The registry is captured for one response. Dynamic nested-region registration, cancellation, append/preserve operations, focus/selection behavior and diagnostics require their named M1 issues and tests.
- Framing survives arbitrary post-content-decoding chunks, but proxy buffering can still delay delivery; [#45](https://github.com/christian-draeger/woge/issues/45) owns that operational evidence.

## Recommendation

Own a small protocol-specific Woge fallback runtime with no mandatory general frontend framework. Keep the native DPU implementation behind the same patch-application boundary and use feature detection to select it only after its semantics are proven. Treat `template-for-polyfill` as native-syntax experiment/compatibility input and htmx as an optional integration, not as replacements for the Woge protocol contract.

The checked-in code is evidence, not production runtime source. M1 should port its observable cases into the production browser module and golden JVM/browser protocol fixtures before publishing an API.
