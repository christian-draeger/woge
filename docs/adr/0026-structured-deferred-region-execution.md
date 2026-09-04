# ADR 0026: Execute deferred regions as bounded request children

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#22](https://github.com/christian-draeger/woge/issues/22), [#23](https://github.com/christian-draeger/woge/issues/23), [#65](https://github.com/christian-draeger/woge/issues/65), [#122](https://github.com/christian-draeger/woge/issues/122), [#124](https://github.com/christian-draeger/woge/issues/124)

## Context

A Woge page should make its useful shell visible before independent server work such as summaries,
activity and tables completes. Those tasks may finish in a different order from their declaration,
time out or fail individually. A browser disconnect, host timeout or application shutdown must still
cancel all outstanding work.

[ADR 0005](0005-server-host-use-case-ports.md) already assigns request lifetime to the host adapter and
coroutine children to portable Woge/application work. [ADR 0022](0022-page-host-spi-contract.md)
defines a cold document flow, while [ADR 0023](0023-minimal-replace-patch-ir.md) defines the visible
replacement value. The missing boundary is the declaration and execution of independent regions. It
must not couple application code to Reactor, Servlet async types, Ktor channels or one wire format.

The asynchronous accessibility policy is not settled yet. Automatically adding live-region or busy
attributes now could create repeated announcements or leave a stable region marked busy after its
children are replaced.

## Decision

Portable page code declares a `DeferredRegion` in `woge-host-spi`. A declaration contains:

- one explicit `PatchTarget` and initial target revision;
- ordinary HTML DSL content for the immediate loading fallback;
- suspending work that returns safe materialized `PatchHtml`;
- a failure renderer that receives only `TIMED_OUT` or `FAILED`, never the original exception.

Creating or rendering a declaration does not start its suspending work. `regionPlaceholder` writes a
normal caller-selected HTML element containing the loading markup and the existing
`data-woge-region` and `data-woge-revision` browser contract. Application classes, ARIA attributes
and semantics remain ordinary HTML. Woge does not add an announcement or busy-state policy before
[#120](https://github.com/christian-draeger/woge/issues/120) resolves it.

`woge-server-runtime` provides one cold `DeferredRegionExecutor` flow. On collection it first rejects
duplicate targets and mixed page epochs. It then launches one child coroutine per declaration under
the collecting request scope. A semaphore limits active content work; the per-region timeout starts
after a task obtains that permit, while the host request timeout still bounds queueing and total
request lifetime. Updates are emitted in completion order rather than declaration order.

The provisional policy allows eight active regions and thirty seconds per active region. Both values
are configurable for a request/runtime. [#122](https://github.com/christian-draeger/woge/issues/122)
will either adopt or supersede these defaults as part of the cross-layer resource budget.

Normal content exceptions are isolated to their region. The executor renders controlled replacement
HTML using only the public failure category and retains the exception separately for adapter-side
diagnostics. Coroutine cancellation always propagates, including parent timeout and disconnect.
JVM `Error` values and a failure-renderer exception are fail-stop because continuing after either is
not a safe partial-recovery policy.

The executor emits `DeferredRegionUpdate.Resolved` or `DeferredRegionUpdate.Failed`, not a
`ReplacePatch` or encoded bytes. [#23](https://github.com/christian-draeger/woge/issues/23) owns patch
ID generation, revision transition, framing and shell-plus-patch transport. This keeps scheduling and
request lifetime independent from protocol encoding.

## Alternatives considered

- **Await regions in declaration order:** rejected because one slow earlier declaration would block a
  later completed region and defeat the visible out-of-order goal.
- **Start work in `GlobalScope` or an application executor:** rejected because disconnect, timeout and
  shutdown cancellation would no longer own all children.
- **Put patch encoding into the executor:** rejected because scheduling would become coupled to one
  browser transport and would have to invent patch IDs and revisions.
- **Pass the original exception to the HTML fallback:** rejected because rendering an exception
  message makes accidental disclosure of backend details easy.
- **Cancel every sibling after one region fails:** rejected because regions are declared independent
  and each has controlled failure content.
- **Add automatic `aria-live` and `aria-busy`:** deferred because the correct behavior depends on the
  interaction and document-owned announcement policy, not merely asynchronous execution.

## Consequences

### Positive

- The shell and its semantic loading content are renderable before any deferred work starts.
- Active work is bounded and emits naturally in completion order.
- Disconnect and request cancellation use standard structured concurrency without a Woge token.
- One region failure does not discard unrelated completed content.
- Application and test code stay independent from Spring, Reactor, Servlet and Ktor types.
- Patch transport can evolve without changing the region scheduling contract.

### Negative

- The initial fallback and final patch content are separate explicit renderers.
- Eight workers and thirty seconds are provisional defaults that require production evidence.
- All declarations create lightweight child coroutines even when they are waiting for a permit.
- Adapter diagnostics temporarily consume the retained cause directly until the semantic observation
  port in [#123](https://github.com/christian-draeger/woge/issues/123) exists.
- No-JavaScript final rendering and shell-plus-patch framing still require the following vertical
  integration work.

## Follow-up

- Map updates to revisioned Replace patches and stream them after the shell in
  [#23](https://github.com/christian-draeger/woge/issues/23).
- Reuse lifecycle tests from the adapter TCK in [#65](https://github.com/christian-draeger/woge/issues/65).
- Verify WebFlux disconnect and timeout behavior first in [#66](https://github.com/christian-draeger/woge/issues/66),
  followed by MVC and Ktor parity.
- Consolidate limits in [#122](https://github.com/christian-draeger/woge/issues/122), semantic events in
  [#123](https://github.com/christian-draeger/woge/issues/123) and recovery categories in
  [#124](https://github.com/christian-draeger/woge/issues/124).
