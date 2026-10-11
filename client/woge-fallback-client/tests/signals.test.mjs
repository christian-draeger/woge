import test from "node:test";
import assert from "node:assert/strict";
import { signal, computed, effect, batch, createOptimisticAction } from "../src/signals.js";

test("Object.is writes, lazy caching and dynamic dependencies", () => {
  const left = signal(1), right = signal(10), choose = signal(true);
  let calls = 0;
  const selected = computed(() => { calls++; return choose.get() ? left.get() : right.get(); });
  left.set(2);
  assert.equal(calls, 0);
  assert.equal(selected.get(), 2);
  assert.equal(selected.get(), 2);
  assert.equal(calls, 1);
  const values = [];
  const stop = effect(() => { values.push(selected.get()); });
  batch(() => { choose.set(false); right.set(11); });
  left.set(3);
  right.set(11);
  assert.deepEqual(values, [2, 11]);
  assert.equal(calls, 2);
  stop(); selected.dispose();
  assert.throws(() => selected.get(), /DISPOSED/);
});

test("diamond dependencies are glitch-free and effects flush in creation order", () => {
  const source = signal(1);
  const a = computed(() => source.get() * 2), b = computed(() => source.get() * 3);
  const diamond = computed(() => a.get() + b.get());
  const runs = [];
  const stop1 = effect(() => { runs.push([1, source.get(), diamond.get()]); });
  const stop2 = effect(() => { runs.push([2, diamond.get()]); });
  batch(() => {
    source.set(2);
    assert.equal(diamond.get(), 10);
    batch(() => source.set(3));
    assert.equal(runs.length, 2);
  });
  assert.deepEqual(runs, [[1, 1, 5], [2, 5], [1, 3, 15], [2, 15]]);
  stop1(); stop2(); diamond.dispose(); a.dispose(); b.dispose();
});

test("effects clean up untracked on rerun, abort and repeated disposal", () => {
  const lifetime = new AbortController(), value = signal(0), other = signal(0);
  const events = [];
  const stop = effect(() => {
    events.push(value.get());
    return () => { other.get(); events.push("cleanup"); };
  }, { signal: lifetime.signal });
  value.set(1); other.set(1);
  batch(() => { value.set(2); lifetime.abort(); });
  stop(); value.set(3);
  assert.deepEqual(events, [0, "cleanup", 1, "cleanup"]);
  effect(() => { assert.fail("aborted effect ran"); }, { signal: lifetime.signal });
});

test("effect disposal during its own initial run releases returned cleanup", () => {
  const lifetime = new AbortController();
  let cleanups = 0;
  effect(() => { lifetime.abort(); return () => { cleanups++; }; }, { signal: lifetime.signal });
  assert.equal(cleanups, 1);
});

test("batch exceptions still flush; failed effects do not strand later effects", () => {
  const value = signal(0), values = [];
  const stop1 = effect(() => { if (value.get()) throw new Error("effect failed"); });
  const stop2 = effect(() => { values.push(value.get()); });
  assert.throws(() => batch(() => { value.set(1); }), /effect failed/);
  assert.deepEqual(values, [0, 1]);
  stop1();
  assert.throws(() => batch(() => { value.set(2); throw new Error("batch failed"); }), /batch failed/);
  assert.deepEqual(values, [0, 1, 2]);
  stop2();
});

test("computed and effect cycles fail boundedly", () => {
  const cycle = computed(() => cycle.get());
  assert.throws(() => cycle.get(), /COMPUTED_CYCLE/);
  cycle.dispose();
  const source = signal(0);
  assert.throws(() => effect(() => { source.set(source.get() + 1); }), /EFFECT_CYCLE/);
  source.set(0);
});

test("optimistic success rebases pending transitions and captures immutable base", () => {
  const state = signal(10), action = createOptimisticAction(state), observed = [];
  const stop = effect(() => { observed.push([state.get(), action.pendingCount.get()]); });
  const first = action.submit((n) => n + 1), second = action.submit((n) => n * 2);
  assert.equal(first.base, 10); assert.equal(second.base, 10);
  assert.equal(state.get(), 22);
  assert.equal(first.resolve(12), true);
  assert.equal(state.get(), 24);
  second.resolve(25);
  assert.deepEqual(observed, [[10, 0], [11, 1], [22, 2], [24, 1], [25, 0]]);
  assert.equal(first.reject("late"), false);
  stop();
});

test("rejection removes only its mutation and replays other mutations in submission order", () => {
  const state = signal(1), action = createOptimisticAction(state);
  const a = action.submit((n) => n + 2), b = action.submit((n) => n * 3), c = action.submit((n) => n - 1);
  b.reject("WOGE_ACTION_RESPONSE_REJECTED");
  assert.equal(state.get(), 2);
  assert.deepEqual(action.failure.get(), { id: b.id, code: "WOGE_ACTION_RESPONSE_REJECTED" });
  a.resolve(4);
  assert.equal(state.get(), 3);
  c.reject("network");
  assert.equal(state.get(), 4);
  assert.equal(action.pendingCount.get(), 0);
});

test("out-of-order newer success supersedes earlier intent; late outcomes cannot overwrite it", () => {
  const state = signal(0), action = createOptimisticAction(state);
  const a = action.submit((n) => n + 1), b = action.submit((n) => n + 2), c = action.submit((n) => n + 3);
  b.resolve(20);
  assert.equal(state.get(), 23);
  assert.equal(a.resolve(1), false);
  assert.equal(a.reject("late failure"), false);
  assert.equal(action.failure.get(), null);
  c.resolve(30);
  assert.equal(state.get(), 30);
});

test("stale/cancelled outcomes roll back without a user failure or POST replay", () => {
  const state = signal(0), action = createOptimisticAction(state);
  const a = action.submit((n) => n + 1), b = action.submit((n) => n + 10);
  a.stale();
  assert.equal(state.get(), 10);
  b.stale();
  assert.equal(state.get(), 0);
  assert.equal(action.failure.get(), null);
  assert.equal(b.stale(), false);
});

test("bad pure transitions leave queue, base and displayed state unchanged", () => {
  const state = signal(0), action = createOptimisticAction(state);
  const a = action.submit((n) => n + 1);
  assert.throws(() => action.submit(() => { throw new Error("bad apply"); }), /bad apply/);
  assert.equal(action.pendingCount.get(), 1);
  assert.equal(state.get(), 1);
  a.resolve(5);
  assert.equal(state.get(), 5);
});
