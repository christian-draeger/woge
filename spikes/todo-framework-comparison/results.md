# First-stage measurements, 2026-10-20

Apple M3 Max, 64 GiB RAM, macOS 27.0.1 arm64, OpenJDK 21.0.6, G1, 128–512 MiB heap. No CPU/container isolation; other desktop/verification work ran concurrently. Browser probe: Linux arm64 Docker, Chromium 153.0.8010.12 / Playwright 1.63.0. Exact candidate source hashes, versions, all timings and byte entries are in [raw data](evidence/2026-10-20.json).

Three rotated rounds, each with 100 warm-up requests and 300 measured requests per case at concurrency four. The table is the median of the three per-round percentiles, in milliseconds. All 14,400 measured HTTP responses had their expected statuses; this is HTTP response time, not completed browser interaction time.

| Mode | Initial p50 / p95 | Native validation p50 / p95 | Native toggle POST p50 / p95 | Enhanced toggle p50 / p95 |
| --- | ---: | ---: | ---: | ---: |
| spring-woge | 0.765 / 1.371 | 0.871 / 1.467 | 0.696 / 0.976 | 0.897 / 1.267 |
| spring-thymeleaf | 0.641 / 1.382 | 0.591 / 1.104 | 0.391 / 0.702 | 0.453 / 0.874 |
| ktor-woge | 0.741 / 1.185 | 0.892 / 1.679 | 0.564 / 0.877 | 0.798 / 1.225 |
| ktor-html | 0.348 / 0.651 | 0.383 / 0.780 | 0.322 / 0.534 | 0.321 / 0.535 |

| Mode | Startup seconds | RSS MiB after render | Board allocation bytes/iteration | Board microseconds/iteration |
| --- | ---: | ---: | ---: | ---: |
| spring-woge | 1.117 | 461.9 | 224748 | 74.8 |
| spring-thymeleaf | 1.162 | 400.9 | 96654 | 41.9 |
| ktor-woge | 0.529 | 359.8 | 224821 | 61.4 |
| ktor-html | 0.505 | 284.0 | 114712 | 29.7 |

RSS is not retained heap. CPU time is cumulative server `ps` time in the raw record, not steady-state utilization. The raw child-CPU field also includes the Docker probe and must not be treated as server CPU. Renderer allocation includes snapshot creation and UTF-8 output; 1,000 warm-up and 2,000 measured board renders run on the current request thread. The combined fixture build took 7 seconds on its first local invocation with existing dependency caches, then 3 seconds after formatting. Separate clean framework build costs have not been measured.

Actual initial script bodies total **67,105 bytes for the shipped Woge classpath modules** versus **52,182 bytes for htmx 2.0.11**; all modes also load the same 907-byte CSS. No compression or HTTP cache is configured. This compares the actual selected delivery recipes, not minified-bundle parity. HTTP header/transfer sizes and each module are in the raw browser entries. Live fetch cancellations are retained; no script, stylesheet, HTTP or page error passed the byte probe.

The last Spring/Thymeleaf round shows substantial drift, including a 3.046 ms enhanced-toggle p95. Raw rounds are retained instead of dropping the unfavorable/outlier run. This variation and the documented [feature differences](README.md#equivalence-and-remaining-gaps) prohibit a general framework ranking.

Woge has higher allocation and response costs in several measured cases. These results give concrete investigation targets: typed form/region rendering, native mutation response preparation, patch/status/form output and optional client delivery. They do not establish the cause of any individual difference. The simpler baselines omit behavior that Woge performs; equivalence and isolated measurement come before speed/resource marketing claims.

#269 remains open for complete browser/validation/live parity, isolated resource measurements, separate builds and a rigorous interaction/load model. Existing regression budgets are unchanged.
