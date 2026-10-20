# ADR 0112: Keep consumer testing layered and optional

- Status: Accepted
- Date: 2026-10-20
- Decision owners: @christian-draeger
- Related issues: [#267](https://github.com/christian-draeger/woge/issues/267), [#268](https://github.com/christian-draeger/woge/issues/268)

## Context

The standalone Todo application provides a shared domain/HTTP/browser fixture. The
[consumer-testing experiment](../../spikes/consumer-testing/results.md) exercised skrape's published
HTML parser, direct Kotlin calls to current Playwright Java, the existing Kotlin DSL with a current
dependency, and Node Playwright Test over the same functional journeys.

## Decision

Use domain tests for rules/concurrency, host HTTP tests plus an optional independent HTML parser for
native outcomes, the real Woge codec for patch assertions, and real browsers for focus/live/DOM behavior.
Keep Node Playwright Test as the maintained repository browser gate. Document direct Playwright Java
as an optional Kotlin consumer path. Do not publish a new Woge test artifact on this evidence alone.

Generic form/label/attribute/diff improvements belong to skrape{it}; its boolean-attribute bug has a
separate tested upstream PR. Host adapters must match current Spring/Ktor APIs. Woge-specific fixture
and patch assertions remain on public Woge APIs. Preserve the existing browser CI during investigation.

## Alternatives considered

A single HTML/HtmlUnit engine cannot establish real-browser focus or streaming behavior. Replacing
the existing runner with a broad DSL would add lifecycle/reporting maintenance without a demonstrated
advantage. An optional small Kotlin fixture/receiver extension is a narrower future experiment.

## Consequences

Testing dependencies stay outside production. The DSL source compiles with Playwright 1.63.0, but
API inspection and one successful matrix are not proof of parallelism, failure ergonomics or flake
rate. Those comparisons and current upstream host-adapter migrations remain explicit follow-ups.

## Follow-up

Keep #267/#268 open for current host-adapter migrations, richer generic selection diagnostics and
runner lifecycle/failure comparisons. Promote a small test helper only after a repeated consumer gap.

## Subsequent evidence, 2026-10-20

The [bounded runner follow-up](../../spikes/consumer-testing/results.md#bounded-runner-follow-up-2026-10-20)
now supplies thread-confined parallel execution, repeated matrices and deliberate assertion failures
with retained diagnostics/traces. Published parser probes also pass against current WebFlux and Ktor
fixtures. The decision is unchanged. #267/#268 can close on the completed assessment and recipe;
upstream adapter/diff improvements remain recommendations, not a required new Woge test subsystem.
The shared-store activation observation is tracked separately in #276 with native-browser comparison.
