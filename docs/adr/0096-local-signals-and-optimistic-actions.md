# ADR 0096: Keep signals local and optimistic reconciliation explicit

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#56](https://github.com/christian-draeger/woge/issues/56)
- Refines: [ADR 0094](0094-lazy-local-island-lifecycle.md), [ADR 0062](0062-latest-intent-and-bounded-region-recovery.md)

## Context

Small islands need immediate reactions without a Compose-like renderer. Core has just 32 raw bytes
of headroom. Native forms, shared validation and server patches already cover most interactions.
Optimism must not turn local feedback into persistence authority or introduce mutation replay.

## Decision

Add a dependency-free, opt-in `signals.js` ES module with `.d.ts` types, package subpath
`@woge/fallback-client/signals`, and Node-free classpath `/assets/woge/signals.js` delivery.
Core, the island loader, framework JVM dependencies and patch protocol are unchanged.

- `signal(value)` exposes `get/set` with Object.is source equality and immutable reference values.
- `computed(fn)` lazily caches pure computations with dynamic dependencies. Explicit `dispose`
  releases subscriptions; consumed computed values are disposed after consuming effects.
- `effect(fn, {signal})` runs immediately and returns idempotent disposal. Return optional cleanup;
  cleanup is untracked, runs before reruns, and once on disposal. Mount AbortSignal owns effects.
- `batch(fn)` is synchronous, nestable and flushes at its outer boundary, including on exceptions.
  Invalidate the complete dependent graph before running effects in creation order. Coalesce queued
  effects; propagate errors after draining other effects. Bound feedback to 100 flush passes and
  detect recursive computed evaluation. No async scheduling or equality pruning of computed outputs.

`createOptimisticAction(signal)` owns one latest-intent scope. `submit(pureTransition)` captures the
immutable authoritative base, gives a monotonic local handle ID, immediately applies optimism, and
retains pending transitions in submission order. `resolve(serverValue)` installs an explicitly
supplied authoritative base, removes its own and older intent, then reapplies newer pending work.
`reject(code)` removes only that transition, replays others, and exposes a safe `{id, code}` failure
signal. `stale()` removes ignored/cancelled work without user failure. Pending count is observable;
new submissions clear failure. Duplicate or superseded settlements are no-ops returning false.
Transitions must be pure, synchronous and total; projection is evaluated before queue/base commits.
Snapshots retain references, not deep clones; application code must not mutate their contents.

This chooses **latest-intent**, not a generic concurrent command log: a newer applied response
supersedes older UI intent exactly as ADR 0062 does. Older out-of-order responses cannot regress the
base. Servers still enforce domain concurrency and persistence; independent commands needing all
acknowledgements belong in separate scopes or their own protocol. Client handle IDs are not server
request identities or authorization facts.

The application supplies outcomes from its real action owner. Our Chromium contract uses an actual
POST, ordering headers, successful patch application, a 400 validation patch, and an out-of-order
stale response. Resolve only after non-stale authoritative completion with explicitly decoded data.
Reject validation/transport/apply failures; drop cancelled/stale work. A before event is not a commit,
and an after event is not whole-stream completion. Existing enhanced forms emit failure codes but no
correlated success event; this API deliberately does not guess outcomes from global DOM events or
install another form interceptor. Uncertain server persistence needs explicit read-only recovery;
local rollback cannot undo a server commit, and no POST is replayed. Transactions retain ADR 0092's
bounded apply guarantees, not whole-response or database atomicity.

Effects use normal DOM APIs. There is no component tree, virtual DOM, general DOM diffing, implicit
props merging, hydration graph or global store. Names echo familiar signal concepts cheaply without
polyfilling TC39's proposal. Kotlin/JS islands may declare tiny application-owned `external` ES module
bindings; plain JS/TS works identically. See the [guide](../guides/island-signals.md).

## Alternatives considered

- Bundle a UI runtime into core: violates optional-cost and ownership boundaries.
- Infer success from after-Replace or HTTP 200: later patch failure/stale work/validation can disagree.
- Await responses in submission order: an old network stall must not block newer authoritative intent.
- Replay every older pending command over newer success: double-counts superseded state and conflicts
  with latest-intent patches. Generic independent mutation semantics need a separate domain protocol.
- Automatic POST replay or rollback of server transactions: neither is safe nor available here.
- Add a Kotlin wrapper module: unnecessary toolchain/API overhead for five small web-native functions.

## Consequences

The API adds optional local state without expanding server-rendered component ownership. Explicit
outcome bridging avoids false acknowledgements but requires application transport code. Computed
subscriptions need disposal; immutable values and total transitions are application responsibilities.
Latest-intent reconciliation is intentionally narrower than independent concurrent persistence.
No existing runtime byte ceilings or no-JavaScript behavior change.

## Evidence and cost

Node 26.3.0, macOS, esbuild 0.28.2, gzip level 9/default Brotli:

| Artifact | Raw / gzip / Brotli | Fixed ceilings |
| --- | --- | --- |
| Signals minified module and complete graph | 2,417 / 1,151 / 1,056 | 4,000 / 1,800 / 1,600 |
| Signals classpath source | 5,417 / 1,839 / 1,554 | 6,500 / 2,300 / 2,000 |
| Unchanged core | 35,968 / 12,146 / 10,764 | 36,000 / 12,500 / 11,000 |

Reproduce bytes with `cd client/woge-fallback-client && npm run budgets`. No existing ceiling rises.
Reproduce runtime with `npm run build && npm run benchmark:signals`: 1,000 source signals feeding one
computed sum/effect, 10,000 writes, one warm-up and median of five runs, excluding setup/disposal.
Measured **535.307 ms unbatched (10,001 effect runs)** versus **0.202 ms batched (2 runs)**.
This deliberately high-fan-in workload illustrates avoided repeated work, not a universal speedup.
The benchmark validates final sums/run counts but has no timing threshold and is outside normal CI.

Unit contracts cover lazy/dynamic dependencies, diamonds, batching/order, cleanup/abort/disposal,
cycles/errors, captured bases, success/rejection, concurrent transitions and out-of-order outcomes.
Chromium exercises actual island lifecycle and real server success/validation/stale patch responses.
The package test verifies the subpath from a packed consumer; the existing classpath wildcard ships
it without requiring Node. No production source depends on experimental spikes.

## Follow-up

Keep framework byte ceilings fixed and runtime measurements informational. General independent-command
reconciliation, automatic response decoding, a Kotlin binding library and DOM rendering remain out of
scope. The reference KMP note island is not changed; the focused server/browser fixture proves this
boundary without complicating its shared rules. Applications own explicit recovery for uncertain
persistence and domain-specific ordering; any broader protocol requires a concrete use case and ADR.
