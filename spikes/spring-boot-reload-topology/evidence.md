# Spring Boot reload topology evidence

## Conclusion

Select a hybrid topology:

- Woge owns the long-lived development session, builds, child process and structured events.
- Spring DevTools plus a successful-build trigger file is the normal Spring Kotlin/generated-source
  restart path.
- Complete Woge-managed child replacement is the correctness fallback.
- A Woge-owned SSE endpoint synchronizes browsers.
- Do not add a stable reverse proxy in M1.

This conclusion is accepted in
[ADR 0041](../../docs/adr/0041-orchestrator-owned-spring-reload-and-sse-channel.md). Production code
belongs to issues #147, #144 and #145; nothing in this spike is a supported runtime.

## Tested system

- Date: 2026-10-09
- Host: Darwin arm64
- Application: canonical external Spring Boot WebFlux scaffold
- Woge: `0.1.0-SNAPSHOT`
- Kotlin: 2.4.10
- Spring Boot: 4.1.1
- Gradle wrapper: 8.14.4
- Scaffold toolchain: JDK 21, JVM target 17
- Measurement launcher: OpenJDK 22.0.2
- Browser harness: Chromium from Playwright 1.62.1, Node.js 26.3.0

The app is materialized from `scaffolds/spring-boot`; test-only files add a plain-text readiness probe,
Spring DevTools and two generated-source shapes. The generated edit rewrites representative generated
output (`object` to class with companion), not a real KSP processor invocation. This is sufficient to
exercise structural class replacement; KSP task wiring remains build-adapter work in #147.

## Measurements

Five warm local samples were recorded per scenario. `detection` is the observed fixture filesystem
change, `compile` is the Gradle task, `ready` is compile completion to observable application or asset
state, and `total` spans the whole edit-to-ready path. These are engineering baselines, not public
performance guarantees.

| Topology | Change | Total P50 | Total P95 | Ready P50 | Ready P95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| DevTools trigger | Kotlin | 2,497 ms | 2,804 ms | 1,873 ms | 2,015 ms |
| DevTools trigger | generated structure | 2,498 ms | 2,530 ms | 1,901 ms | 1,925 ms |
| DevTools trigger | CSS | 527 ms | 544 ms | 33 ms | 60 ms |
| managed child replacement | Kotlin | 4,004 ms | 4,248 ms | 3,401 ms | 3,427 ms |
| managed child replacement | generated structure | 3,957 ms | 4,019 ms | 3,311 ms | 3,352 ms |
| managed child replacement | CSS | 507 ms | 522 ms | 31 ms | 65 ms |

The DevTools process ID remained constant across Kotlin and structural generated edits. Complete child
replacement produced a new process ID each time. CSS became visible from the development classpath
without changing either process.

Five real two-tab browser samples measured SSE event publication through visible document state at
16 ms P50 and 21 ms P95. The values exclude compilation and server readiness, which are measured
separately above.

## Reliability scenarios

| Scenario | Result |
| --- | --- |
| intentionally broken Kotlin | build failed; no trigger/restart occurred; previous probe stayed reachable |
| structural generated source | both restart topologies served the new shape |
| rapid successive generations | both tabs converged on the newest generation |
| stale/out-of-order generation | both tabs ignored the older event |
| multiple tabs | two independent `EventSource` clients refreshed correctly |
| SSE disconnect | clients reconnected and sent a non-empty `Last-Event-ID` |
| CSS-only change | stylesheet changed without navigation or process replacement |
| graceful child shutdown | Spring shutdown completed and the same port was reusable |
| occupied loopback port | application/channel startup failed rather than choosing a hidden port |

A failed compile preserves a reachable last valid application for both topologies. During a
successful *complete* child replacement, the last valid server stops before the new server can bind
the same port. The local gap measured roughly 3.3 seconds after compilation. A stable reverse proxy
could remove that gap, but ordinary Spring edits use the same-process DevTools path and do not need
it. The first implementation should therefore expose the fallback transition honestly instead of
adding a proxy preemptively.

## Transport comparison

| Transport | Fit for lifecycle events | Decision |
| --- | --- | --- |
| Server-Sent Events | native browser API, one-way HTTP stream, automatic reconnect and event ID | select |
| WebSocket | useful for bidirectional realtime, but unnecessary for downstream build state | reject for M1 |
| long polling | simple HTTP but repeated requests and avoidable latency | reject |
| fetch response stream | flexible, but Woge would own reconnect and replay behavior | reject |
| Spring LiveReload | deprecated in Spring Boot 4.1 and lacks Woge lifecycle semantics | reject |

Commands remain separate from the event stream. This keeps future authorization explicit and avoids
turning the browser lifecycle channel into a general remote-control protocol.

## Sources

- [Spring Boot 4.1 Developer Tools](https://docs.spring.io/spring-boot/4.1/reference/using/devtools.html)
  documents directory classpath monitoring, the two-classloader restart, trigger files, known
  classloading limitations and deprecated LiveReload support.
- [Gradle continuous builds](https://docs.gradle.org/8.14.4/userguide/continuous_builds.html)
  documents quiet-period coalescing, input watching and build-model limitations. Gradle remains a
  build adapter rather than Woge's semantic lifecycle.
- [HTML Standard: Server-Sent Events](https://html.spec.whatwg.org/multipage/server-sent-events.html)
  defines `EventSource`, reconnection and `Last-Event-ID` behavior.

## Reproduce

```bash
npm ci --prefix spikes/spring-boot-reload-topology
npx --prefix spikes/spring-boot-reload-topology playwright install chromium
./spikes/spring-boot-reload-topology/validate.sh
./spikes/spring-boot-reload-topology/measure-spring.sh 5 build/reload-measurements.tsv
```
