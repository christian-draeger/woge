# Reference project completion, updates and scheduled load

This study extends the [frozen deferred-project experiment](reference-project-deferred-2026-11-25.md)
for [#293](https://github.com/christian-draeger/woge/issues/293). It reuses the same three independently
packaged reference hosts at `7fb2f6616e4115b4fb0050f872bef669cf8c5b88`. Package hashes, the original
manifest, the added workload plan and raw observations accompany the report. The historical
[#269 incremental study](../../spikes/todo-framework-comparison/isolated-results.md) stays frozen.

| Workload | Equivalence and useful outcome |
| --- | --- |
| Complete project | JavaScript disabled. Same summary, three task rows and two activity items in a usable complete HTML document. |
| Shell and deferred project | Heading and complete-page button before three independent revisions. All three correctly rendered regions, the fixture rows, no loading placeholders, and a separate HTTP check of the framing and terminal. |
| Rejected form | An identical 122-UTF-16-unit draft reaches server validation, returns 400 and remains in the input. Enhanced mode focuses the summary. Neither produces a saved note. |
| Corrected mutation | The same valid note appears once in the visible list. Native returns 303 and follows its GET. Enhanced returns a patch and announces the save without navigation. |
| Paginated task table | Eighty user-visible tasks inserted through the real route and one existing task. Both modes show eight table rows, the correct page count and exact sentinels before and after the next-page link. This covers a larger paginated dataset with eight rendered rows. |
| Peer updates | Twelve successful mutations in a second tab. After each group of three, the first tab displays the exact cumulative change count and retains its focused draft. A safe refresh shows the final peer row. |

The host comparisons answer questions about a specific application and deployment. The standalone
same-host [KTE/htmx and Thymeleaf/Datastar Read](https://github.com/christian-draeger/woge/blob/735aba169496b974d4e11c2ac321d03b6ac698f1/spikes/todo-datastar/deferred-comparison-2026-11-25.md)
and [Add and peer update](https://github.com/christian-draeger/woge/blob/735aba169496b974d4e11c2ac321d03b6ac698f1/spikes/todo-datastar/comparison-2026-11-25.md) studies answer
narrow renderer/application questions. The [Next.js, KTE and Woge deployment study](../../spikes/todo-next/matched-draft-study.md)
adds a cross-runtime comparison for a form with a newer pending draft. Its separately measured
cold dependency setup, offline builds, package sizes and startup apply to those deployments.
The delayed Todo region performs one read after an explicit delay. This project renders three
separate regions and uses different JVM limits. The observations are published side by side.
The full three-host reference suite already passed 288 functional checks on Chromium, Firefox and
WebKit for these packaged artifacts. Only Chromium supplies the additional timings here.

## Fixed resources and measurement

The scheduled runs use Linux arm64, two CPUs, 2 GiB memory, Temurin 21.0.12.1+1, G1,
`-Xms128m -Xmx512m -XX:ActiveProcessorCount=2` and only the application's own loopback. Packages
run one at a time. The Python generator shares the container quota. Warmups and process startup are
recorded separately. The first useful full HTML probe starts from a new JVM with the image and
packages already present. Operating-system caches are retained. The archived reference build
log reports a 24 s combined cold-output build from the existing dependency cache on the original
host. Tool and dependency acquisition, build, process startup and steady request handling have
separate clocks and input states.

All load timings start on the scheduled arrival, include generator queueing, and end after
validating the entire streamed body. The objective is at least 99% of scheduled journeys correctly
finished within 750 ms. HTTP error, missed deadline, undispatched work and completed latencies
are reported separately. Each block warms from 24 scheduled arrivals/s for four seconds. Resource
data uses Linux process RSS and CPU plus separate post-drain JVM heap probes. JFR allocation
sampling runs separately after the timed blocks. The default packaged configuration has no
semantic exporter, and these comparisons make no claim about exporter overhead or cross-runtime
trace propagation. The existing CI budgets remain in effect.

## Scheduled arrival rates

The first ladder offered 24, 96, 192 and 320 journeys/s for five seconds each. Host order rotated
and the rate order reversed in the middle round. All 28,440 scheduled arrivals were correct and
within the objective. At 320/s the round p95 completion values ranged from 366.5 to 370.2 ms, with
round p99 from 368.1 to 383.8 ms. The p95 generator queue stayed under 3.6 ms at that rate.
The 360 ms loader accounts for most of the completed latency. This ladder establishes a lower
bound for this small fixture under the declared resource limits. A separate higher-load ladder
checks the limiting operating region.


| Packaged host | Fresh useful response, median ms | RSS after drain, median MiB | Used heap after full GC, median MiB |
| --- | ---: | ---: | ---: |
| Spring WebFlux | 1,814 | 285.2 | 16.6 |
| Spring MVC | 1,654 | 305.1 | 26.7 |
| Ktor | 535 | 229.9 | 9.9 |

The committed Java heap was 128 MiB in these probes. All nine blocks drained their inbound
connections and reaped their listeners. The separately profiled round records JFR sampled
allocation classes, weights and call stacks. The recordings do not establish that every byte was
observed or that exact per-request allocation costs can be subtracted across hosts.

### Higher offered-load control

The second ladder used four-second windows at 550, 900 and 1,200 scheduled requests/s, the same
750 ms objective and 400 maximum generator workers. It retained all 95,400 scheduled outcomes.
Each row below aggregates all three rounds. Queue p95 is the median of round p95 values. Rounds
that missed the objective still count in the denominator.

| Host | Offered/s | Correct within 750 ms / offered | Request deadline / undispatched | Generator queue p95, ms |
| --- | ---: | ---: | ---: | ---: |
| WebFlux | 550 | 6,600 / 6,600 | 0 / 0 | 33.9 |
| WebFlux | 900 | 10,800 / 10,800 | 0 / 0 | 16.7 |
| WebFlux | 1,200 | 9,915 / 14,400 | 1,939 / 161 | 352.2 |
| MVC | 550 | 4,879 / 6,600 | 0 / 0 | 252.5 |
| MVC | 900 | 3,994 / 10,800 | 0 / 0 | 616.0 |
| MVC | 1,200 | 3,661 / 14,400 | 2,068 / 947 | 960.5 |
| Ktor | 550 | 5,811 / 6,600 | 0 / 0 | 208.0 |
| Ktor | 900 | 8,271 / 10,800 | 0 / 0 | 185.4 |
| Ktor | 1,200 | 5,587 / 14,400 | 1,966 / 314 | 936.3 |

There were no additional misrendered responses or classified HTTP errors. Successful but late
responses account for the difference between the objective and the terminal-outcome counts.
At 1,200/s the driver dispatched a median of 938, 1,101 and 936 journeys/s in the window for
MVC, WebFlux and Ktor respectively. At 400 workers, a 360 ms operation alone implies a
theoretical dispatch ceiling of about 1,111 arrivals/s before any overhead. Substantial queueing,
shared CPU pressure and variation across rotations prevent attribution of an independent
server capacity. Under the given local limits WebFlux passed the objective at both 550 and 900/s
in each round. The other hosts had objective misses at these offered rates. Neither ladder
establishes sustained capacity, safe production concurrency or a general host ranking.

## Chromium outcomes and bytes

Three rotations per host used unthrottled Chromium with browser cache disabled, emulated
80 ms added latency with 187,500 bytes/s down and 93,750 bytes/s up plus 4× CPU under a disabled
cache, and the same throttling after priming the context. These are Chromium conditions on a
development workstation. Earlier verification provides the cross-engine functional result.

All 189 declared Chromium workload/mode/condition cases passed. The supplementary 27
shell measurements all saw the visible heading and full-page button before three correct
regions, with no loading placeholders remaining. The supplementary runs corrected the
initial observer's enhanced fallback selector. The archive retains the calibration errors and the
initial driver, both corrected workload drivers, and all successful observations.

| Condition | WebFlux shell / complete, ms | MVC shell / complete, ms | Ktor shell / complete, ms |
| --- | ---: | ---: | ---: |
| Unthrottled, cache disabled | 49 / 443 | 26 / 429 | 88 / 479 |
| Constrained, cache disabled | 241 / 1,313 | 244 / 1,313 | 239 / 1,309 |
| Constrained, primed context | 105 / 490 | 111 / 488 | 239 / 1,312 |

Cells are medians of three per-host measurements. The complete native document was already
observably useful at roughly 273–286 ms in the constrained cold runs. That project fixture
produces the same rows without the explicit deferred-work delays via its complete route.
The enhanced shell provides earlier separate interaction and an eventual three-region result.
Both routes and the initial shell have distinct latency and throughput semantics.

Under constrained, cache-disabled Chromium, host median rejected and corrected note outcomes
landed at 168–184 ms and 148–183 ms respectively for enhanced forms. Native form outcomes
landed at 198–212 ms and 297–298 ms with the native redirect. These durations include external
browser checks. The server retained an invalid draft without adding a row. Every correct
mutation added a single row, with enhanced mode emitting the tested status. In the populated
table case both modes exposed a usable next page. The connected observer kept its original
draft and focus during all twelve peer changes. The fourth group's notification was observed
about 229–256 ms after the last peer click under cold constrained Chromium.

The cold enhanced project delivered 2,105 HTML bytes, 4,016 CSS bytes, 67,105 Woge browser
module bytes, 3,193 application module bytes, 1,374 Woge UI dialog bytes, plus the 1,820-byte
patch body including metadata, HTML and framing. The native document body was 2,215 bytes
and the native route loaded no enhancement modules. Resources in this project probe had
equal encoded and decoded body sizes. Resource Timing records transfer separately, including
overhead and replay from cache. With a primed browser the Spring packages transferred zero
CSS/application/Woge JavaScript bytes. Ktor transferred the same resources again in this setup,
so the cache behavior accounts for an observable package difference before attributing it to
rendering. The archive lists each script, resource and condition independently.

CDP records document parse-to-interactive, renderer task, script, layout and style intervals
and Woge's before/after replace events. Cache-disabled constrained project observations had
median renderer task duration of 102–142 ms and script duration of 7–13 ms. The parse boundary
uses the navigation's `domInteractive` value, which also includes time waiting for delivery.
Some counter deltas across warmed navigations were negative after the renderer reset its
counters. Their raw values are retained and do not enter a warm CPU comparison. Browser
interface latency, renderer task duration and network time have separate boundaries.

## Transport and recovery controls

A separate study on the same three packages observed 45 valid deferred streams across
direct loopback, actual NGINX with buffering off and on, an explicit relay holding the entire
body to EOF and a paced direct reader. Nine fresh/native recovery pairs passed, and every
connection and listener drained. NGINX with buffering on still delivered the first complete
frame at a median of 131 ms and the terminal frame at 371 ms. The EOF hold delivered both at
370 ms. Reading at most 128 bytes every 25 ms produced correct frames but shifted terminal
parse to a median of 638 ms. These 1,820-byte bodies are too small to show sustained socket
backpressure. Earlier deferred disconnect controls verified first-frame closure and recovery,
and the standalone deferred Todo comparison also measured cancellation of its worker.

The separate raw SSE burst control committed 57 native mutations across the three hosts.
Each observer received twelve invalidations during the connected burst, then recovered six
offline changes plus prior state using a genuine last-event ID, resync and safe current GET.
The run retained exact final state and no remaining sockets. It did not observe coalescing.
The browser check owns focused draft preservation independently. Multi-node durability and
reconnect belong to the [PetClinic evidence](../../examples/petclinic-woge/README.md)
and [#289](https://github.com/christian-draeger/woge/issues/289).

## Reproduce and retain

The [workload archive](../../testing/performance/evidence/2026-11-25-reference-workloads.tar.gz)
contains exact source/package identities, both declared load ladders, browser plans and drivers,
cold and steady process records, profiled samples, retained unsuccessful calibration, and raw
latency, status, connection and resource observations. Its `reproduce.md` gives the commands and
cache and profiling boundaries. The [transport archive](../../testing/performance/evidence/2026-11-25-reference-transport-controls.tar.gz)
contains the effective proxy configuration, independent artifact verification and raw streams
and reconnect observations. The ordinary CI regressions retain their existing budgets.
