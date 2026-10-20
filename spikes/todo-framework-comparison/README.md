# Todo framework comparison

State: Active, #269. Time box: four JVM fixtures, four HTTP workloads and shared three-engine
native/enhanced browser journeys, three measured rounds. The isolated comparison identifies
incremental costs; it does not rank production capacity. No production project depends on it.

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
./gradlew -p spikes/consumer-testing installDist
docker build --tag woge-todo-comparison:local spikes/todo-framework-comparison
python3 spikes/todo-framework-comparison/run.py \
  --container-image woge-todo-comparison:local \
  --browser-journeys --browser-bytes --skrape-probe
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

Container mode verifies actual 2-CPU/768-MiB limits and records the image ID and server JRE separately
from the host build JDK. The browser byte probe uses Chromium 153 in Playwright 1.63.0, a fresh context,
no compression and the same CSS. Docker reaches the host through host networking on Linux or
`host.docker.internal` on macOS. A local-only secure-origin flag supports the byte probe; full
three-engine journeys use a streaming loopback proxy. This is a measurement topology, not a deployment
recipe. Resource/HTTP/page failures fail the probe; live-fetch cancellations are recorded separately.
Full browser journeys separately record fill/submit/visible completion. Without the container/browser
options the HTTP harness can run directly on the host with only JDK/Python.

## Equivalence and remaining gaps

All modes escape the same titles, preserve invalid submitted text, return native 400/303 responses,
check a hostile Origin and mutate the same store. Shared browser tests verify filtering, enhanced
validation/focus, list/count/status/form updates and two-tab SSE/refetch preserving draft and focus.
The htmx fixture owns its validation/live-read serialization adapter. Woge additionally supplies typed
decoding, identities, managed ordering and recovery. Malformed protocol input, reconnect/deferred
streams and account authorization are not equivalent workload proofs. The baseline's strict Origin
check is a limited fixture policy, not a replacement for Woge's host request-context policies.

The HTTP workloads compare particular responses; shared browser outcomes are verified separately.
Native mutation timing ends at 303, before the redirected GET; validation is a native response.
The render loop measures board markup + snapshot + UTF-8 bytes only; Woge's typed outer-region
identity/framing is outside that micro-loop. It uses ThreadMXBean allocations on the current thread,
not application-wide or retained memory. JIT/GC and other desktop activity remain uncontrolled even
when each application has fixed container limits.
All dependencies are in the combined fixture build, including unused host/render libraries.

[Isolated results](isolated-results.md) include unfavorable observations, all rounds and compressed raw
evidence. The original [unisolated results](results.md) preserve the earlier simpler baseline; do not
mix those datasets. Separate clean builds/minimal distributions and a rigorous arrival-rate load
model remain open under #269. A Next.js comparison needs a separately defined equivalent application
and deployment setup. Existing [performance budgets](../../docs/performance/budgets.md) remain
unchanged; no general comparative marketing claim follows from these descriptive measurements.
