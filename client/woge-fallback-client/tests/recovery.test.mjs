import assert from "node:assert/strict";
import test from "node:test";
import { PatchStreamDecoder, WogePatchError, WogeRemotePatchError } from "../src/protocol.js";
import { classifyWogeFailure, createWogeRecoveryBudget } from "../src/recovery.js";
import { encodeStream, patchFrame, rawFrame } from "../test-support/protocol-fixture.mjs";

function decodeFailure(bytes) {
  try {
    const decoder = new PatchStreamDecoder();
    decoder.push(bytes);
    decoder.finish();
  } catch (problem) {
    return problem;
  }
  assert.fail("Expected the stream to be rejected");
}

test("malformed streams fail closed", () => {
  const problem = decodeFailure(Uint8Array.of(1, 2, 3, 4, 5));
  assert.deepEqual(classifyWogeFailure(problem), {
    code: "WOGE_INVALID_PREAMBLE",
    category: "protocol",
    outcome: "fail-closed",
  });
});

test("a disconnected stream fails closed even for a safe request", () => {
  const complete = encodeStream([patchFrame()]);
  const problem = decodeFailure(complete.slice(0, complete.byteLength - 3));
  const classification = classifyWogeFailure(problem, { safeRequest: true });
  assert.equal(classification.category, "transport");
  assert.equal(classification.outcome, "fail-closed");
});

test("a network failure is retried only for a safe request", () => {
  const problem = new TypeError("Failed to fetch");
  assert.equal(classifyWogeFailure(problem, { safeRequest: true }).outcome, "retry-safe");
  assert.equal(classifyWogeFailure(problem).outcome, "fail-closed");
  assert.equal(classifyWogeFailure(problem, { safeRequest: false }).outcome, "fail-closed");
});

test("version skew reloads the page", () => {
  assert.deepEqual(classifyWogeFailure(new WogePatchError("WOGE_UNSUPPORTED_VERSION", "x")), {
    code: "WOGE_UNSUPPORTED_VERSION",
    category: "incompatible-client",
    outcome: "reload-page",
  });
  assert.equal(classifyWogeFailure(new WogePatchError("WOGE_STALE_PAGE_EPOCH", "x")).outcome, "reload-page");
});

test("stale and missing targets never apply out of order", () => {
  assert.equal(classifyWogeFailure(new WogePatchError("WOGE_INTERACTION_MISMATCH", "x")).outcome, "ignore-stale");
  assert.equal(classifyWogeFailure(new WogePatchError("WOGE_REVISION_MISMATCH", "x")).outcome, "refetch-region");
  assert.equal(classifyWogeFailure(new WogePatchError("WOGE_UNKNOWN_TARGET", "x")).outcome, "refetch-region");
});

test("cancellation is ignored as superseded work", () => {
  const abort = new DOMException("aborted", "AbortError");
  assert.equal(classifyWogeFailure(abort).outcome, "ignore-stale");
  assert.equal(classifyWogeFailure(new WogePatchError("WOGE_CANCELLED", "x")).outcome, "ignore-stale");
});

test("action response and navigation failures fail closed with stable categories", () => {
  assert.equal(classifyWogeFailure(new WogePatchError("WOGE_ACTION_RESPONSE_REJECTED", "x")).category, "protocol");
  assert.deepEqual(classifyWogeFailure(new WogePatchError("WOGE_UNSAFE_ACTION_NAVIGATION", "x")), {
    code: "WOGE_UNSAFE_ACTION_NAVIGATION", category: "security", outcome: "fail-closed",
  });
});

test("oversized frames are resource exhaustion", () => {
  const problem = decodeFailure(encodeStream([rawFrame(1, "text/html; charset=utf-8", "x".repeat(64 * 1024 + 1), "")]));
  assert.equal(classifyWogeFailure(problem).category, "resource-exhaustion");
  assert.equal(classifyWogeFailure(problem).outcome, "fail-closed");
});

test("browser apply failures fail closed", () => {
  for (const code of [
    "WOGE_ACTIVE_CONTENT", "WOGE_TARGET_CHANGED", "WOGE_INVALID_DOCUMENT", "WOGE_INVALID_ITEM", "WOGE_ITEM_CHANGED",
  ]) {
    assert.deepEqual(classifyWogeFailure(new WogePatchError(code, "x")), {
      code,
      category: "browser-apply",
      outcome: "fail-closed",
    });
  }
});

test("remote failures follow only the recovery the server allowed", () => {
  const reload = new WogeRemotePatchError({ code: "WOGE_RENDER", correlationId: "c-1", recovery: "reload" });
  const none = new WogeRemotePatchError({ code: "WOGE_RENDER", correlationId: "c-1", recovery: "none" });
  assert.equal(classifyWogeFailure(reload).outcome, "reload-page");
  assert.equal(classifyWogeFailure(none).outcome, "fail-closed");
});

test("HTTP status codes map to one outcome and are never retried", () => {
  const outcomes = Object.fromEntries(
    [400, 401, 403, 404, 409, 413, 422, 429, 500, 503].map((status) => [
      status,
      classifyWogeFailure({ status }, { safeRequest: true }).outcome,
    ]),
  );
  assert.deepEqual(outcomes, {
    400: "error-response",
    401: "error-response",
    403: "error-response",
    404: "error-response",
    409: "error-response",
    413: "fail-closed",
    422: "error-response",
    429: "fail-closed",
    500: "error-response",
    503: "fail-closed",
  });
});

test("unknown problems fail closed without echoing their message", () => {
  const classification = classifyWogeFailure(new Error("secret token=abc"));
  assert.deepEqual(classification, { code: "WOGE_UNKNOWN", category: "unknown", outcome: "fail-closed" });
  assert.ok(Object.isFrozen(classification));
});

test("the recovery budget reloads once per epoch and retries once per request", () => {
  const values = new Map();
  const storage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  const budget = createWogeRecoveryBudget({ storage });
  assert.equal(budget.tryReload("epoch-a"), true);
  assert.equal(budget.tryReload("epoch-a"), false);
  assert.equal(createWogeRecoveryBudget({ storage }).tryReload("epoch-a"), false);
  assert.equal(budget.tryReload("epoch-b"), true);
  assert.equal(budget.tryRetry("/patches"), true);
  assert.equal(budget.tryRetry("/patches"), false);
});

test("without storage the budget never reloads", () => {
  assert.equal(createWogeRecoveryBudget({ storage: null }).tryReload("epoch"), false);
  const throwing = { getItem: () => { throw new Error("denied"); }, setItem: () => {} };
  assert.equal(createWogeRecoveryBudget({ storage: throwing }).tryReload("epoch"), false);
});
