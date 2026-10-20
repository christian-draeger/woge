# Isolated equivalent-journey measurements, 2026-10-20

The four fixtures now implement the shared native/filter/validation and enhanced list/count/status/form journeys, including two-tab SSE/refetch with preserved draft and focus. The exact same maintained Todo tests run against all four; only the document-identity assertion uses `performance.timeOrigin` so it applies to both Woge and htmx. All **180 applicable browser journeys passed**, with 36 intentional no-JavaScript live skips over three rounds. All **14,400 sampled HTTP responses** had their expected status. Published skrape parser checks passed on all four modes in every round. No retries or discarded measurement rounds.

Each application runs with **2 CPUs, 768 MiB RAM, 128–512 MiB G1 heap**, verified against the real Docker HostConfig. The server JRE is **Temurin 21.0.12.1+1** on Linux arm64; build/host-probe JDK is 21.0.6. Host: Apple M3 Max, 64 GiB, macOS 27.0.1 / OrbStack. Other desktop work is not isolated. All modes use the same distribution/classpath; these figures are incremental application comparisons, not minimal packaging comparisons.

Measured source is [ad99e56](https://github.com/christian-draeger/woge/tree/ad99e56179a0d88b36e227ee1a104881225c0f2c/spikes/todo-framework-comparison). Every recorded Kotlin/template/client source SHA-256 was verified against that commit. Subsequent Kotlin formatting does not change the recorded image. Image ID, raw latencies, per-resource bytes, source hashes, browser summaries and interaction samples are in [compressed raw evidence](evidence/2026-10-20-isolated.json.gz). Inspect with `gzip -dc spikes/todo-framework-comparison/evidence/2026-10-20-isolated.json.gz`.

The table shows the median of three per-round percentiles, milliseconds. Warm-up is 100 requests per case; measurement is 300 at concurrency four with keep-alive and closed-loop load. Native toggle stops at 303; validation is an ordinary HTTP 400. Enhanced toggle applies the same three visible outputs, while Woge additionally carries typed identities/ordering/recovery.

| Mode | Initial p50 / p95 | Native validation p50 / p95 | Native toggle POST p50 / p95 | Enhanced toggle p50 / p95 |
| --- | ---: | ---: | ---: | ---: |
| spring-woge | 1.271 / 4.576 | 1.594 / 6.317 | 1.258 / 6.648 | 1.813 / 8.010 |
| spring-thymeleaf | 1.338 / 3.002 | 1.150 / 2.764 | 0.657 / 1.625 | 0.844 / 2.017 |
| ktor-woge | 1.259 / 3.780 | 1.573 / 4.796 | 1.268 / 4.900 | 1.780 / 4.093 |
| ktor-html | 0.589 / 1.646 | 0.727 / 3.029 | 0.563 / 1.694 | 0.598 / 1.108 |

| Mode | Startup seconds | JVM RSS MiB | Board allocation bytes/iteration | Board microseconds/iteration |
| --- | ---: | ---: | ---: | ---: |
| spring-woge | 1.192 | 404.4 | 224631 | 68.5 |
| spring-thymeleaf | 1.224 | 302.2 | 112285 | 66.1 |
| ktor-woge | 0.576 | 336.2 | 225039 | 65.6 |
| ktor-html | 0.572 | 245.7 | 112856 | 26.1 |

The render loop includes board/snapshot/UTF-8 work but excludes HTTP/framing, and uses per-thread allocations (1,000 warm-up + 2,000 measured renders). RSS is not retained heap. Cgroup CPU in the raw record is cumulative container CPU before browser journeys, including its tiny `/proc` probes; it is not steady-state utilization. Throughput, response bytes and each round remain in the raw record. The load is closed-loop without coordinated-omission correction, so these RPS values are not production capacity.

| Mode | Enhanced fill+submit+visible p50 / p95 (ms) | Native fill+submit+visible p50 / p95 (ms) | Initial script bodies (bytes) |
| --- | ---: | ---: | ---: |
| spring-woge | 191.5 / 246.4 | 98.5 / 303.1 | 67105 |
| spring-thymeleaf | 202.8 / 240.1 | 105.4 / 280.8 | 54541 |
| ktor-woge | 189.8 / 242.0 | 107.8 / 288.3 | 67105 |
| ktor-html | 205.6 / 245.8 | 101.4 / 374.6 | 54541 |

Browser interaction timing includes field filling, automation/proxy overhead and waiting for the new escaped row to be visible. Nine enhanced and nine native samples per mode combine the three engines and three rounds; this is a small descriptive sample, not a stable latency ranking. Script bytes are actual initial no-compression/no-cache bodies: Woge classpath modules versus htmx plus the fixture-owned live/validation adapter. Each mode also loads the same 907-byte CSS. HTTP warm-up/caches and fresh-browser caches are separate. Browser traffic uses a streaming loopback proxy so all engines receive a trustworthy local origin.

Woge still pays for typed decoding, multiple region outputs and managed ordering/recovery, while htmx needs explicit fixture code for live reads, request serialization and validation focus. The shared outcomes are now verified, but the fixtures do not establish identical malformed-input/security/protocol or deferred-stream behavior. Readable claims remain limited to this dataset and measured setup; there is no cross-ecosystem winner.

## Build and cache boundaries

Three common-fixture clean-output builds took **8.454, 2.513 and 2.073 seconds**; the immediately
following unchanged incremental invocations took **0.640, 0.598 and 0.607 seconds**. All six completed
successfully. These are wall times for `./gradlew -p spikes/todo-framework-comparison
-PwogeRepository=PATH clean installDist` and then `installDist`, with JDK 21.0.6, warm dependency/
Gradle caches and no OS-cache reset. Retain the first slower round rather than dropping daemon/JIT
variation. [Raw build evidence](evidence/2026-10-20-build.json) records every sample and the exact
source revision. The combined distribution has 110 JARs / 51,793,056 JAR bytes, including all four
modes. Neither these build times nor that footprint compare independent minimal framework apps.

The first-stage [unisolated results](results.md) remain historical evidence with their original
simpler baseline and JDK; do not mix their numbers with this table. [ADR 0115](../../docs/adr/0115-bounded-framework-comparison-evidence.md)
completes this bounded incremental JVM question. Independent clean builds/minimal distributions and
a Next.js deployment comparison are scoped separately in [#277](https://github.com/christian-draeger/woge/issues/277).
Cold OS caches, arrival-rate saturation, interaction tails at scale and controlled deferred workloads
are unmeasured extensions, not claims made by this dataset. Existing deterministic budgets are unchanged.
