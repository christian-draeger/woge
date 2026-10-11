# Local-island target comparison

**State: Frozen.** Decision: [ADR 0093](../../docs/adr/0093-kotlin-js-local-islands.md).
This standalone investigation answers issue [#53](https://github.com/christian-draeger/woge/issues/53).
It is not a supported island implementation and is deliberately absent from root settings and CI.
Time box: one working session; one DOM-bound island, not a rendering-framework benchmark.

The reference application's Garden notes form has a 120-character limit. This fixture renders a
useful list, label, native required/maxlength input and POST form before JavaScript. The island adds
an accessible character count and nonblank/length feedback. All targets decode the same typed JSON
props, run the same pure Kotlin rule and coroutine, and bind the same DOM input event. The small
Node fixture server accepts ordinary POSTs and validates independently; it is not the production
server or the shared JVM validation implementation planned in #55.

## Reproduce

From the repository root, with its JDK/Gradle wrapper and Node available:

```sh
spikes/local-island-target/scripts/measure.sh
```

The script reuses the **pinned** Playwright and esbuild installation in
`client/woge-fallback-client/node_modules`. If absent, install only that client:

```sh
(cd client/woge-fallback-client && npm ci && npx playwright install chromium)
```

`RUNS=50 spikes/local-island-target/scripts/measure.sh` changes the sample count (default 30).
Kotlin, serialization and coroutines versions come from the root version catalog, not a second
version declaration. The spike has its own Gradle settings and output; the root build does not
include or depend on it. Gradle-managed Node/Yarn, if required by additional Kotlin browser tasks,
remain a spike toolchain detail, never a requirement for plain HTML/CSS applications.

The script produces ignored `build/site/` artifacts and `build/results.json`, prints complete
per-file payloads, measures fresh browser contexts, and verifies input events, validation,
no-JavaScript POSTs, feature-negative dual fallback and pre-mount Wasm-failure fallback. It also
checks maps and mapped Kotlin breakpoints through Chromium's DevTools protocol. Servers and
browsers close on completion. See [evidence](evidence.md) for results and limitations.

## Inspect interop and maps

- `shared/Island.kt`: typed props, JSON decoding and common validation/dispatch.
- `js-direct/src/jsMain/kotlin/Dom.kt`: raw JavaScript DOM interop without explicit kotlinx.browser.
- `js-browser/src/jsMain/kotlin/Dom.kt`: typed kotlinx.browser DOM interop.
- `wasm/src/wasmJsMain/kotlin/Dom.kt`: Wasm-to-JavaScript string/callback boundary.
- `web/dual.mjs`: GC/EH probes and lazy imports; an experiment, not a lifecycle API.

For manual DevTools inspection after measurement, serve `build/site` with any static server using
`application/wasm` for `.wasm` files. Serve a copy of `web/index.html` with
`<script type="module" src="/js-browser/island.mjs"></script>` appended. Enable JavaScript source
maps in DevTools, open `Dom.kt` under Sources, and set a breakpoint in `showStatus` before typing.
Own Kotlin sources are embedded in the bundled maps. Wasm also emits a `.wasm.map`, but our exact
optimized mapped breakpoint did **not** resolve; do not treat map existence as debugging parity.
