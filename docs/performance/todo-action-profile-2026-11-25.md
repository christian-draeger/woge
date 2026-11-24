# Todo enhanced action allocation profile, 2026-11-25

WebFlux and Ktor each ran the same real Woge enhanced Todo toggle action.
HTML rendering, patch HTML safety checks, and the remaining frame encoding
accounted for 85–94% of the weighted allocation samples in every recording.
The task focused on one action with ten rows and fresh client ordering state.
[Investigation #294](https://github.com/christian-draeger/woge/issues/294) ·
[Comparison contract](comparison-contract.md)

## Scope and reproduction

The unchanged [comparison fixture](../../spikes/todo-framework-comparison/README.md)
used Woge's local `0.1.0-SNAPSHOT` artifacts from
[`ce22eca`](https://github.com/christian-draeger/woge/commit/ce22eca7538ab816f6bcc38f80d28627719250a5),
including the [board escape improvement](board-allocation-profile-2026-11-24.md).
The source and seed stay separate from the original frozen results. The common
distribution still carries its other comparison hosts. This trial measured the
Woge WebFlux and Ktor modes only.

The driver opened the full page and read the actual page epoch and region
revisions. It sent `id=1&filter=all` over one keep-alive connection, with a
fresh request identity and a new interaction sequence every time. It used the
current Woge browser protocol decoder to validate each response after measuring
HTTP time. It checked the terminal frame, three replacements, current epoch,
contiguous revisions, stable ten-row board, toggled state, form, and status.
The revision map carried forward to the next request. Before each sample run,
the driver also required a native 303, invalid-value retention, and cross-origin
rejection. Each sequence toggled an even number of times and left the item active.

The experiment used eight independent Linux arm64 containers on OrbStack,
Temurin 21.0.12.1+1, G1, 128–512 MiB heap, two CPUs, 768 MiB container memory,
and 256 PIDs. The driver ran on the same Apple M3 Max with macOS 27.2.1 and
Node 26.3.0. Each process ran 240 warmup and 1,200 measured actions in a closed
loop. Runs alternated JFR and control with reversed pair order. All 9,600 measured
actions and 1,920 warmups passed. HTTP timing begins just before dispatch and
ends on receipt of the body, before client-side decode and assertions.

Run from the repository root with JDK 21 and resolved build dependencies.

```sh
./gradlew --offline publishScaffoldArtifacts
./gradlew -p spikes/todo-framework-comparison --offline \
  "-PwogeRepository=$PWD/build/scaffold-maven-repository" installDist
docker build --pull=false -t woge-action-profile spikes/todo-framework-comparison
node testing/performance/profile-todo-actions.mjs woge-action-profile /tmp/woge-action-profile
node testing/performance/summarize-todo-actions.mjs /tmp/woge-action-profile
```

The [raw record](../../testing/performance/evidence/2026-11-25-todo-action-profile.json.gz)
contains the source hashes, exact image ID, every warmup and timed sample, all
process readings, and the recorded allocation and GC events in the timed
windows. The extractor omits JVM process properties and application payloads.
The frozen comparison dataset and driver remain at their original revision.

## HTTP and process measurements

| Run order | Host | JFR | HTTP p50 / p95 / p99 (ms) | JVM RSS ready / warm / end (KiB) | Measured container CPU (s) |
| ---: | --- | --- | --- | --- | ---: |
| 1 | WebFlux | Off | 1.42 / 2.18 / 3.56 | 273,864 / 292,808 / 304,272 | 3.087 |
| 2 | WebFlux | On | 1.24 / 1.97 / 3.17 | 281,404 / 310,156 / 325,048 | 2.836 |
| 3 | Ktor | On | 1.08 / 1.84 / 4.20 | 202,296 / 248,616 / 278,980 | 2.822 |
| 4 | Ktor | Off | 1.01 / 1.81 / 4.58 | 192,940 / 237,748 / 259,824 | 2.795 |
| 5 | Ktor | Off | 1.06 / 1.74 / 4.82 | 206,960 / 239,004 / 262,724 | 2.971 |
| 6 | Ktor | On | 1.48 / 6.75 / 12.37 | 214,120 / 261,400 / 282,360 | 3.736 |
| 7 | WebFlux | On | 1.65 / 2.34 / 4.60 | 275,468 / 303,820 / 317,748 | 3.473 |
| 8 | WebFlux | Off | 2.18 / 6.42 / 10.45 | 271,796 / 292,120 / 308,996 | 4.361 |

The same set of response sizes, 10,627–10,650 bytes, appeared in every run.
End RSS was higher in the sampled JFR processes on both hosts. The timing
difference between controls and JFR changed direction between pairs. These
process snapshots and tail variation support reporting all runs separately.
The shared machine, closed loop, and differing allocation/JIT histories
limit latency inference. RSS covers the JVM process and its resident
runtime memory. Container CPU also includes the counter reads.

## Allocation attribution

The JFR profile attributed weighted allocation samples to recognizable
stack frames. A sample traversing the fragment validator belongs to patch
HTML safety even when it calls URL validation. Other matching frames are
classified nearest-first. Coroutine or transport allocations with no
recognizable Woge stack remain in the framework or unattributed group.
The weights give allocation-source estimates for this action loop.

| Run | Samples | HTML render | Patch HTML safety | Other framing | Identity | Typed decode inside Woge | Woge host bridge |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| WebFlux #2 | 560 | 48.7% | 20.7% | 16.0% | 1.3% | 0.4% | 1.1% |
| Ktor #3 | 520 | 35.0% | 38.2% | 20.7% | 1.3% | 0.2% | 1.0% |
| Ktor #6 | 857 | 45.0% | 19.1% | 20.9% | 1.5% | 0.3% | 3.3% |
| WebFlux #7 | 660 | 48.7% | 21.1% | 18.8% | 1.1% | 0.6% | 2.4% |

Remaining samples include HTTP/Netty, Todo application work, escaping and
unattributed code. Each profile recorded eight garbage collections during
the timed window. The artifact retains the individual stack and object
types. Rendering samples include HTML attribute storage, tag-name checks
and buffering. Safety samples include Jsoup fragment parsing and inspecting
attributes for blocked content. Other framing samples include HTML UTF-8
payload bytes, metadata bytes and chunk assembly.

## Decision

Keep the action result, region ordering, chunk ownership, byte budgets and
patch HTML validation. The parser is part of the executable-content boundary.
A narrower follow-up can compare intermediate frame copying with identical
payloads and validation. It needs a repeatable reduction on both hosts before
changing the runtime. Typed decoding and render identity show little sampled
weight here, and larger or different inputs could change that balance.

This slice leaves independent browser-visible timing, sustained load,
broader failure paths and full-application resource attribution for their
own studies. No runtime or API change follows from it.
[Broader performance study #293](https://github.com/christian-draeger/woge/issues/293)
