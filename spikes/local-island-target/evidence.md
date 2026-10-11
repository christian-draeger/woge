# Local-island target evidence

- Question: [#53](https://github.com/christian-draeger/woge/issues/53), M6 local islands.
- Decision: **Kotlin/JS IR ES modules**, [ADR 0093](../../docs/adr/0093-kotlin-js-local-islands.md).
- State: Frozen; one-session, standalone investigation. No production dependency on this spike.
- Measurement: 2026-10-11T11:20:51.287Z (2026-10-11, local Europe/Berlin).

## Workload and method

The Garden notes reference form limits notes to 120 characters. `web/index.html` supplies a useful
server-rendered list, native required/maxlength input, described status hint and ordinary POST form.
One local island reads `{"maxLength":120}` from an inert JSON script, reports a character count,
and checks nonblank/length on input. It does not render a client UI tree, change submission, or use
the patch runtime. Common code uses Kotlin serialization and a coroutine before binding events;
DOM adapters differ, but the typed props and rule are identical. Length is Kotlin/JS/Wasm UTF-16
code-unit length, not grapheme count. The fixture reuses application `--surface`, `--text`, `--accent`
tokens and native label/description/status contracts; the island never replaces its CSS.

Production Kotlin/JS compilation applies DCE; esbuild bundles and minifies ES modules. Both JS
variants include their reachable stdlib, serialization, coroutines and transitive dependencies.
“Without kotlinx.browser” means no explicit browser-binding dependency; coroutines still brings
DOM API compatibility declarations transitively. Wasm uses the **optimized** production executable,
not the larger pre-optimization `.wasm`, and bundles every JS glue/builtins module into one ES module.
No Compose, DOM renderer, npm UI library or patch client is present in any row.

Initialization starts immediately before dynamic import in the already-parsed page and ends after
props decoding, coroutine dispatch, event listener binding and the first status update. It includes
fetch/parse/module evaluation and Wasm compilation/instantiation; it excludes HTML parsing, browser
launch and later keystroke latency. There are **30 fresh contexts per row** in one headless Chromium
process, sequentially grouped by target, HTTP `no-store`, uncompressed loopback responses and no CPU
or network throttling. Median averages the two middle observations. Source-map debugging runs happen
after timing, not during it.

## Versions and machine

| Item | Tested value |
| --- | --- |
| Kotlin / serialization plugin | 2.4.10, read from `gradle/libs.versions.toml` |
| kotlinx.serialization JSON | 1.11.0 |
| kotlinx.coroutines core | 1.11.0 |
| kotlinx.browser | 0.5.0 |
| Gradle wrapper | 8.14.4 |
| Launcher JDK | OpenJDK 22.0.2 |
| Node | v26.3.0 |
| Playwright | 1.62.1 |
| Headless Chromium | 151.0.7922.34 |
| esbuild | 0.28.2 |
| Machine | Darwin 27.0.0 arm64 Apple M3 Max |

The runner reuses the client's pinned npm lockfile installation. The standalone compile-sync tasks
do not execute a Node/Yarn dev server; the measurement runner uses the available Node shown above.
Additional Kotlin browser tasks may download Gradle-managed Node/Yarn inside the isolated build.
There is no new toolchain requirement for production server-only/HTML/CSS applications.

## Results

All numbers are **bytes**, not rounded kB. Compression is each downloaded file separately using
Node zlib gzip level 9 and Brotli quality 11, then summed. Runtime payload excludes the common HTML
and debugger-only maps; the next table includes the complete document download as well. No external
CSS, stdlib CDN, glue or other browser dependency is omitted.

| Runtime path | Raw | Gzip | Brotli | Median init/mount ms | Min–max ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| JS IR, raw DOM bridge | 266,412 | 80,722 | 66,782 | 10.50 | 10.10–14.20 |
| JS IR, kotlinx.browser | 266,250 | 80,667 | 66,676 | 10.40 | 10.00–11.00 |
| Wasm production + glue | 233,766 | 90,598 | 74,260 | 9.80 | 9.50–10.90 |
| Dual: feature-positive Wasm | 234,353 | 90,902 | 74,504 | 14.60 | 14.10–15.60 |
| Dual: feature-negative JS | 266,837 | 80,971 | 66,920 | 14.85 | 14.30–15.40 |
| Dual: full Wasm download fails, then JS | 500,603 | 171,569 | 141,180 | 19.80 | 19.20–20.90 |

The feature-negative run removes WebAssembly before page execution. It downloads **no Wasm artifact**.
The failure run leaves GC/EH probes positive, consumes the complete real Wasm response, then injects
a `CompileError` from `instantiateStreaming` **before main/mount**, exercising fallback with full
failed-download cost. This is not an actual incompatible branded browser. Dual mode deploys both
artifacts (500,603 raw / 171,569 gzip / 141,180 Brotli bytes including its loader), but normally downloads
only one. Failure after mounting is not handled by this experimental loader.

### Complete browser downloads, including HTML

The document includes inline styles, typed props and inline import timing bootstrap; its size differs
slightly with the entry URL. The no-JavaScript POST test adds one server note before the measured runs.
These totals cover every successful response in the measured page, including that document.

| Runtime path | HTML raw / gzip / Brotli | Total raw | Total gzip | Total Brotli |
| --- | ---: | ---: | ---: | ---: |
| JS IR, raw DOM bridge | 1,091 / 666 / 461 | 267,503 | 81,388 | 67,243 |
| JS IR, kotlinx.browser | 1,092 / 670 / 459 | 267,342 | 81,337 | 67,135 |
| Wasm production + glue | 1,086 / 665 / 459 | 234,852 | 91,263 | 74,719 |
| Dual: feature-positive Wasm | 1,086 / 667 / 458 | 235,439 | 91,569 | 74,962 |
| Dual: feature-negative JS | 1,086 / 667 / 458 | 267,923 | 81,638 | 67,378 |
| Dual: full Wasm download fails, then JS | 1,086 / 667 / 458 | 501,689 | 172,236 | 141,638 |

### Downloaded enhancement files

| File | Raw | Gzip | Brotli |
| --- | ---: | ---: | ---: |
| `/js-direct/island.mjs` | 266,412 | 80,722 | 66,782 |
| `/js-browser/island.mjs` | 266,250 | 80,667 | 66,676 |
| `/wasm/island.mjs` | 6,370 | 2,616 | 2,312 |
| `/wasm/local-island-target-wasm.wasm` | 227,396 | 87,982 | 71,948 |
| `/dual/loader.mjs` | 587 | 304 | 244 |

The JS bundles contain all reachable Kotlin libraries. The Wasm bytes contain Kotlin libraries;
`/wasm/island.mjs` includes loader, imports and string builtins. Maps exist but normal browser
execution does not fetch them. The runner captures response URLs to verify the file set rather than
counting only the entry file.

## DOM interop and library compatibility

Kotlin/JS with bindings uses familiar typed DOM objects:

```kotlin
val input = document.getElementById("note-text") as HTMLInputElement
input.addEventListener("input", { onInput(input.value) })
document.getElementById("note-counter")!!.textContent = message
```

Without bindings, the same code uses `dynamic`, string IDs and casts:

```kotlin
private val document: dynamic = js("globalThis.document")
val input = document.getElementById("note-text")
input.addEventListener("input", { _: dynamic -> onInput(input.value as String) })
```

Wasm cannot use JS `dynamic` as the DOM abstraction. This fixture explicitly crosses the boundary
with typed strings and callbacks, putting DOM work in a JavaScript function:

```kotlin
@JsFun("(onInput) => { const input = document.getElementById('note-text'); input.addEventListener('input', () => onInput(input.value)); onInput(input.value); }")
actual external fun bindInput(onInput: (String) -> Unit)
```

Typed Wasm DOM bindings could replace that bridge; this experiment does not claim none exist.
For a few events, JS bindings are simpler to inspect and refactor than JavaScript embedded in strings.
The two JS bundles differ by just 162 raw / 55 gzip / 106 Brotli bytes, slightly favoring typed bindings;
there is no meaningful binding penalty demonstrated here, nor a general guarantee of equal size.

| Concern | Kotlin/JS | Kotlin/Wasm | Dual |
| --- | --- | --- | --- |
| Serialization 1.11.0 | Compiled and decoded typed JSON props | Same successful common code | Same schema, two artifacts |
| Coroutines 1.11.0 | `Dispatchers.Default` launch reaches mounted state | Same successful common code | Must support both target variants |
| Pure Kotlin validation/state | Common sources compile | Same sources compile | Shared logic, duplicated deployment |
| Web/npm libraries | Direct JS interop and typed wrappers where supplied | Explicit supported externals/conversions; no arbitrary `dynamic` | Library selection must work on both sides or use platform adapters |
| DOM | Typed `HTMLInputElement` or raw dynamic calls | Explicit `@JsFun` string/callback bridge tested | Two DOM adapters |
| Threading | Main-browser event loop in this test | Main-browser event loop in this test | No concurrency/worker benefit demonstrated |

This proves these exact library versions and the exercised API subset, not every library operation,
reflection API, third-party npm package or JVM-only dependency. No browser-specific dependencies
belong in the shared server validation/state code planned by #55.

## Debugging and source maps

The Kotlin compiler emitted `.mjs.map` files for JS and `.wasm.map` for optimized Wasm. esbuild chains
JS maps and embeds the original Kotlin sources in `island.mjs.map`. The compiler's Wasm map has null
source content initially; the runner embeds the existing common source without changing mappings.
Its native-implementation entry remains unavailable. Shipping maps is optional and exposes source.

Chromium's DevTools protocol (`Debugger.enable`, `scriptParsed`, mapped `setBreakpoint`, `paused`)
was used, not merely filesystem presence checks:

| Target | Map visible to Chromium | Original Kotlin source | Input-triggered breakpoint |
| --- | --- | --- | --- |
| JS raw DOM | `island.mjs.map` | Embedded `Dom.kt` and `Island.kt` | Resolved and paused at `Dom.kt:11` |
| JS browser bindings | `island.mjs.map` | Embedded `Dom.kt` and `Island.kt` | Resolved and paused at `Dom.kt:13` |
| Wasm optimized | `local-island-target-wasm.wasm.map` | Embedded `Island.kt`; native source missing | Mapped `Island.kt:23` failed: “Could not resolve breakpoint” |

JS source maps therefore work for the tested production input handler, not just generated JavaScript
lines. Wasm emits nonempty mappings recognized by Chromium, but the exact optimized mapped location
we attempted did not resolve. This is a concrete debugging limitation of **this pipeline/test**, not
proof that all Kotlin/Wasm or DevTools debugging is broken. No manual graphical DevTools session,
variable-inspection parity or branded-browser debugger matrix was verified. `scripts/debug.mjs`
repeats the protocol check; the README gives manual DevTools steps.

## Browser baseline and fallback

[ADR 0007](../../docs/adr/0007-browser-support-and-progressive-enhancement.md) separates useful HTML,
supported enhancement browsers, optional islands and experimental adapters. The JS target's intended
matrix follows the enhancement tier (current/previous stable Chrome, Edge, Firefox, Safari, Firefox
ESR, current Chrome Android and Safari iOS), with ES modules and ordinary DOM APIs. This experiment
measures Chromium only; those tiers remain release-testing obligations, not results of this spike.

[Kotlin's browser configuration documentation](https://kotlinlang.org/docs/wasm-configuration.html)
(accessed 2026-10-11) gives Wasm defaults of Chrome/Chromium/Edge 119+, Firefox 120+, Safari/WebKit/iOS
18.2+. Kotlin wasmJs uses **legacy exception handling** by default, alongside Wasm GC; the new EH
proposal needs an explicit compiler option, which this build does not set. Plain WebAssembly support
is insufficient, especially in old Safari or embedded browsers. The generated glue requests string
builtins and carries JS implementations; actual compilation/instantiation remains the final check.

`web/dual.mjs` probes GC struct validation, exception tags and the JS exception API, then imports
Wasm lazily. A rejected pre-mount import can choose the JS module. Probes are not a guarantee that
all instructions/imports are supported. Users without usable modules, blocked scripts or neither
target keep the server list, native form and static validation hint. Server-side validation stays
authoritative. The no-JavaScript POST/redirect journey and both dual fallback branches passed.
This is a minimal experiment, not the cancellation/disposal/error contract for #54.

## Exact reproduction commands

From repository root (see README for missing npm/browser installation only):

```sh
# Complete build, minification, browser checks and measurements:
spikes/local-island-target/scripts/measure.sh

# Individual production compiler command used by that script:
./gradlew -p spikes/local-island-target :js-direct:jsProductionExecutableCompileSync :js-browser:jsProductionExecutableCompileSync :wasm:wasmJsProductionExecutableCompileSync --console=plain -q

# Rebundle and rerun browser evidence without invoking Gradle:
node spikes/local-island-target/scripts/measure.mjs

# Full production gate, independent of all spike builds:
WOGE_NATIVE_BROWSER_SCRIPT="$PWD/client/woge-fallback-client/scripts/test-native-forms.mjs" ./gradlew ktlintFormat check --console=plain -q > /Users/A200296237/.copilot/session-state/c1843742-abf2-4297-9824-fd4255f0cca9/files/island-full-gate.log 2>&1; echo EXIT $?
rm -f java_pid*.hprof
```

The measurement command and final full root gate both completed with **EXIT 0**.
ADR and documentation validators also passed. The measurement output contains per-run samples, map/debugger
results, machine information and file sizes in ignored `build/results.json`. Root settings still
include only production modules/examples; no new module, dependency or test points to this spike.

## Limitations and recommendation

This is one DOM-bound island on a fast desktop with warm OS caches and one browser process. Fresh
contexts avoid document HTTP caches, but are not cold browser launches or independent V8-process
runs. Sequential target ordering and process scheduling can bias tiny differences. Separate reruns
had single-target medians around 10–14 ms. Compression is computed offline; actual timings use
uncompressed localhost HTTP, not a mobile connection or CDN. Compression settings, server caching,
CSP, source-map deployment and shared-library caching will change results. No Safari/Firefox/mobile,
low-end device, large list, workers, compute-heavy kernel or actual Wasm-incompatible browser was timed.
The standalone Node POST fixture is not production security/CSRF evidence or shared JVM validation.

Choose **Kotlin/JS** for Kotlin-authored DOM islands: direct browser/library interoperability,
smaller compressed payload here, and verified source-level event debugging. Wasm's smaller raw
payload did not yield smaller compressed bytes; the approximately 0.6 ms single-run median advantage
is not meaningful evidence of a persistent startup gain. Dual adds deployment, download and fallback
complexity without a demonstrated benefit. For a counter alone, plain JS remains a better lightweight
choice unless Kotlin domain sharing pays for this approximately 67 kB Brotli baseline. Keep the patch
runtime in small JS/TS. #54–#56 build on the JS decision; a compute-heavy Wasm follow-up needs its own
workload and cross-browser evidence before expanding supported targets.
