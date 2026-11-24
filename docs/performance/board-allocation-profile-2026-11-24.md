# Todo board allocation profile, 2026-11-24

The current Woge board allocates about twice as many bytes per render as the matched baselines
in the frozen comparison fixture. The most consistent sampled site across WebFlux and Ktor
is HTML escaping. The following results guide analysis of this board workload.
[Archived comparison](../../spikes/todo-framework-comparison/isolated-results.md) ·
[Investigation #294](https://github.com/christian-draeger/woge/issues/294)

## Replay conditions

Built local `0.1.0-SNAPSHOT` artifacts at
[`8f12583`](https://github.com/christian-draeger/woge/commit/8f125831a2b8c51872f97e896237686ae6f1a090)
and used the checked-in, unmodified runner. The frozen result stays tied to its recorded
`ad99e56` source. The checked-in fixture has formatting and runner metadata changes since
that source. Woge now places the add form above the live board. The board loop and byte totals are
comparable across these two versions.

The app container uses the same pinned Temurin 21.0.12.1+1 JRE on Linux arm64, 2 CPUs and
768 MiB with 128–512 MiB G1 heap. The load generator runs on Apple M3 Max under OrbStack with
macOS 27.2.1, versus 27.0.1 in the archive. Desktop load and OS caches are shared. The replay
used three rotated rounds, 100 warmup requests and 300 measured requests per HTTP case, with
four closed-loop workers. The harness checked all 14,400 measured HTTP statuses and retained
all rounds. Browser and parser probes were outside this replay.

| Mode | Frozen median allocation in B/render | Current median allocation in B/render | Current samples in B/render | Board output in B/render |
| --- | ---: | ---: | --- | ---: |
| WebFlux with Woge | 224,631 | 225,286 | 224,637 / 225,591 / 225,286 | 9,251 |
| WebFlux with Thymeleaf and htmx | 112,285 | 112,285 | 112,287 / 109,311 / 112,285 | 6,325 |
| Ktor with Woge | 225,039 | 225,608 | 225,635 / 225,591 / 225,608 | 9,251 |
| Ktor with kotlinx.html and htmx | 112,856 | 114,777 | 114,777 / 114,776 / 114,777 | 6,078 |

The thread-allocation counter covers snapshot creation, board rendering and the harness's UTF-8
conversion in 2,000 iterations after 1,000 warmups. Different markup sizes remain part of the
result. Full HTTP and process measurements are retained as separate records.
[Raw replay and profile evidence](../../testing/performance/evidence/2026-11-24-board-allocation.json.gz)

## Allocation attribution

Separate runs repeated the same board endpoint on the current image with JFR
`settings=profile` and without JFR. Each process served two route-level warmup requests and
twenty measured requests after readiness. The attribution below uses weighted object-allocation
samples at the measured loop line, grouped by the first application frame above the allocation.
The 792 WebFlux and 605 Ktor samples show likely allocation sources. Weighted shares are
profiler estimates for these recordings.

| First application site | WebFlux share | Ktor share | Sampled work |
| --- | ---: | ---: | --- |
| `HtmlEscapingKt.escapeHtml` | 34.2% | 33.4% | String builders, result strings and their backing arrays |
| `MutableAttributes.putUnique` | 17.9% | 47.7% | Attribute entries and map nodes |
| `BufferedHtmlSink.write` and `content` | 18.9% | 10.4% | Byte arrays from growing the buffer and producing the HTML string |
| `BenchKt.renderMetrics` | 15.6% | 2.0% | UTF-8 conversion to count fixture output bytes |

With and without JFR, the repeated-loop allocation median was 214,376 B/render on both hosts.
The no-JFR and JFR median times were 53.58 and 54.63 µs on WebFlux and 44.64 and 48.65 µs on
Ktor. These one-process pairs expose warmup and instrumentation effects. Retain the distinct
three-round replay and repeated-loop data when evaluating changes.
[JDK 21 JFR tooling](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html)

Investigate avoiding temporary strings when an input requires no escaping or normalization.
Preserve HTML text and attribute escaping, Unicode handling, byte budgets and duplicate-attribute
checks. Before claiming an improvement, compare both host paths with current artifacts and run
the deterministic HTML, protocol and browser checks. Typed decoding, action frame serialization,
host context and browser latency still need separate attribution.
[Comparison contract](comparison-contract.md)

## Escape candidate

A shared character-to-replacement mapping now identifies the first change and produces the
escaped suffix. Strings that need no change are reused. When escaping is needed, the writer
copies the clean prefix once. The same entity and Unicode normalization rules still apply.

The before/after comparison uses the same two image builds and container conditions. Two
process pairs per host counterbalance order. Each process runs two warmup requests and twelve
measured requests to the board endpoint. All requests retain the same 9,251-byte output.
Current rendered HTML, cross-origin rejection, invalid form, native mutation and enhanced
patch responses are checked in each process.

| Host | Median B/render before | Median B/render after | Before median µs/render, two starts | After median µs/render, two starts |
| --- | ---: | ---: | --- | --- |
| WebFlux | 214,376 | 178,600 | 53.23 / 54.06 | 50.33 / 49.81 |
| Ktor | 214,376 | 178,600 | 44.24 / 43.94 | 37.58 / 38.83 |

Each process pair allocates **35,776 fewer bytes per board render**, a 16.7% reduction for the
measured loop. Timing remains descriptive. The core, WebFlux and Ktor JVM suites and the new focused tests passed. The opt-in browser
fixtures were outside this local validation. A compiled-JAR comparison produced identical text and attribute output for all
65,536 single UTF-16 code units and 4,096 seeded mixtures. The escaped tail retains valid
surrogate pairs, normalizes NUL and isolated surrogates and preserves each context's entity rules.
Bytecode inspection confirmed primitive character appends.

[Source and all eight paired runs](../../testing/performance/evidence/2026-11-24-board-escape-pairs.json.gz)

The [enhanced Todo action profile](todo-action-profile-2026-11-25.md) follows
this renderer work with the real HTTP action, frame and process measurements.
