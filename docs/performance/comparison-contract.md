# Compare equivalent web application work

Choose the application question and its observable result before collecting timings. Record the
workload revision, evidence and approved wording together so a reader can tell which use case and
versions a claim covers. [Claim and evidence register](claim-evidence.md)

This contract governs new work under [#284](https://github.com/christian-draeger/woge/issues/284).
The bounded [October 2026 JVM comparison](../../spikes/todo-framework-comparison/isolated-results.md)
retains its own scope, frozen inputs and original results.
[ADR 0115](../adr/0115-bounded-framework-comparison-evidence.md)

## Todo workload v1

The `todo-v1` definition specifies a small server-backed list with validation and two browser sessions.
Reset the same ordered dataset at the beginning of each independent journey. Use ten rows numbered
1–10, titled `Todo 01` through `Todo 10`. Rows 1–5 are active and rows 6–10 are complete.
Use `Read <HTML> & CSS` for a valid submission and three spaces for an invalid one.
Pin and record the fixture source and seed. Change the workload revision when data, observable
success criteria or an intentionally different capability would affect comparability.

| ID | Task | Required observable outcome |
| --- | --- | --- |
| N1 | Open and filter the list without JavaScript | Useful HTML contains ten rows and five active items. Active and completed URLs each show the corresponding five rows, with labeled controls and the selected filter identified. |
| N2 | Submit, complete and delete without JavaScript | Add exactly one active row and show escaped text after the redirect. Completing it puts it under the completed filter. Deletion restores the original counts. Preserve the selected view on redirects. |
| N3 | Submit invalid text and correct it without JavaScript | Preserve the literal input, associate the visible error with its field, keep the list unchanged and accept one subsequent corrected submission. |
| E1 | Add, complete and delete with enhancement | Reach the same server state as N2 and see the correct list, counts, form and status in the existing document. The inserted title remains text. |
| E2 | Submit invalid text and correct it with enhancement | Reach the same server state as N3. Preserve the draft, expose the field error and focus a useful error summary. Clear errors on the corrected submission. |
| S1 | Send invalid and cross-origin requests directly | Enforce equivalent server-side validation and origin/CSRF policy. The hostile request changes no state. Apply the same identity, credentials and ownership fixture to each entry when authenticated work is in scope. |

N1–N3 and S1 are mandatory for a claim about the complete native Todo workflow. E1 and E2 extend
that contract for a progressive enhancement claim. An aspect study declares its common scenario
IDs, deliberately different capabilities and excluded scenarios. Record every missing required
outcome as unsupported or failed in the capability matrix. A closely related aspect with shared
outcomes can still be measured under its own claim. Keep each contender's defaults and
feature-equivalent opt-ins visible. Count the application code, assets, setup and resource costs of
each chosen opt-in.

| Optional aspect | Additional observable outcomes when selected |
| --- | --- |
| L1 live refresh | An independent session updates the list and counts without replacing the first session's unsent draft or moving its focus. A valid activation commits once. Recover from disconnect with current state and current authorization. |
| D1 deferred completion | A useful shell appears before the controlled region completes. Measure useful shell and completed visible content separately. Verify the complete native path, cancellation and failure behavior. Fix region count, payload sizes and release gates in the study manifest. |
| R1 uncertain mutation | Use a controlled lost response and compare the state read through safe recovery. Record the actual outcome and any explicit second activation. Count replay, duplicate commit or misleading success as correctness failures. |

For each study pin row count, selected views, data payload sizes, input bounds, authorization rules,
CSS, proxy behavior, failure schedule and state-reset commands. A large table, authenticated
persistent workload or different disclosure of native support needs a separately versioned fixture.
Use the maintained [Todo consumer](../../examples/todo-spring-boot/README.md) and
[adapter contract](../../testing/woge-adapter-tck/README.md) where they exercise the promised
outcomes. [The maintained runner has its own delivery issue](https://github.com/christian-draeger/woge/issues/299).

## Classify the question and the compared applications

| Experiment | What stays fixed or is reported separately |
| --- | --- |
| Same-host incremental cost | Host/engine, repository, rules, run limits and classpath policy. Describe any unused dependencies in a common distribution. |
| Independently packaged full applications | Equivalent deployed task, resource limits and available features. Measure each app's production dependency closure, build, startup, transfer and operation separately. |
| Isolated rendering | Input snapshot, output work, encoding, sink, iteration boundaries and measured thread/process allocation scope. |
| Browser interaction | Browser/version, JS mode, network/cache/CPU and useful visible completion. Separate first shell, completed content, submit-to-visible result, assets, transfer bytes and browser work. |
| Feature change or defect injection | Starting artifact, instructions, requested change, prior knowledge and the same deterministic behavior checks. Record edits, time and incomplete or unsafe outcomes. |
| Human or agent study | Participant/model and tools, familiarity, task order, assistance, time/cost and common checks. Preserve incomplete attempts. Keep participant results separate from synthetic timing. |

Record both the **server host and engine** and the **application rendering approach** for every
result row. A comparison across server hosts measures that combination. Within a fixed host a
renderer or framework change answers the incremental question. A client-owned approach can occupy
a separate row for an equivalent measured aspect. Publish separate results per question and
workload to keep unlike measurements out of a single score.

| Host axis | Woge | Same-host alternative renderer and interaction approach |
| --- | --- | --- |
| Spring Boot WebFlux with Reactor Netty | Frozen Todo comparison | Thymeleaf with htmx and fixture adapter, measured in the frozen comparison |
| Spring Boot MVC with declared Servlet engine | Maintained Woge adapter fixture | No result in the frozen comparison |
| Ktor with Netty | Frozen Todo comparison | `kotlinx.html` with htmx and fixture adapter, measured in the frozen comparison |
| Quarkus | Await the declared adapter contract and capability evidence | Record explicit cells when an equivalent fixture exists |

The matrix identifies scope, and each experiment records its exact libraries and feature choices.
Use primary documentation to verify contender defaults and optional behavior.
[Thymeleaf](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html) ·
[htmx](https://htmx.org/reference/) · [kotlinx.html](https://github.com/Kotlin/kotlinx.html) ·
[Woge adapter parity](../architecture/server-adapter-parity.md)

## Predeclare, run and retain

Use these fields in the study manifest and link it from each result or claim record.

| Record | Required information |
| --- | --- |
| Question and success | Workload revision and scenario IDs, host × rendering approach, expected status and state assertions, treatment of defaults and opt-ins, primary metrics with units and timing endpoints, practical effect threshold or SLO, success/failure rules and exclusions. |
| Build identity | Exact source commits and any local changes, artifact/image IDs or digests, fixture hashes, Woge and comparator versions, dependency locks, toolchain versions, compile/package/run commands and production options. |
| Environment | OS, architecture, CPU and memory limits, host and load generator placement, runtime/JVM/GC/heap settings, proxy and transport, compression, cache state, dependency cache, file/OS cache and cold or warm startup definition. Record browser and actual network/CPU/cache settings where used. |
| Run plan | Dataset and reset, pilot variation, sample size and independent unit, warmup, concurrency or arrival schedule, pacing, deadlines, round duration, rotation or order and seed. Specify whether profiling or observation is enabled. |
| Raw outcomes | Run ID, scenario, round/order, intended and observed load, individual samples or included timestamp/outcome events, correctness checks, resource readings, timeouts, drops, failed/incomplete attempts and exclusion reasons. Keep the original logs, data and reproduction command. |

Choose the smallest difference that matters for the use case before the main run. Use pilot
variation to choose rounds, repetitions or participants that can resolve that difference with the
declared method. Keep pilot data labeled. For within-machine comparisons, rotate or randomize
contender order and use rounds as the unit of inference when requests within a round share state or
conditions. For participant studies use the participant or properly paired trial as the unit.

Report all round values and the spread or uncertainty at the chosen unit. Give the total attempts,
correct outcomes and latency distribution of completed work together. Include p50/p95 and tail
quantiles only where there are enough samples to resolve them. Label small samples descriptive. Report an inconclusive result when uncertainty
leaves materially different decisions plausible. Retain errors, slower rounds, timeouts and
exclusions with their reasons. A missed correctness criterion blocks a positive claim for that
scenario, regardless of its measured speed. Keep qualitative failures and evidence of costs
available for corrective issues.

## Arrival-rate and saturation measurements

Closed-loop work starts a new operation after an earlier one completes. Report concurrency and
achieved throughput for that schedule. In a slowdown the generator offers less work, so completion
samples can underrepresent the delay an independent arrival would experience.

For an open-loop run, schedule starts independently of prior responses. Retain the planned arrival,
actual dispatch and terminal outcome for each operation. Use a monotonic clock in the driver to
measure scheduled arrival to completion. Report dispatch minus scheduled arrival as generator
queue delay, and completion minus dispatch as the observed request interval. Measure a server-side
queue separately when there is a verified event and clock boundary. Set a bounded arrival window,
drain/deadline and dropped-work policy before the test.

Report intended rate, actual started rate, correct completion rate, queue delay, errors, timeouts,
drops and unfinished work at each load level. Use all scheduled arrivals as the denominator for
deadline-based success. Record generator capacity at the tested load so generator exhaustion is
visible. Latency distributions of completed operations go alongside that denominator. Scheduled
timestamps preserve observed queueing and expose coordinated omission. When a tool estimates omitted
samples, retain the original observations, disclose the method and expected interval and label its
output as a separate estimate. [Open versus closed load models](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/)

## Observation and instrumentation

Declare a minimal/no-op measurement mode. If observation is part of the question, run a separate
comparable configuration with enabled signals, SDK and exporter versions, sampling, export
destination, resource sharing and dropped telemetry recorded. Measure instrumentation overhead
with paired runs and report the difference and variability for each contender. Keep profiling
separate when profiling changes the workload. Document which clocks and identifiers connect the
driver, server and browser. Avoid subtracting timestamps from unrelated clocks without a verified
synchronization boundary.

Woge semantic events describe operations such as page use-case execution, shell rendering, deferred
region work, patch encoding and patch application. Measure end-to-end HTTP or visible outcomes at
the driver or browser. Use profilers and resource counters for CPU, allocations and memory
attribution. Declare which semantic events fire in the selected path and how observations are
correlated. Treat Spring coroutine parent-span propagation as unverified by the current bridge.
Keep sensitive payloads and credentials out of evidence, and use bounded operation/outcome dimensions for
metrics. [Semantic observation guide](../guides/semantic-observability.md)

## Review and publication

Store the versioned manifest, raw rounds and analysis beside the result. Add a scoped claim record
with approved wording and the trigger for revalidation. A bounded negative or inconclusive result
completes its study when the evidence is retained. File corrective work for reproduced gaps, with
the failed scenario and supporting evidence. Keep the frozen comparison and public-demo response
times separate from new controlled runs.

Keep deterministic functional equivalence tests and the existing [regression budgets](budgets.md)
in ordinary CI. Comparative timing and live human/model calls run separately. An eventual
machine-readable runner manifest belongs with [#299](https://github.com/christian-draeger/woge/issues/299).
