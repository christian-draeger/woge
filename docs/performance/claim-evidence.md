# Comparison claim and evidence register

Register format v1, reviewed 2026-11-24. Workload and measurement requirements are in the
[comparison contract](comparison-contract.md). Use the wording column for product pages and release
material. Give each new record a stable ID, date, use case and scenario revision, outcome status,
tested application and comparator versions/platform, metric and uncertainty, limitations, raw
evidence, revalidation trigger and agreed wording. Update a record with a dated successor when
new evidence changes its scope or result.

## Frozen incremental Todo comparison

These records describe the comparison measured on 2026-10-20 using source commit
[`ad99e56179a0d88b36e227ee1a104881225c0f2c`](https://github.com/christian-draeger/woge/tree/ad99e56179a0d88b36e227ee1a104881225c0f2c/spikes/todo-framework-comparison).
The fixture used a local Woge `0.1.0-SNAPSHOT`, Spring Boot WebFlux 4.1.1 with Thymeleaf
3.1.5.RELEASE, and Ktor 3.5.2 with `kotlinx.html` 0.12.0. Both baselines used htmx 2.0.11 and
fixture-owned browser code. The four modes shared a distribution. Servers ran with Temurin
21.0.12.1 on Linux arm64 in 2-CPU/768 MiB containers. The three rotated rounds and Playwright
1.63.0 browser run are described in the [results](../../spikes/todo-framework-comparison/isolated-results.md).
The [raw rounds](../../spikes/todo-framework-comparison/evidence/2026-10-20-isolated.json.gz)
contain per-record versions, source hashes, timings, failures and browser data.

| ID and status | Use case, metric and result | Limits and revalidation | Supported wording |
| --- | --- | --- | --- |
| C01 verified | A shared Todo fixture compared native forms, validation, filtering and enhanced updates with two-tab SSE/refetch. The four modes together passed 180 applicable browser journeys and returned the expected status for 14,400 sampled HTTP responses. | Three rounds with 36 intended no-JavaScript live skips. Authenticated data ownership, malformed protocols and deferred completion need separate proof. Rerun the relevant scenarios when the application, renderer, host, browser runtime or protocol changes. | In the October 2026 shared Todo fixture, Woge and both htmx baselines passed the same scoped native and enhanced browser journeys. |
| C02 unfavorable for Woge | Enhanced toggle HTTP p95, median of three per-round values, was 8.010 vs 2.017 ms on WebFlux and 4.093 vs 1.108 ms on Ktor, Woge first in each pair. | 300 sampled requests per case per round at four closed-loop workers. The HTTP endpoint is distinct from visible browser completion. Rerun matched same-host rounds after action/render changes. [Profiling follow-up](https://github.com/christian-draeger/woge/issues/294) | For the measured fixture and load, Woge's enhanced toggle HTTP response had higher p95 than the htmx baseline on WebFlux and Ktor. |
| C03 unfavorable for Woge | Board-render allocation was 224,631 vs 112,285 bytes per render on WebFlux and 225,039 vs 112,856 on Ktor, Woge first in each pair. | Per-thread allocation in the board/snapshot/UTF-8 loop with 1,000 warmup and 2,000 measured renders. The loop excludes HTTP/framing. Output was 9,251 bytes per render for Woge, 6,325 for Thymeleaf and 6,078 for `kotlinx.html`. Rerun after render, sink or JVM changes. [Profiling follow-up](https://github.com/christian-draeger/woge/issues/294) | Woge allocated roughly twice as many bytes in this board-render loop as the matched baseline render paths. |
| C04 unfavorable for the measured Woge delivery | Initial script bodies were 67,105 bytes for the shipped Woge classpath modules and 54,541 bytes for htmx plus fixture code. The same 907-byte CSS was loaded in each case. | Fresh browser contexts with no compression. Rerun actual resource delivery after changes to scripts, app opt-ins, bundling or compression. | In this uncompressed initial fixture load, the Woge scripts totaled 67,105 bytes and the htmx setup totaled 54,541 bytes. |
| C05 inconclusive | The measured enhanced fill-to-visible timing had mixed p50/p95 observations. WebFlux Woge and its baseline were 191.5/246.4 and 202.8/240.1 ms. Ktor Woge and its baseline were 189.8/242.0 and 205.6/245.8 ms. | Nine samples per mode and interaction type, with automation/proxy work included and browser engines pooled. Revalidate with a powered browser plan and separately reported conditions. [Expanded browser study](https://github.com/christian-draeger/woge/issues/293) | The small browser sample is descriptive. A visible interaction latency ranking remains inconclusive. |

Rows C01–C05 share the archived raw dataset. The earlier unisolated baseline used a different JRE,
environment and workload behavior and remains a separate historical record.
[First-stage measurements](../../spikes/todo-framework-comparison/results.md)

## Shared reference deferred project

| ID and status | Use case, metric and result | Limits and revalidation | Supported wording |
| --- | --- | --- | --- |
| C09 verified, bounded | Independently packaged Woge WebFlux, MVC and Ktor each completed the same read-only deferred project journey at 8 and 24 scheduled arrivals/s. All 2,304 measured journeys were correct within 750 ms, and median round p95 values across hosts were 380.1–387.7 ms. [Study and raw evidence](reference-project-deferred-2026-11-25.md) | Linux arm64, Temurin 21.0.12.1+1, 2 CPU and 2 GiB, three eight-second blocks per host and rate. The immutable fixture includes a 360 ms regional delay. Revalidate after changing the project fixture, host packages, protocol or runtime. Capacity and renderer comparisons require separate measurements. | The same deferred Woge project completed correctly on all three tested hosts at both declared load points. |

## Two-person Woge and Hotwire comparison

| ID and status | Use case, metric and result | Limits and revalidation | Supported wording |
| --- | --- | --- | --- |
| C10 bounded, mixed | All three configurations completed the native task. The two-user filtered edit passed all Woge and permanent-form runs and two of three default Hotwire runs. A permanent form restored draft and held-control behavior on Hotwire. Woge caught up after reconnect and committed once after the injected lost response. Both Hotwire variants produced duplicate writes in that fault. [Study and raw evidence](collaborative-todo-2026-11-25.md) | Chromium, 40 ms injected loopback RTT, two subscribers and process-local transport and storage. The lost-response result covers the tested app, browser and fault together. Revalidate after changing form preservation, recovery, transport or persistent mutation handling. | The two-user fixture showed different draft and failure-recovery behavior under explicit browser and transport conditions. |

## Claims awaiting matching evidence

| ID and status | Question and current evidence boundary | Revalidation and supported wording |
| --- | --- | --- |
| C06 unmeasured | Independent deployment footprint and build speed. The [recorded build runs](../../spikes/todo-framework-comparison/evidence/2026-10-20-build.json) measured one combined classpath with warm caches. The shared distribution also bounds startup/RSS conclusions. | Require independently packaged equivalent applications and declared build/cache conditions. The archived build timings describe the common fixture. [Independent deployment study](https://github.com/christian-draeger/woge/issues/277) |
| C07 unmeasured | Arrival-rate capacity and cross-renderer deferred completion under load. The frozen runs used closed-loop load. C09 covers two offered-load points for the same Woge workload. | Require scheduled-arrival results and equivalent deferred behavior where claimed. Current evidence describes scoped closed-loop resource and timing observations. [Expanded performance study](https://github.com/christian-draeger/woge/issues/293) |
| C08 unmeasured | Comparative human or agent developer productivity on equivalent current tasks. The [beta Todo agent run](../ai-dx/results/2026-11-24-beta-todo-agent.md) is one candidate engineering run with prior project context. | Require comparable participants, tasks and versions with an unaided control and matching gates. The beta agent run shows completion of three scoped tasks in its setup. [Human control](https://github.com/christian-draeger/woge/issues/181) · [Comparative study](https://github.com/christian-draeger/woge/issues/292) |

Review all affected wording when a workload, published artifact, comparator, platform, support
policy or measurement method changes. Dated historical statements remain tied to their tested
inputs. [Decision about the frozen evidence](../adr/0115-bounded-framework-comparison-evidence.md)
