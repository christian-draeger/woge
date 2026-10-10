import { WogePatchError } from "./protocol.js";

const MAX_TARGETS_PER_EVENT = 128;

/**
 * Opens one EventSource and refreshes each region the server reports as changed, using the
 * application's own safe region GET. The stream carries ids only, never HTML.
 */
export function connectWogeLive(runtime, url, { load, onError, EventSource: Source = globalThis.EventSource } = {}) {
  if (typeof runtime?.refreshRegion !== "function" || typeof load !== "function" || typeof Source !== "function") {
    throw new WogePatchError("WOGE_INVALID_LIVE_CONFIGURATION", "Live updates need a runtime, a safe loader and EventSource");
  }
  const controller = new AbortController();
  // At most one running refresh plus one follow-up per region, however many events arrive.
  const running = new Map();
  const report = (problem) => {
    if (controller.signal.aborted) return;
    try {
      onError?.(problem);
    } catch {
      // Error reporting must not stop later refreshes.
    }
  };
  const refresh = (target) => {
    const state = running.get(target);
    if (state) {
      state.again = true;
      return;
    }
    const next = { again: false };
    running.set(target, next);
    void (async () => {
      do {
        next.again = false;
        try {
          await runtime.refreshRegion(target, load, { signal: controller.signal });
        } catch (problem) {
          report(problem);
        }
      } while (next.again && !controller.signal.aborted);
      running.delete(target);
    })();
  };
  const receive = (event) => {
    const targets = new Set(String(event.data).split("\n").filter(Boolean));
    if (targets.size > MAX_TARGETS_PER_EVENT) {
      report(new WogePatchError("WOGE_INVALID_LIVE_EVENT", "Live event lists too many regions"));
      return;
    }
    targets.forEach(refresh);
  };
  const source = new Source(url, { withCredentials: false });
  source.addEventListener("invalidate", receive);
  source.addEventListener("resync", receive);
  return Object.freeze({
    /** Refreshes one region now, sharing the same per-region ordering as server events. */
    refresh,
    close() {
      source.close();
      controller.abort();
    },
  });
}
