# ADR 0116: Bound multipart diagnostics to the test harness

- Status: Proposed
- Date: 2026-10-20
- Decision owners: Woge maintainers
- Related issues: [#278](https://github.com/christian-draeger/woge/issues/278)

## Context

A repeated WebFlux multipart TCK request timed out instead of returning 413 on Linux amd64.
Isolated repetitions have not established a cause. Thread stacks alone cannot correlate the JDK
client, Reactor request-body subscription, coroutine decoder, file cleanup and response lifecycle.

## Decision

Add `TckUploadDiagnostics` to the testing module as a test-fixture hook. Its public header constant
and recording method let adapter test sources join the same in-memory timeline. Only request IDs
registered by the local TCK accept events. Callers supply fixed phase labels, safe byte counts,
transport/exception types and connection IDs, never payloads or sensitive metadata. The recorder
retains at most 2,048 events per fixture and releases registrations after verification.

Keep the host SPI, production decoder and production request/response protocols unchanged. The
diagnostic header belongs only to loopback TCK fixtures; applications do not install these hooks.
Failures print the timeline, and an environment flag retains successful timelines in JUnit XML.
The investigation runs on native Linux amd64 after the clean root gate, compares three parallel
and three serial three-host attempts with nginx, and stops at the first failure within a fixed
time budget. Reports remain distinct and successful processes must supply fresh executed TCK XML.
The serial control limits Gradle to one worker, and XML suite time windows verify the actual
presence or absence of host overlap; a CLI mode label alone is insufficient evidence.

## Alternatives considered

- **A production observer API:** extends the runtime contract before the missing evidence is known.
- **Global HTTP wire logging:** may disclose bodies, headers or file metadata and adds excessive I/O.
- **Longer timeouts or assertion retries:** conceal the original failure without identifying its cause.

## Consequences

The testing artifact gains a narrow public diagnostic utility; production artifacts and limits do
not change. In-memory recording still changes scheduling slightly, so passing instrumented runs
cannot prove the uninstrumented defect is fixed. Bounded runs can finish without reproducing it.

## Follow-up

Use the first failing timeline to select a focused reproducer and regression, then fix the demonstrated
cause. Retain an explicit unresolved finding if the investigation remains green. Reproduction commands
and evidence locations are in [Build and test Woge](../development/build-and-test.md).
