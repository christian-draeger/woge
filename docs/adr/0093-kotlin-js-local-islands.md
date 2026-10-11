# ADR 0093: Use Kotlin/JS for opt-in local islands

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#53](https://github.com/christian-draeger/woge/issues/53)
- Refines: [ADR 0007](0007-browser-support-and-progressive-enhancement.md)

## Context

An island is a small piece of genuinely local interaction mounted into useful server HTML, such as
immediate note validation. It is an escape hatch, not a second application framework or a prerequisite
for the component library. We compared one real note character counter in Kotlin/JS, Kotlin/Wasm and
Wasm-with-JS-fallback mode. [Reproducible evidence](../../spikes/local-island-target/evidence.md) includes
all downloaded artifacts, compression, initialization, DOM interop and debugger checks.

## Decision

Support **Kotlin/JS IR, production DCE/minification, ES modules** for Kotlin-authored islands.
Prefer typed `kotlinx.browser` bindings when they improve DOM code; raw web API interop is available.
Keep the patch runtime small JavaScript/TypeScript. Kotlin/Wasm and dual output are not supported
Woge targets at this stage. An application may supply an ordinary JavaScript/TypeScript ES module,
or experiment with Wasm through the same application-owned integration boundary, without changing
server components or the patch protocol. Wasm experiments own their browser matrix and HTML/JS
fallback; they are not a promise of framework support.

For this island, typed Kotlin/JS was 66,676 bytes Brotli versus 74,260 bytes for Wasm including glue.
Both mounted in roughly 10 ms on unthrottled local Chromium. Wasm offered no demonstrated startup
advantage, required more explicit DOM bridges, and the optimized Kotlin breakpoint did not resolve.
Dual mode adds a loader, two deployable targets and a failure path that can download both. This is
sufficient evidence for a DOM-bound default, not a verdict on computation-heavy Wasm workloads.

Islands must preserve the [HTML baseline and browser tiers](0007-browser-support-and-progressive-enhancement.md):

- Core forms, navigation, overlays and data-display components work without Kotlin/JS, Wasm or
  hydration. Blocked scripts, failed imports and unsupported browsers leave useful HTML; server
  validation and authorization remain authoritative.
- JavaScript islands use web-native DOM events and APIs, existing design tokens and the same semantic
  component contracts: labels, descriptions, status announcements, focus and native form behavior.
  There is no Compose/mobile-style UI tree or second styling system.
- Read versioned, typed props from inert JSON scoped to the server-rendered root; serialize safely,
  do not evaluate code, and do not share server-only types or secrets. Mount only the chosen root.
- Only applications opting into Kotlin islands need its Gradle-managed Kotlin/JS toolchain. Plain
  HTML/CSS applications need no Node toolchain. Shared domain code uses common Kotlin sources for
  JVM and JS; platform-specific DOM and server concerns stay outside those sources.
- Kotlin/JS support follows ADR 0007's supported enhancement browsers; release testing still covers
  branded desktop/mobile browsers. Wasm GC plus legacy exception handling has higher minimums
  (documented Chrome/Edge 119, Firefox 120, Safari/iOS 18.2), not merely “supports WebAssembly”.
  Feature probes and actual instantiation are needed; browser-version guessing is insufficient.

## Alternatives considered

- **Wasm-only:** rejected as the default for DOM interaction. Smaller raw bytes did not translate to
  smaller compressed downloads, simpler interop or better startup/debugging in this experiment.
- **Wasm plus Kotlin/JS fallback:** rejected as the standard distribution. It doubles build/deploy
  artifacts and can double downloads after a failed initialization without demonstrated user benefit.
- **Raw JS interop only:** unnecessary. Typed browser bindings cost no material extra bytes here.
- **JavaScript/TypeScript only:** remains a valid escape hatch; Kotlin/JS additionally permits sharing
  typed validation and state transitions with a Kotlin server.

## Consequences

There is one supported Kotlin island target and a small, web-native integration surface. Kotlin/JS
is still a substantial download for a character counter: use plain JS for trivial behavior unless
Kotlin sharing justifies the cost, and lazy-load only where genuinely needed. Maps work for tested JS
breakpoints, but production maps may disclose source and should be deployed deliberately. Other
libraries and slower/mobile browsers need workload-specific evidence. Revisit Wasm when a real
compute-heavy island shows benefits, with cross-browser and debugging evidence before acceptance.

## Follow-up

- [#54](https://github.com/christian-draeger/woge/issues/54): build lazy manifest loading around a JS
  ES-module entry with root-scoped mount and disposal. Include props schema/version and owned assets;
  failed loading leaves HTML usable. Do not auto-hydrate all components or infer a Wasm target.
  Define cancellation, repeat-mount prevention, replacement/unmount cleanup and failure reporting.
  Any future dual adapter must fall back before mount starts, never attach two sets of handlers.
- [#55](https://github.com/christian-draeger/woge/issues/55): share pure typed validation/state-machine
  code between JVM and one JS island. Keep DOM/native APIs in JS adapters and authorization on the
  server. Specify Unicode length semantics and malformed-props behavior; this spike uses UTF-16 length.
- [#56](https://github.com/christian-draeger/woge/issues/56): define minimal local signals and explicit
  optimistic-action reconciliation over existing authoritative actions. No Kotlin migration of the
  patch runtime, global client store, mandatory component hydration or Compose lifecycle.
