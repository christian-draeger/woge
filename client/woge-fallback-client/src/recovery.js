/**
 * Canonical failure classification from ADR 0047. Every failure maps to exactly one bounded outcome;
 * the result carries only stable codes, never request data, HTML or exception messages.
 */

const PROTOCOL_CODES = new Set([
  "WOGE_INVALID_CHUNK",
  "WOGE_INVALID_STREAM",
  "WOGE_INVALID_PREAMBLE",
  "WOGE_INVALID_LENGTH",
  "WOGE_UNKNOWN_FRAME_KIND",
  "WOGE_INVALID_CONTENT_TYPE",
  "WOGE_INVALID_UTF8",
  "WOGE_INVALID_METADATA",
  "WOGE_INVALID_SEQUENCE",
  "WOGE_ACTION_RESPONSE_REJECTED",
  "WOGE_BYTES_AFTER_TERMINAL",
]);
const TRANSPORT_CODES = new Set(["WOGE_TRUNCATED_STREAM", "WOGE_MISSING_TERMINAL"]);
const EXHAUSTION_CODES = new Set(["WOGE_METADATA_TOO_LARGE", "WOGE_PAYLOAD_TOO_LARGE"]);
const BROWSER_APPLY_CODES = new Set([
  "WOGE_ACTIVE_CONTENT",
  "WOGE_INVALID_DOCUMENT",
  "WOGE_INVALID_PAGE_EPOCH",
  "WOGE_INVALID_TARGET",
  "WOGE_INVALID_REGION_STATE",
  "WOGE_DUPLICATE_TARGET",
  "WOGE_REGION_STATE_CHANGED",
  "WOGE_TARGET_CHANGED",
  "WOGE_REGION_REGISTRY_CHANGED",
  "WOGE_INVALID_OBSERVER",
  "WOGE_INVALID_ACTION_CONFIGURATION",
  "WOGE_INVALID_ITEM",
  "WOGE_ITEM_CHANGED",
  "WOGE_INVALID_BROWSER_STATE",
  "WOGE_BROWSER_STATE_CONFLICT",
  "WOGE_INVALID_INTERACTION",
  "WOGE_INVALID_RESYNC",
  "WOGE_RESYNC_EXHAUSTED",
]);
const LOCAL_OUTCOMES = new Map([
  ["WOGE_UNSAFE_ACTION_NAVIGATION", ["security", "fail-closed"]],
  ["WOGE_UNSUPPORTED_VERSION", ["incompatible-client", "reload-page"]],
  ["WOGE_STALE_PAGE_EPOCH", ["stale", "reload-page"]],
  ["WOGE_INTERACTION_MISMATCH", ["stale", "ignore-stale"]],
  ["WOGE_STALE_PATCH", ["stale", "ignore-stale"]],
  ["WOGE_INTERACTION_EXHAUSTED", ["stale", "reload-page"]],
  ["WOGE_CANCELLED", ["cancelled", "ignore-stale"]],
  ["WOGE_REVISION_MISMATCH", ["stale", "refetch-region"]],
  ["WOGE_UNKNOWN_TARGET", ["stale", "refetch-region"]],
]);

/**
 * Classifies a thrown failure or a non-success `Response`-like `{ status }` value.
 * Only `safeRequest: true` (an idempotent GET without side effects) may produce `retry-safe`.
 */
export function classifyWogeFailure(problem, { safeRequest = false } = {}) {
  if (typeof problem?.status === "number" && !(problem instanceof Error)) {
    return classifyHttpStatus(problem.status);
  }
  if (problem?.name === "AbortError") return result("WOGE_CANCELLED", "cancelled", "ignore-stale");
  if (problem?.name === "WogeRemotePatchError") {
    return result(problem.code, "rendering", problem.recovery === "reload" ? "reload-page" : "fail-closed");
  }
  if (problem?.name === "TypeError") {
    return result("WOGE_NETWORK", "transport", safeRequest ? "retry-safe" : "fail-closed");
  }
  return classifyLocalCode(problem?.name === "WogePatchError" ? problem.code : undefined);
}

/**
 * Bounds automatic recovery: at most one reload per page epoch and tab, and at most one retry per
 * request key. Recovery therefore cannot loop.
 */
export function createWogeRecoveryBudget({ storage = globalThis.sessionStorage } = {}) {
  const retried = new Set();
  return Object.freeze({
    tryReload(pageEpoch) {
      const key = `woge:reloaded:${pageEpoch}`;
      try {
        if (!storage || storage.getItem(key) !== null) return false;
        storage.setItem(key, "1");
        return true;
      } catch {
        return false;
      }
    },
    tryRetry(requestKey) {
      if (retried.has(requestKey)) return false;
      retried.add(requestKey);
      return true;
    },
  });
}

function classifyLocalCode(code) {
  if (typeof code !== "string") return result("WOGE_UNKNOWN", "unknown", "fail-closed");
  if (PROTOCOL_CODES.has(code)) return result(code, "protocol", "fail-closed");
  if (TRANSPORT_CODES.has(code)) return result(code, "transport", "fail-closed");
  if (EXHAUSTION_CODES.has(code)) return result(code, "resource-exhaustion", "fail-closed");
  if (BROWSER_APPLY_CODES.has(code)) return result(code, "browser-apply", "fail-closed");
  const [category, outcome] = LOCAL_OUTCOMES.get(code) ?? ["unknown", "fail-closed"];
  return result(code, category, outcome);
}

function classifyHttpStatus(status) {
  const code = `WOGE_HTTP_${status}`;
  if (status === 400 || status === 422) return result(code, "request-decoding", "error-response");
  if (status === 401 || status === 403) return result(code, "security", "error-response");
  if (status === 409 || status === 412) return result(code, "domain-conflict", "error-response");
  if (status === 413 || status === 429 || status === 503) return result(code, "resource-exhaustion", "fail-closed");
  return result(code, "rendering", "error-response");
}

function result(code, category, outcome) {
  return Object.freeze({ code, category, outcome });
}
