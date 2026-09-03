# Native Declarative Partial Updates evidence

Recorded on 2026-09-03 with Node.js 24, Playwright 1.62.1 and Chrome for Testing 151.0.7922.34. The executable fixtures are in [`fixtures/primitives.html`](fixtures/primitives.html) and [`tests/native-dpu.spec.js`](tests/native-dpu.spec.js).

## Status changed during M0

The issue began as an experimental-Chromium question. The platform moved while Woge was planning: [Chrome 150 release notes](https://developer.chrome.com/release-notes/150#out_of_order_streaming) list out-of-order streaming and HTML processing instructions as stable DOM/HTML features. Chrome 151 therefore applies `<template for>` without an experiment flag.

The wider script-initiated Streaming HTML API is still separate. In the tested Chrome 151 build, `streamAppendHTMLUnsafe` is absent in the normal configuration and present with `--enable-experimental-web-platform-features`.

This is still a limited-availability platform feature rather than a Woge cross-browser guarantee. Firefox and WebKit continue to use Woge's supported fallback path.

## Reproduction matrix

The suite runs every case once in normal Chromium and once with experimental web-platform features enabled:

```shell
cd spikes/native-dpu
npm ci
npx playwright install chromium
npm test
```

```text
chromium-stable:                      6 passed, 1 streaming-API case skipped
chromium-experimental-html-streaming: 7 passed
total:                               13 passed, 1 skipped
```

The CI workflow repeats the same pinned matrix on Linux. Tests use a real local HTTP response parser, not only `innerHTML` or a DOM reconstruction.

## Observed patch primitives

| Woge-relevant behavior | Native syntax | Chrome 151 observation |
| --- | --- | --- |
| Replace one marker | `<?marker name="x">` then `<template for="x">…</template>` | Template content replaces the marker; the applied template is removed |
| Replace a range | `<?start name="x">fallback<?end>` | Both instructions and the fallback range are replaced atomically when the template closes |
| Append repeatedly | Each template writes content and a new `<?marker name="x">` | Two templates append in parser/completion order and leave the final marker available |
| Nested ranges | Inner start/end inside an outer start/end | Inner patch applies while the outer range remains addressable |
| Local parent scope | Template inside an owner element targets its descendants | Applies within that parent subtree |
| Cross-parent scope | Template in a sibling owner targets a marker outside its parent | Does not apply; target stays unchanged and the template remains in the DOM as an error signal |
| Shadow tree scope | Light and declarative-shadow trees reuse the same name | Each same-name template resolves only in its own tree scope |
| Body exception | Body-level template targets a head marker | Applies to the head, matching the documented page-wide exception |

Markers are real `ProcessingInstruction` nodes (`nodeType === 7`) in supporting Chrome. A repeated append is not a separate `append` operation: the prior template must deliberately emit the next marker. If it omits that marker, the address disappears.

Names and tree/parent placement are therefore observable protocol data. Woge must generate opaque collision-resistant names and place a template in the narrowest owner scope. A body-level template has page-wide reach and is reserved for a page-owned target such as head metadata.

## Completion-order streaming

The test server writes both placeholders first, then exposes its live HTTP response to the test. The test deliberately releases the `fast` template while the earlier `slow` region remains pending, observes `Fast ready` in the DOM, verifies `Slow pending` is still visible, and only then releases the slow template and closes the response.

This demonstrates actual completion-order HTML parsing without timers or an assumption that coroutine completion follows document order. It also confirms that a template becomes visible when its closing tag is parsed, before the response completes.

## Parser and dynamic-update constraints

- A template parsed through `insertAdjacentHTML` belongs to an intermediate fragment and cannot reach an existing document marker. In the fixture the target remains pending and the inert template remains in the body.
- With the experiment flag, writing the same template through `body.streamAppendHTMLUnsafe()` uses a streaming parser attached to the existing tree and does patch the descendant.
- The stable `HTMLTemplateElement.prototype.htmlFor` reflection is a usable client-side capability signal in Chrome 151. It does not let the server know support on the first request, so Woge must not replace capability negotiation with user-agent sniffing.
- The native mechanism has no Woge page epoch, target revision, terminal completion/error frame, size ceiling or stale-intent rule. Those remain responsibilities of the framed fallback/action protocol.
- A template that cannot resolve does not produce a transport error; it stays in the DOM. Production diagnostics would have to detect/report that condition separately.

## Security observation

The most important negative fixture puts a classic script and an image `onerror` handler inside an initial-parser native patch. Chrome 151 inserts both and executes both. Native DPU is therefore not an inert/sanitizing sink and cannot directly satisfy Woge's WOGE-XSS-002 patch contract.

This does not make the platform feature inherently unsafe: parser-delivered application HTML traditionally has active semantics. It does mean Woge may use the native path only for structurally generated, context-encoded patch markup that has passed an active-content policy. An `UnsafeHtml`/raw block, script, inline handler, `srcdoc` or active URL cannot enter this channel. Content requiring an explicit executable extension remains an external module/island outside the patch.

The native path also cannot inspect a later malformed template before the HTML parser mutates earlier valid targets. Woge must preserve the same streaming partial-commit model and never claim response-wide atomicity.

## Maximum recommended support

For the MVP, native DPU is an opt-in, limited-availability encoder for initial-document completion-order patches in Chrome 150+. It is suitable only when:

1. the public Kotlin operation already maps to the transport-neutral Patch IR;
2. all target names and template placement are generated by Woge;
3. the rendered patch is structurally known to contain no active/raw content;
4. the region's complete or linked HTML-only fallback still meets the browser policy;
5. absence or failure of native support selects the normal complete-page or Woge fallback behavior.

Do not use `streamAppendHTMLUnsafe` as the MVP action/live-update sink. It is still experimental in the tested stable Chrome configuration, does not remove the need for Woge framing/ordering checks, and has active parser semantics. Action, navigation and live responses continue through the cross-browser runtime accepted by [ADR 0014](../../docs/adr/0014-small-owned-fallback-patch-runtime.md).

The adapter stays off by default until capability negotiation and native/fallback parity tests exist. It never changes the public Kotlin API and never becomes a Spring-, Ktor- or browser-specific application concept.

## Primary references

- [Chrome 150: Out of order streaming](https://developer.chrome.com/release-notes/150#out_of_order_streaming)
- [Chrome: Declarative partial updates](https://developer.chrome.com/blog/declarative-partial-updates)
- [WICG patching explainer](https://github.com/WICG/declarative-partial-updates/blob/main/patching-explainer.md)
- [WHATWG HTML pull request 11818](https://github.com/whatwg/html/pull/11818)
