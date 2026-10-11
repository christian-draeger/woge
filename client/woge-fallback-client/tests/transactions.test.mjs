import assert from "node:assert/strict";
import test from "node:test";
import { applyTransaction, decodeTransaction, withViewTransition } from "../src/transactions.js";
import { PatchStreamDecoder } from "../src/protocol.js";
import { classifyWogeFailure } from "../src/recovery.js";
import { completeFrame, encodeStream, patchFrame, rawFrame, readGoldenStream, transactionFrame }
  from "../test-support/protocol-fixture.mjs";

const members = () => [patchFrame({ target: "alpha" }), patchFrame({ target: "beta" })];
function envelope(frames = members(), motion = false) {
  return new PatchStreamDecoder().push(encodeStream([transactionFrame(frames, motion), completeFrame()]))[0].patch;
}
function registry(log, failure) {
  return { preparePatch(patch) {
    if (patch.target === failure) throw new Error("invalid target");
    return { target: { contains: () => false }, change: { addedEntries: [] },
      ...Object.fromEntries(["before", "check", "commit", "focus", "after"].map((phase) =>
        [phase, (prepare = true) => log.push(`${phase}${prepare ? "" : "-verify"}:${patch.target}`)])) };
  } };
}

test("one transaction frame buffers every member at arbitrary transport boundaries", async () => {
  const bytes = await readGoldenStream("transaction-patch-stream-v1");
  for (let split = 0; split <= bytes.length; split++) {
    const decoder = new PatchStreamDecoder();
    const events = [...decoder.push(bytes.slice(0, split)), ...decoder.push(bytes.slice(split))];
    decoder.finish();
    assert.equal(events.length, 2);
    assert.equal(events[1].patchCount, 1);
    assert.deepEqual(decodeTransaction(events[0].patch).members.map((patch) => patch.target), ["alpha", "beta"]);
  }
});

test("invalid, duplicate, nested, nonreplace and incomplete membership never applies", async () => {
  for (const frame of [
    transactionFrame([]), transactionFrame([patchFrame(), patchFrame()]),
    transactionFrame([transactionFrame(members())]),
    transactionFrame([patchFrame({ operation: "append", itemId: "one" })]),
    rawFrame(4, "application/json; charset=utf-8", '{"viewTransition":false}', encodeStream(members())),
    rawFrame(4, "application/json; charset=utf-8", '{ "viewTransition":false}', encodeStream([...members(), completeFrame(2)])),
  ]) {
    const patch = new PatchStreamDecoder().push(encodeStream([frame, completeFrame()]))[0].patch;
    const log = [];
    await assert.rejects(applyTransaction(registry(log), patch, {}));
    assert.deepEqual(log, []);
  }
  const log = [];
  await assert.rejects(applyTransaction(registry(log, "beta"), envelope(), {}), { code: "WOGE_TRANSACTION_REJECTED" });
  assert.deepEqual(log, []);
});

test("all checks precede commits and all commits precede focus and after lifecycle", async () => {
  const log = [];
  await applyTransaction(registry(log), envelope(), {});
  assert.deepEqual(log, ["before:alpha", "before:beta", "check:alpha", "check:beta", "check-verify:alpha", "commit:alpha", "check-verify:beta", "commit:beta",
    "focus:alpha", "focus:beta", "after:alpha", "after:beta", "check-verify:alpha", "check-verify:beta"]);
});

test("post-commit failures are explicit browser apply failures, not successful partial results", async () => {
  const fake = registry([]);
  const prepare = fake.preparePatch;
  fake.preparePatch = (patch) => {
    const plan = prepare(patch);
    if (patch.target === "beta") plan.commit = () => { throw new Error("native failure"); };
    return plan;
  };
  await assert.rejects(applyTransaction(fake, envelope(), {}), (problem) => {
    assert.deepEqual(classifyWogeFailure(problem), { code: "WOGE_TRANSACTION_APPLY_FAILED",
      category: "browser-apply", outcome: "fail-closed" });
    return true;
  });
});

test("motion is opt-in and capability detected, reduced motion and unsupported browsers apply directly", async () => {
  for (const [enabled, available, reduced] of [[false, true, false], [true, false, false], [true, true, true]]) {
    let count = 0;
    const document = { defaultView: { matchMedia: () => ({ matches: reduced }) },
      ...(available ? { startViewTransition: () => { throw new Error("must not run"); } } : {}) };
    assert.equal(await withViewTransition(document, enabled, () => ++count), 1);
    assert.equal(count, 1);
  }
});

test("animation rejection never replays mutation or waits for finished; callback failures remain failures", async () => {
  let count = 0;
  const document = { startViewTransition(update) {
    const done = Promise.resolve().then(update);
    return { updateCallbackDone: done, ready: Promise.reject(new Error("skipped")), finished: new Promise(() => {}) };
  } };
  assert.equal(await withViewTransition(document, true, () => ++count), 1);
  assert.equal(count, 1);
  await assert.rejects(withViewTransition(document, true, () => { throw new Error("apply failed"); }), /apply failed/);
  assert.equal(await withViewTransition({ startViewTransition() { throw new Error("unsupported"); } }, true,
    () => ++count), 2);
});

test("transaction member count remains bounded and pre-callback cancellation does not apply", async () => {
  const frames = Array.from({ length: 129 }, (_, index) => patchFrame({ target: `target-${index}` }));
  assert.throws(() => decodeTransaction(envelope(frames)), { code: "WOGE_RESOURCE_LIMIT_EXCEEDED" });
  const controller = new AbortController();
  controller.abort();
  const log = [];
  await assert.rejects(applyTransaction(registry(log), envelope(), {}, controller.signal), { code: "WOGE_CANCELLED" });
  assert.deepEqual(log, []);
});
