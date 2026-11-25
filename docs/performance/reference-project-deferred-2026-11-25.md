# Deferred reference project across three hosts

This study covers the same Woge reference application packaged independently for Spring Boot
WebFlux 4.1.1 with Reactor Netty 1.3.7, Spring Boot MVC 4.1.1 with Tomcat 11.0.24, and Ktor
3.5.2 with Netty. The measured source is `7fb2f6616e4115b4fb0050f872bef669cf8c5b88`.
The study follows the comparison contract's host/application axis and the D1 deferred-completion
scenario. The measured workload is a three-task, two-activity, read-only project, with controlled
region delays of 120, 220 and 360 ms. [Comparison contract](comparison-contract.md)

Each HTTP arrival loaded a useful project shell, requested the associated patch stream, and
validated three distinct replacements, matching page epoch and revision, required content, a
valid completion frame and HTTP body termination. The ordinary complete page was validated
independently. Each block also closed a separate stream after its first frame and checked that a
new complete journey still worked. The disconnect probes measure client closure, connection
drain and recovery. Coroutine cancellation and retained heap were outside the instrumented scope.

The packages ran one at a time in a Linux arm64 Ubuntu 24.04.4 container under a two-CPU
quota and 2 GiB memory limit. All JVMs used Temurin 21.0.12.1+1, G1, a 128 MiB initial heap,
a 512 MiB maximum heap and two active processors. The driver shared those resources. The
packaged runner bound to loopback with the ordinary reference profile. The HTTP driver used
HTTP/1.1 and requested identity encoding. There was no proxy, telemetry export or added
profiling. Every host had four seconds of HTTP warmup in each rotated block.

## Scheduled traffic

The driver offered 8 and 24 journeys per second for eight seconds at each rate, three times per
host. Host order rotated across rounds, and the rate order reversed in the second round. Every
scheduled arrival had a terminal outcome, with no retries. Latency begins at the planned arrival
and includes generator queue time, the shell, streaming and result validation. The study objective
was at least 99% of scheduled journeys correctly completed within 750 ms.

| Packaged host | Planned journeys/s | Correct / offered | p50 (ms) | p95 (ms) | Round p95 range (ms) |
| --- | ---: | ---: | ---: | ---: | ---: |
| WebFlux | 8 | 192 / 192 | 377.2 | 385.6 | 377.1–389.7 |
| MVC | 8 | 192 / 192 | 380.0 | 387.7 | 380.9–389.4 |
| Ktor | 8 | 192 / 192 | 375.1 | 383.8 | 380.8–386.3 |
| WebFlux | 24 | 576 / 576 | 370.6 | 380.1 | 378.8–381.0 |
| MVC | 24 | 576 / 576 | 372.5 | 382.2 | 380.1–384.2 |
| Ktor | 24 | 576 / 576 | 370.6 | 380.3 | 379.7–381.1 |

Each table quantile is the median of three round quantiles. All 2,304 journeys met the deadline,
with zero errors, drops and timeouts. Every round dispatched all arrivals during its intended
arrival window. Through final drain the observed rates were about 7.74–7.77 and 23.02–23.07
correct journeys per second. Round p95 generator queue time reached at most 10.3 ms.
Round p50 values for the shell read were 2.6–8.5 ms, followed by the first complete patch
at 126.7–137.1 ms and the terminal result around 370–390 ms.

All hosts sent identical identity-encoded body byte counts for this fixture. The shell was
2,105 bytes, the complete patch body was 1,820 bytes including frames and metadata, and the
complete native HTML was 2,215 bytes. These counts exclude HTTP headers and transport framing.

All nine disconnect probes were followed by a correct fresh deferred journey. Each host block
had zero established incoming sockets after the final drain and no listener after the JVM
was reaped. Post-drain process RSS ranged from 252.6 to 255.7 MiB for WebFlux, 250.3 to 252.8 MiB
for MVC, and 173.0 to 180.1 MiB for Ktor. The evidence records process CPU seconds and open file
descriptors per round. These are process observations for these distributions and limits.

The median round p95 spread between hosts stayed below the predeclared 25 ms practical
difference at both loads. The 360 ms fixture delay dominates completion, and the runs cover
two short offered-load points. Saturation, longer steady state, proxy behavior, different
arrival patterns and renderer alternatives require their own measurements.

## Browser evidence

The maintained shared reference suite passed 96 tests on each packaged host across Chromium,
Firefox and WebKit. It exercised enhanced and JavaScript-disabled journeys, including completed
visible deferred content and the ordinary fallback. These 288 checks establish scoped functional
coverage.

A separate run used the maintained Chromium budget probe under 4× CPU emulation, 80 ms configured
latency, 187,500 bytes/s download and 93,750 bytes/s upload, with the HTTP cache disabled.
Three rotated host blocks each measured three navigations on a fresh packaged process.
The values below are medians of three round medians. Parentheses give the minimum and maximum
round medians.

| Packaged host | First contentful paint (ms) | First applied patch (ms) | Longest patch apply event interval (ms) |
| --- | ---: | ---: | ---: |
| WebFlux | 248 (244–256) | 1061.2 (1060.7–1063.9) | 0.40 (0.30–0.80) |
| MVC | 248 (244–252) | 1063.8 (1057.1–1080.8) | 0.20 (0.20–0.80) |
| Ktor | 248 (236–252) | 1060.8 (1059.5–1073.4) | 0.20 (0.20–0.20) |

All 27 budget samples passed. First contentful paint and first applied patch are separate browser
milestones. Comparative timing for all three visible regions, comprehensive browser asset transfer
and a second browser condition remain open. The custom navigation driver stalled during calibration,
so the study retained those attempts separately and used the maintained probe with unchanged
application artifacts. The small set of Chromium timings is descriptive.
[Maintained reference runner](../../examples/reference-application/README.md#try-the-packaged-reference)

## Evidence and scope

The first HTTP pilot exposed a media-type spacing mismatch in the driver. The corrected pilot
checked the same media type and version across the three hosts and passed 96 journeys before
the main series. The evidence bundle retains both pilot attempts, the preregistered protocol and
browser amendment, source and distribution manifests, host and tool versions, raw HTTP rounds
and controls, functional browser results, budget samples, drivers and reproduction commands.
[Evidence bundle](../../testing/performance/evidence/2026-11-25-reference-deferred.tar.gz)

This fixture measures three Woge host packages on one small workload under fixed limits.
Renderer comparisons, general capacity, slow clients, proxies, long-running and cold scenarios,
and sustained external updates remain separate questions.
[Expanded performance study](https://github.com/christian-draeger/woge/issues/293)

The [reference workload extension](reference-workloads-2026-11-25.md) adds full
visible completion, forms, a populated paginated table, peer changes, connection recovery
and separate higher open-loop arrivals on the same archived packages.
