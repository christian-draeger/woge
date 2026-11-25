# Patch frame copying on WebFlux and Ktor

The existing [rendering change](board-allocation-profile-2026-11-24.md#escape-candidate) reduced
board-loop allocation by 35,776 bytes per render on both measured hosts. The later [enhanced
action profile](todo-action-profile-2026-11-25.md) found substantial work in HTML rendering,
patch validation and frame encoding. This experiment answers the narrower question of whether
to retain a second change that removes an intermediate copy in frame assembly.
[Issue #294](https://github.com/christian-draeger/woge/issues/294)

## Work and conditions

The candidate keeps the same encoder, limits, HTML safety checks, operation observations and
flush boundaries. It collects encoded byte segments and copies each multi-segment frame directly
into its emitted array. A single-segment preamble already has its own array. The current runtime
uses a reusable intermediate `ByteArrayOutputStream` before copying each finished frame.

The baseline is `d52d5ba48487c28d98df44c3da0ae1213be9e2dc`. The tested candidate is
`bd7f9a11b7e3059092b296c7f7c9a18d778c6cac`, with only the frame assembly change. Both images use
the unchanged ten-row Todo fixture on Linux arm64 and the same Temurin 21.0.12.1+1 base, 2 CPUs,
768 MiB container limit, 128–512 MiB G1 heap. Only the selected WebFlux or Ktor process runs in
each container. The HTTP driver uses the published action/profile harness with JFR enabled for
both variants. Host and order alternate across the two pairs per host. Each process runs 240
warmup and 1,200 measured actions.

The separate local JDK 21.0.6 allocation probe alternates the two candidates across four JVM
starts. It encodes the same prepared three-patch action single-threaded, after 2,000 warmups,
with five rounds of 1,200 encodes per process. It uses `ThreadMXBean` allocated bytes rather than
RSS or sampled JFR allocation weights. Both versions produced 11,976 identical wire bytes with
the same five chunk sizes and SHA-256 in every process.

| Prepared action encoding | Current | Candidate |
| --- | ---: | ---: |
| Median allocated bytes per encode | 189,859 | 178,040 |
| Min–max allocated bytes per encode | 189,752–190,240 | 177,944–178,417 |
| Median µs per encode | 66.3 | 72.9 |

The candidate used 11,819 fewer bytes per encode (6.2%) in this loop. The measured median time
was about 10% higher. The probe operates on an already prepared action and excludes page
rendering, sockets and browser work.

## Packaged HTTP and browser observations

All 9,600 timed HTTP actions and 1,920 warmups decoded three correct replacement frames and a
valid terminal frame, advanced the real revisions, and reached the intended board state.
The native 303, literal rejected form value and foreign-origin rejection checks passed before
each process was timed. Both versions sent the same set of response body sizes, 10,627–10,650
bytes. The focused server-runtime and protocol suites also passed on the candidate.

Separate Chromium 151.0.7922.34 runs used fresh default contexts. The browser clock measures
from click capture to the observed change in both button text and status. All 80 measured
actions passed, plus 16 warmups. This measures DOM state rather than a completed paint. Host
and load driver shared one workstation. Each cell shows its two independent process rounds.

| Observation | WebFlux current | WebFlux candidate | Ktor current | Ktor candidate |
| --- | ---: | ---: | ---: | ---: |
| HTTP p95 (ms) | 2.118 / 1.655 | 1.844 / 1.759 | 1.590 / 1.521 | 1.580 / 1.639 |
| Browser median click-to-DOM outcome (ms) | 11.00 / 9.85 | 10.00 / 14.70 | 18.65 / 15.95 | 13.25 / 21.45 |
| End JVM RSS (KiB) | 319,204 / 316,668 | 322,288 / 321,288 | 282,680 / 279,228 | 279,744 / 274,052 |
| JFR weighted frame allocation estimate (bytes per action) | 115,546 / 41,203 | 29,942 / 81,427 | 36,358 / 44,147 | 67,438 / 50,547 |

JFR estimates are allocation-sample weights divided by action count. The large between-process
variation and the higher Ktor candidate estimates leave the allocation effect in the packaged
request path unsettled. HTTP, browser and RSS observations also vary by host and order.

## Decision and reproduction

Keep the current frame assembly. The one-thread allocation saving came with more encoding time,
and the packaged comparisons provided no repeatable benefit across both hosts. The earlier
board escape improvement remains the measured runtime change from this investigation. The
published protocol and API surface stay as recorded in the existing compatibility work.

The [evidence archive](../../testing/performance/evidence/2026-11-25-patch-frame-copies.tar.gz)
contains both source revisions and image IDs, the candidate patch, the exact allocation and
browser probes, all samples and weighted events. To rebuild both application images, follow the
[documented comparison packaging](todo-action-profile-2026-11-25.md#scope-and-reproduction) on
the baseline and on a disposable checkout with the archived candidate patch applied. From a
checkout containing the paired profile runner, pass both image IDs:

```sh
node testing/performance/profile-todo-actions.mjs BASELINE_IMAGE OUTPUT_DIRECTORY CANDIDATE_IMAGE
node testing/performance/summarize-todo-actions.mjs OUTPUT_DIRECTORY
node EXTRACTED_EVIDENCE/browser-probe.mjs "$PWD" BROWSER_RESULTS BASELINE_IMAGE CANDIDATE_IMAGE
```

For the allocation probe, build `allocation-probe` from the archive with the repository Gradle
wrapper and `-PwogeRepository=BASELINE_MAVEN_REPOSITORY`. Run the same probe JAR on JDK 21 against
each packaged distribution's `lib/*`, using `FrameAllocationKt` as the entry point and
`-Xms128m -Xmx512m -XX:+UseG1GC`. Broader visible latency, saturation and resource limits belong
to [#293](https://github.com/christian-draeger/woge/issues/293).
