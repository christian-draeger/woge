# Production performance and bundle budgets

Woge's small-runtime promise is a regression contract, not a promise that every application loads
in a particular time. These MVP gates are proposed in [ADR 0083](../adr/0083-production-performance-and-bundle-budgets.md).
They are implemented now so CI can show the cost of changing them.

## What blocks a release

| Cost | Gate | Tolerated variation |
| --- | --- | --- |
| Core browser runtime, each shipped component script and CSS asset | Raw, gzip and Brotli bytes | Both fixed ceiling and recorded size +10% |
| Tailwind service-card output | Same three encodings; equivalent plain-CSS comparison | At most 2x plain CSS plus explicit reset/compiler allowance |
| Server rendering allocation | Same fixture on MVC, WebFlux and Ktor | Each adapter's median +50%, and shared 524,288-byte ceiling |
| First shell, first patch, action round-trip, patch apply | Existing Chromium browser jobs, three samples | Median below 3,000 / 4,000 / 2,000 / 200 ms respectively |
| Server throughput and Spring Boot starter footprint | Published report | Informational; no noisy throughput failure |

The byte inventory must match the committed baseline exactly. A new component family cannot silently
bring in a browser framework: every `woge-ui-*` resource script is measured and bundled into a
self-contained graph for inspection. Framework assets must have no `node_modules` inputs or external
imports. The core graph permits only its reviewed dynamic `transactions.js` import; that optional
module and its complete self-contained graph are separately measured. Application-owned opt-in islands are outside this
framework inventory and must report their own costs.

A failed gate blocks merge/release. Fix the regression, or explicitly review a policy/fixture change
with new evidence. Do not automatically update baselines in CI or raise the portable server ceiling
because one host got slower. The 50% allocation allowance is deliberately broad for JVM/JIT/sink
variation; it is not permission to skip review of smaller growth.

These are not safety limits. [ADR 0065](../adr/0065-request-owned-resource-admission.md) and
[resource budgets](../guides/resource-budgets.md) define #122's hard exhaustion policy. They remain
unchanged. Development save-to-visible feedback and its release gate belong to
[#153](https://github.com/christian-draeger/woge/issues/153), not this production suite.

## Recorded browser bytes

Recorded on 2026-10-11: macOS, Node 26.3.0, esbuild 0.28.2, Tailwind CLI 4.3.3.
Compression uses Node's gzip level 9 and default Brotli. Sizes exclude source maps, declarations,
package metadata and test tools: none of these are loaded by the browser at runtime.

| Artifact | Raw bytes | gzip bytes | Brotli bytes |
| --- | ---: | ---: | ---: |
| Minified core ES module | 35,968 | 12,146 | 10,764 |
| Unbundled core classpath modules, sum of ten separately served files | 67,105 | 20,803 | 17,542 |
| Opt-in native dialog helper, as shipped | 1,374 | 661 | 549 |
| Dialog helper if separately minified | 649 | 397 | 311 |
| Opt-in minified navigation entry | 5,167 | 2,183 | 1,927 |
| Navigation shared browser-state module, minified | 5,317 | 2,177 | 1,894 |
| Navigation and state if bundled together | 10,370 | 4,081 | 3,613 |
| Opt-in classpath navigation source | 9,176 | 2,903 | 2,485 |
| Lazy transaction module, including its complete dependency graph | 8,853 | 3,595 | 3,145 |
| Lazy classpath transaction source, reusing existing protocol module | 3,893 | 1,533 | 1,271 |
| Reference application's generated/minified plain CSS | 3,071 | 1,238 | 1,027 |
| Equivalent plain-CSS service card | 424 | 261 | 186 |
| Optional dark/high-contrast card theme | 224 | 162 | 113 |
| Tailwind service card, including compiler output and preflight | 6,006 | 2,012 | 1,718 |

Issue #215 adds managed action ordering and a declarative live-page bootstrap to the existing
client. The reviewed change costs 1,391 minified bytes (410 gzip / 383 Brotli), replacing application
JavaScript in the gradient. Measurements were updated intentionally; all fixed ceilings stay
unchanged. No module or runtime dependency was added.

The bundled core has fixed ceilings of 36,000 raw / 12,500 gzip / 11,000 Brotli bytes. The stricter
of the ceiling and baseline +10% wins. Per-file ceilings and measurements are in
[`asset-baseline.json`](../../testing/performance/asset-baseline.json), including every unbundled
module. A real server must enable compression to achieve the compressed figures; the classpath
artifact does not itself turn HTTP compression on. Summed file compression is not bundle compression.

Issue #51 adds a separate opt-in navigation entry, never imported by core. It did not change
the core bytes at that milestone.
The optional minified entry has fixed ceilings of 5,500 raw / 2,500 gzip / 2,200 Brotli bytes;
its separately shipped shared state module has 6,000 / 2,500 / 2,200-byte ceilings. The complete
self-contained navigation graph is also checked at 11,000 / 4,500 / 4,000 bytes, so shared imports
cannot hide its cost. Node-free classpath delivery adds only `navigation.js` (12,000 / 4,000 /
3,500-byte ceilings), using already measured classpath state modules. These are separate opt-in
costs, not an increase to the core ceiling or automatic runtime loading. All encodings and the
+10% drift rule are enforced by the existing inventory gate. See
[enhanced navigation](../guides/enhanced-navigation.md).

Issue #52 adds `transactions.js`, imported lazily only when the server emits an explicit atomic
unit. Its minified module and full graph each have fixed 10,000 raw / 4,500 gzip / 4,000 Brotli
ceilings; classpath source has 6,000 / 2,500 / 2,200 ceilings and reuses the already measured protocol
module. No existing ceiling changes. Compact existing diagnostics preserve stable error codes while
making room for the small core dispatch/preparation hook: core raw growth is ten bytes; compressed
growth is 208 gzip / 196 Brotli bytes. Bundler graph verification allows only this reviewed dynamic
import, never arbitrary external dependencies. See [atomic visual updates](../guides/typed-actions.md#atomic-visual-updates)
and [ADR 0092](../adr/0092-atomic-patch-transactions.md).

Disclosure, popover and live-region primitives use browser-native behavior and add no behavior
script. Dialog behavior is opt-in and separate from core. There is no shipped component-wide client
framework or default CSS theme. The optional theme here is a fixed author-owned recipe, not a claim
that Woge ships a theme catalog.

The comparison fixture styles the same service card (layout, colors, spacing, button, focus and
responsive direction) with plain CSS and literal Tailwind classes. Tailwind uses `source(none)` and
one explicit HTML source, so unrelated classes cannot inflate this fixture silently. It includes
preflight/reset and generated properties rather than subtracting them from the result. The allowed
extra bytes are 6,000 raw / 1,600 gzip / 1,400 Brotli, plus twice the equivalent plain-CSS bytes.
Both the absolute asset gate and +10% drift gate still apply. This is a scoped recipe comparison,
not a pixel-equivalence assertion for the whole reference dashboard or the Tailwind example.

## Server rendering

The unchanged HTTP TCK harnesses bind one shared fixture through each adapter: six regions, each
with a 17-row table, escaped strings and native buttons. The body is exactly 9,940 UTF-8 bytes.
There are no deferred workers in this allocation fixture, and its six region declarations remain
within the existing eight-worker default when reasoning about representative screen complexity.

After 30 warm-up requests, 50 serial loopback GETs render the fixture. HotSpot's
`ThreadMXBean.getThreadAllocatedBytes` is read on the thread executing the HTML writer before/after
its content. The median is the allocation gate; throughput measures the 50-request HTTP batch.

Recorded with Liberica HotSpot Java 21.0.6 on macOS:

| Adapter | Median rendering allocated bytes | Recorded serial requests/second |
| --- | ---: | ---: |
| Spring MVC | 249,496 | 843.4 |
| Spring WebFlux | 249,552 | 902.4 |
| Ktor | 253,584 | 541.7 |

These are separate adapter baselines, not a ranking. See the committed
[MVC](../../testing/performance/spring-mvc-baseline.json),
[WebFlux](../../testing/performance/spring-webflux-baseline.json) and
[Ktor](../../testing/performance/ktor-baseline.json) files and shared
[policy](../../testing/performance/server-policy.json).

The allocation window includes HTML writing and host sink work performed synchronously during it.
It excludes writer construction, routing, request decoding, later asynchronous transport work,
JVM startup, background threads and HTTP-client allocations. This avoids attributing unrelated
threads to a render, but is **not whole-request allocation**. Throughput includes the loopback HTTP
client and response buffering and is deliberately informational. It is not concurrent production
capacity or an isolated renderer benchmark. CI uses Temurin HotSpot Java 21; other VMs need an
explicit allocation measurement implementation, not silently skipped checks.

## Browser interaction results

Playwright 1.62.1 Chromium uses 4x CPU throttling, 80 ms emulated network latency, 187,500 download
bytes/second and 93,750 upload bytes/second (1.5 / 0.75 Mbit/s). Cache is disabled. Every gate takes
the median of three samples, while reports retain all samples. No new browser matrix job is added.

| Real reference host | First shell ms | First patch ms | Action round-trip ms | Patch DOM application ms |
| --- | ---: | ---: | ---: | ---: |
| Spring MVC | 244 | 1,037.9 | 128.9 | 0.4 |
| Spring WebFlux | 248 | 1,050.4 | 125.8 | 0.6 |
| Ktor | 244 | 1,041.3 | 124.2 | 0.5 |

First shell is first-contentful-paint of the native page, measured from navigation start. First patch
is the first `woge:after-replace` from navigation start, including shell/module transfer and real
server deferred work. Action round-trip runs a real board mutation, from form submission to the
updated task list and two animation frames. Reference patch application measures the
before/after-replace DOM lifecycle window; it excludes earlier protocol decoding/sanitization.

The bundled-client fixture additionally measures a full 102-row patch stream, including decoding,
validation, sanitization and DOM mutation, with stream/runtime creation outside the timer. Its
recorded medians are 108 ms shell / 564.8 ms first patch / 113.5 ms synthetic action / 2.1 ms full
patch apply. This synthetic HTTP server isolates client costs; it is not backend throughput evidence.
Both the full apply measurement and real-host DOM measurements must pass the shared 200 ms ceiling.

Recorded values and environment are committed in
[`browser-baseline.json`](../../testing/performance/browser-baseline.json); fresh CI reports and
Playwright attachments contain sample values and conditions. Browser baseline timings are evidence,
not a relative +10% gate. Timing uses the deliberately generous fixed ceilings instead.

CDP slowdown is a practical mid-range constraint, not physical-device certification. It does not
reproduce mobile memory pressure, radio behavior, display scheduling or thermal throttling. The
network condition includes loopback delivery; server work remains local and unthrottled. First patch
is DOM-applied, not a pixel-paint timestamp. These fixtures do not cover huge user applications,
tail latency, sustained mutation loads, production proxies or startup cold-cache behavior. Firefox
and WebKit keep their existing functional conformance gates; no equivalent CPU simulation is claimed.

## Spring Boot starter footprint, independently reported

The neutral starter's own jar is 261 bytes; Woge autoconfigure is 26,008 bytes. Its selected runtime
dependency closure plus the starter is 12,371,857 jar bytes in this build, with **no MVC/WebFlux
adapter or web stack selected by the starter**. The artifact list is recorded separately in
[`starter-baseline.json`](../../testing/performance/starter-baseline.json).

This is disk/jar dependency size, not browser transfer size, startup time or incremental overhead
in an existing Boot application. Many dependencies are already shared by a Boot app. The adapter
harnesses also start different servers, so their startup times would not be a fair starter-overhead
delta. `reportProductionOverhead` writes a fresh dependency inventory without changing the runtime.

## Reproduce and maintain

Maintainers need the locked browser measurement tools; plain HTML/CSS application consumers still
need neither Node nor Tailwind.

```shell
npm --prefix client/woge-fallback-client ci
./gradlew performanceBudgets :woge-spring-boot-starter:reportProductionOverhead
./gradlew :woge-spring-mvc:test :woge-spring-webflux:test :woge-ktor:test --tests '*AdapterTckTest'
npm --prefix client/woge-fallback-client run test:browser -- --project=chromium performance-budgets.spec.mjs
WOGE_REFERENCE_HOST=spring-webflux npm --prefix client/woge-fallback-client run test:reference -- --project=chromium-enhanced performance.enhanced.spec.mjs
```

Repeat the final command with `spring-mvc` and `ktor`. If port 8080 belongs to another application,
set `WOGE_REFERENCE_PORT` to a free local port; do not kill that application.

After reviewing an intended change, refresh asset and server measurements with:

```shell
./gradlew performanceBudgets :woge-spring-mvc:test :woge-spring-webflux:test :woge-ktor:test --tests '*AdapterTckTest' -Pwoge.budgets.update=true
```

The update flag does not raise byte/allocation ceilings. New assets require explicitly registered
ceilings first. Review generated diffs; a changed baseline is not automatic budget approval. Update
published browser/starter evidence manually after reviewing the fresh reports. The complexity-gradient
suite's committed-JSON/update-flag pattern is reused, but its complexity scores stay independent.

Normal `check` runs the server allocation gates plus starter reporting and stays Node-free. Byte budgets
run through Node in the fallback-client CI job (`npm run check`) and locally with `./gradlew performanceBudgets`.
Existing fallback and reference-browser CI jobs gate browser timings separately and upload JSON/Playwright evidence.
The server report includes measurement duration to make the added cost visible. Dependency install,
clean compilation, browser download and server startup are not included in that duration.

### Added gate cost

On this workstation, the asset build/measurement command took 0.51 seconds. During the full
native-browser-enabled `check`, all six TCK allocation measurements together took 0.60 seconds
(58–132 ms each). Thus the new measurement work adds approximately 1.1 seconds of serial work to
`check`, less wall time when adapter tests overlap. This excludes dependency installation, clean
compilation and Gradle scheduling; it is not a portable CI duration guarantee. The starter report
only walks already-resolved jar files and is incrementally up to date when they are unchanged.

Browser timings remain outside normal `check`: the isolated fallback timing test took 4.5 seconds.
Focused real-host timing runs took 10.9–18.8 seconds including server startup; the existing reference
jobs already pay that startup cost, so these tests add only their three constrained journeys.
