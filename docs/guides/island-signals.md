# Local signals and explicit optimistic actions

Most interactions need only **server-rendered forms and patches**. Start there. Use signals only
inside a small island needing immediate local feedback, such as a counter. They do not render HTML,
diff DOM, hydrate components, authorize writes, or replace server validation. They are not a
TC39 Signals polyfill or a global application store.

## A local reaction

Import the optional module from `@woge/fallback-client/signals` in a bundler, or from
`/assets/woge/signals.js` with Node-free classpath delivery. For npm static deployment, copy `dist`
and import its `signals.js`. Core never imports this module.

```javascript
import { signal, computed, effect, batch } from "/assets/woge/signals.js";

export function mount(root, props, { signal: lifetime }) {
  const text = signal("");
  const maximum = signal(Number(props.maximum)); // Validate props in your application.
  const remaining = computed(() => maximum.get() - text.get().length);
  const input = root.querySelector("input");
  effect(() => {
    root.querySelector('[role="status"]').textContent = `${remaining.get()} characters left`;
  }, { signal: lifetime });
  input.addEventListener("input", () => text.set(input.value), { signal: lifetime });
  return {
    update(next) { batch(() => { maximum.set(Number(next.maximum)); text.set(input.value); }); },
    dispose() { remaining.dispose(); },
  };
}
```

`signal(initial).get()` tracks reads; `.set(next)` invalidates dependents only when `Object.is`
changes. Values are ordinary references: use immutable values, not in-place mutations.
`computed(fn)` is lazy, cached and dynamically tracks only its latest reads. Computations must be
pure. Dispose computed values after their consuming effects to release their upstream subscriptions.
The island loader aborts the lifetime before calling the controller's `dispose()`.

`effect(fn, {signal})` runs immediately, then synchronously after invalidation. It returns an
idempotent dispose function. An optional cleanup function returned by `fn` runs untracked before
reruns and once on disposal. An already-aborted signal prevents execution. Use normal `textContent`,
attributes and `classList`; no HTML renderer is supplied. An invalidated effect runs even if its
computed result happens to remain equal.

`batch(fn)` defers effects until the outermost synchronous batch ends (even on an exception).
Reads inside a batch see writes immediately. Diamond dependencies are invalidated before effects
run; effects flush in creation order, with duplicate invalidations coalesced. Effect writes may
schedule another pass; more than 100 passes throw `WOGE_SIGNAL_EFFECT_CYCLE`. Computed cycles throw
`WOGE_SIGNAL_COMPUTED_CYCLE`. Errors are not swallowed: other queued effects still run, then the first
error is rethrown. Do not put an async function in a batch; later awaits are outside its scope.

## An optimistic transition is not a successful save

```javascript
import { signal, createOptimisticAction } from "/assets/woge/signals.js";
const count = signal(0);
const adds = createOptimisticAction(count);
const mutation = adds.submit((base) => base + 1); // Immediate local display.
// Later, only after the real response's non-stale patch successfully applies:
mutation.resolve(authoritativeCount);
// Alternatively, on server rejection or transport/apply failure:
// mutation.reject("NOTE_REJECTED");
// Or on superseded/aborted work: mutation.stale();
```

The action owns writes to this signal for its lifetime; do not also set it independently. `submit` captures the immutable
base in `mutation.base`, assigns a local monotonic `id` (not a request/replay identity), and queues a
**pure, synchronous, non-throwing** transition. Each transition receives the prior transition's
value. Never send requests or mutate inputs inside it: it is replayed during reconciliation.

Success replaces the base with the supplied server value, removes that mutation **and older intent**,
and reapplies remaining newer transitions in submission order. Rejection removes only its mutation
and reapplies the others over the last authoritative base. `pendingCount.get()` and
`failure.get()` are read-only signals. Failure is `{id, code}` or `null`; a new submission clears it,
a successful settlement retains the last failure until then. Codes should be application-safe codes,
not raw response bodies or exception messages. Duplicate/late settlements return `false` without
changing anything. `stale()` removes optimism without a user error.

This is **one latest-intent scope**, matching Woge region ordering, not a log of independent database
commands. A newer accepted server snapshot supersedes older UI intent; older handles cannot overwrite
it. Your server still needs domain versions, authorization and an explicit concurrency policy. If
commands must all remain independently pending rather than be superseded, use separate scopes or an
application-specific protocol. Never infer persistence from local state or replay a POST automatically.

### Connect to actual server outcomes

The executable [browser island](../../client/woge-fallback-client/browser-tests/signals-island.mjs)
uses a real POST and `runtime.applyPatchStream(response.body, {signal: lifetime})`. Its
[server fixture](../../client/woge-fallback-client/browser-tests/server.mjs) sends both successful
patches and a 400 validation patch. The island sits **outside** the replaced server region.

For your own action transport:

1. Register `runtime.beginInteraction([target])` **before** the request. Send its epoch, interaction
   and base revision using your existing host's typed action contract. Keep CSRF/authentication,
   request identity and validation behavior required by that host; signals do not supply them.
2. Check the HTTP outcome, negotiated patch content type, and expected `Woge-Validation` summary.
   A validation response is rejection even when its error patch applies successfully.
3. Await patch application/completion. Resolve with explicitly decoded, validated authoritative data
   from the applied region/response only for a non-stale successful response. Use a dedicated response
   scope: a mixed stream with stale and current patches is not an unqualified success for this action.
4. Call `stale()` for ignored older intent (`stalePatchCount`/`WOGE_STALE_PATCH`), cancellation or
   aborted lifetime; call `reject(safeCode)` for validation, network and patch-apply failures. On an
   uncertain outcome, rollback is **local display only**, not a database rollback; offer an explicit
   authoritative GET/full-page recovery, never an automatic mutation retry.
5. Bind listeners, effects and fetch to the mount signal. A containing Replace needs a stable island
   key to preserve it, or must deliberately dispose/reinitialize it. Do not merge incoming props.

`woge:after-replace`/append/remove events expose applied DOM, but **not** whole-action completion;
before events are not commit promises. Transactions deliver after events after all member commits,
yet a later stream unit can still fail. Enhanced forms expose `woge:action-error` with a safe code,
not a success event or per-mutation correlation; stale errors may be suppressed. Therefore do not
resolve an optimistic action just because a global lifecycle event fired or a submit occurred.
Keep a request-local handle and an explicit response-to-value bridge, as in the fixture, or have
your application's existing action owner supply the outcome. Do not install two submit owners.

## Kotlin/JS interop and cost

Kotlin islands keep the same ES `mount` contract. An application-owned binding can declare
`@JsModule("@woge/fallback-client/signals") external fun <T> signal(initial: T): LocalSignal<T>`
and `external interface LocalSignal<T> { fun get(): T; fun set(value: T) }` at file scope, alongside
bindings for only the functions it uses. Its bundler resolves the package; unbundled deployment may
instead use `@JsModule("/assets/woge/signals.js")` and retain that ES import. Alias the mount
AbortSignal to avoid confusing it with the `signal` factory. This is not a new Kotlin runtime or
wrapper library, and framework JVM modules still need no Node/Kotlin/JS toolchain.

See [ADR 0096](../adr/0096-local-signals-and-optimistic-actions.md) and
[performance budgets](../performance/budgets.md) for bytes and reproducible runtime measurements.
The reference shared-rules example remains unchanged: its `READY` phase is not a persisted result.
