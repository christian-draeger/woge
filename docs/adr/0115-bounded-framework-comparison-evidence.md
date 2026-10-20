# ADR 0115: Bound framework comparisons to the measured application question

- Status: Accepted
- Date: 2026-10-20
- Decision owners: @christian-draeger
- Related issues: [#269](https://github.com/christian-draeger/woge/issues/269), [#277](https://github.com/christian-draeger/woge/issues/277)

## Context

The [isolated Todo comparison](../../spikes/todo-framework-comparison/isolated-results.md) verifies
shared WebFlux/Ktor native, validation, filtering and enhanced/live outcomes over three measured
rounds. The common distribution holds host/dependency setup fixed for incremental comparisons.
It also means RSS/startup and clean fixture builds do not describe minimal standalone applications.
The earlier unisolated, simpler baseline remains historical evidence and cannot be mixed with it.

## Decision

Use the completed #269 dataset as a bounded descriptive JVM comparison, including its unfavorable
Woge observations, exact versions/source hashes, raw samples, errors, cache states and fixed
application resources. Freeze the executable snapshot after its functional CI passes. Keep current
deterministic production budgets; noisy comparative throughput is informational.

Record common fixture clean-output and unchanged incremental builds explicitly as common builds.
Do not turn those numbers into independent framework build rankings or minimum footprints.
No production capacity, cross-ecosystem winner or speed superiority follows from these measurements.
The independently scoped #277 owns a Next.js/full-deployment study with minimal distributions and
separate build/setup costs. New deferred/saturation workloads must state their own equivalent contract.

## Alternatives considered

Hello World RPS or unequal browser journeys would hide application work. Treating all future load,
deployment and ecosystem questions as prerequisites would keep one bounded experiment permanently
open. Independent minimal applications answer a valuable later deployment question but do not
replace the controlled same-host incremental question already measured here.

## Consequences

The comparison can guide profiling and design decisions without a marketing winner. A shared
classpath, browser automation overhead, small interaction samples, warm OS/dependency caches and
closed-loop load stay visible limitations. Reopening a frozen measurement requires explicit scope
and new evidence. Production host/protocol/security conformance remains in maintained tests.

## Follow-up

Use #277 for the separate cross-ecosystem study. Keep #276's interaction-preservation investigation
separate from runner isolation and POST execution; do not hide its shared-store observation by retries.
