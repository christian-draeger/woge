# Todo framework comparison

State: Active, #269. Time box: four JVM fixtures and four bounded HTTP workloads, three measured
rounds. This first stage identifies costs and missing equivalence; it does not rank production
capacity. No production project depends on this experiment.

The same ten-row, synchronized Todo repository and add/toggle/delete rules power all modes:
`spring-woge`, `spring-thymeleaf`, `ktor-woge`, `ktor-html`. The Woge modes compile the maintained
Todo page/actions. The baseline modes use Thymeleaf or kotlinx.html and htmx. The Spring pair uses
the same WebFlux host; the Ktor pair uses the same Netty host. All modes have the same classpath,
JDK and JVM flags, so these RSS/startup figures cannot describe minimal standalone distributions.

## Reproduce

From the Woge root, with JDK 21, Python 3, Docker and Node:

```shell
./gradlew publishScaffoldArtifacts
./gradlew -p spikes/todo-framework-comparison \
  -PwogeRepository="$PWD/build/scaffold-maven-repository" installDist
npm --prefix spikes/todo-framework-comparison ci --ignore-scripts
python3 spikes/todo-framework-comparison/run.py --browser-bytes
```

The build uses explicit local candidate artifacts; it is not a published-artifact consumer proof.
The independent Todo CI supplies that separate proof. htmx 2.0.11 is downloaded with a checked
SHA-256; it is never fetched from a CDN by the browser. Set JAVA_HOME to the recorded JDK when
comparing runs. `--samples`, `--concurrency` and `--rounds` make the load explicit.

The harness records all request timings/status failures, response sizes, startup, RSS, cumulative
server CPU and render-loop allocations under `build/evidence`. It rotates process order, sends
100 warm-up requests per workload and makes 300 measured requests at concurrency four by default.
One connection per worker keeps HTTP connections alive. This is closed-loop load without
coordinated-omission correction, not an arrival-rate capacity test. There is no throughput threshold.
A functional/status or resource-load failure stops the run rather than counting errors as fast work.

The browser byte probe uses actual Chromium 153 resource timing in Playwright 1.63.0, a fresh
context, no compression and the same Todo CSS. Docker reaches the host through `host.docker.internal`;
an explicit local-only secure-origin flag supplies the secure APIs normally available over HTTPS.
This is a measurement topology, not a deployment recipe. Script/CSS/HTTP/page errors fail the probe;
live-fetch cancellations are recorded separately. It measures initial resources, not browser action
latency or a warm HTTP cache. Without `--browser-bytes`, Docker/npm are unnecessary.

## Equivalence and remaining gaps

All modes escape the same titles, preserve invalid submitted text, return native 400/303 responses,
check a hostile Origin and mutate the same store. Enhanced toggle updates the list/count in one
board. The Woge response additionally supplies status/form replacements, identities, typed decoding,
ordering and recovery; htmx returns a single HTML fragment. The simpler baseline does not implement
the full filter journey, enhanced validation focus/400 handling, invalidation/reconnect/deferred
streams, replay protection or account authorization. Its strict Origin check is a limited fixture
policy, not a general replacement for Woge's host request-context policies.

Consequently the HTTP workloads compare particular responses, not fully equivalent browser journeys.
Native mutation timing ends at 303, before the redirected GET; validation is a native response.
The render loop measures board markup + snapshot + UTF-8 bytes only; Woge's typed outer-region
identity/framing is outside that micro-loop. It uses ThreadMXBean allocations on the current thread,
not application-wide or retained memory. JIT/GC and other desktop activity remain uncontrolled.
All dependencies are in the combined fixture build, including unused host/render libraries.

[Recorded results](results.md) include unfavorable observations and raw data. Keep #269 open for
equivalent full browser/validation/live fixtures, repeatable CPU/container isolation, separate clean
build/artifact footprints, successful interaction latency and a more rigorous load model. Only then
consider a cross-ecosystem comparison. Existing [performance budgets](../../docs/performance/budgets.md)
remain unchanged. No comparative marketing claim follows from this first stage.
