import { AFTER_REPLACE_EVENT, BEFORE_REPLACE_EVENT, PageRegionRegistry } from "./dom.js";
import {
  PatchStreamDecoder,
  WogePatchError,
  WogeRemotePatchError,
  fail,
} from "./protocol.js";

/** Owns one active document's region registry and applies validated patch streams to it. */
class WogePatchRuntime {
  #registry;
  #observer;
  #nextObservationId = 1;

  constructor(root = document, { observer } = {}) {
    if (observer !== undefined && typeof observer !== "function") {
      fail("WOGE_INVALID_OBSERVER", "Patch observer must be a function");
    }
    this.#registry = new PageRegionRegistry(root);
    this.#observer = observer;
  }

  async applyPatchStream(stream, { signal } = {}) {
    if (!stream || typeof stream.getReader !== "function") {
      fail("WOGE_INVALID_STREAM", "Patch input must be a readable byte stream");
    }
    if (signal?.aborted) fail("WOGE_CANCELLED", "Patch stream application was cancelled");

    const decoder = new PatchStreamDecoder();
    const reader = stream.getReader();
    const cancel = () => void reader.cancel(signal.reason).catch(() => {});
    signal?.addEventListener("abort", cancel, { once: true });
    let completion;

    try {
      while (true) {
        const { value, done } = await reader.read();
        if (signal?.aborted) fail("WOGE_CANCELLED", "Patch stream application was cancelled");
        if (done) break;
        for (const event of decoder.push(value)) {
          if (event.type === "patch") this.#applyObserved(event.patch);
          if (event.type === "complete") completion = Object.freeze({ patchCount: event.patchCount });
          if (event.type === "error") throw new WogeRemotePatchError(event.failure);
        }
      }
      decoder.finish();
      return completion;
    } catch (problem) {
      try {
        await reader.cancel(problem);
      } catch {
        // Preserve the original protocol, application, DOM, or cancellation failure.
      }
      throw problem;
    } finally {
      signal?.removeEventListener("abort", cancel);
      reader.releaseLock();
    }
  }

  #applyObserved(patch) {
    const observationId = this.#nextObservationId++;
    const context = Object.freeze({
      pageEpoch: patch.epoch,
      target: patch.target,
      patchId: patch.patchId,
    });
    const started = performance.now();
    this.#emit(Object.freeze({ observationId, phase: "started", operation: "patch.apply", context }));
    try {
      this.#registry.applyReplace(patch);
      this.#emitFinished(observationId, context, "succeeded", started);
    } catch (problem) {
      this.#emitFinished(observationId, context, patchOutcome(problem), started);
      throw problem;
    }
  }

  #emitFinished(observationId, context, outcome, started) {
    this.#emit(
      Object.freeze({
        observationId,
        phase: "finished",
        operation: "patch.apply",
        outcome,
        durationMs: Math.max(0, performance.now() - started),
        context,
      }),
    );
  }

  #emit(event) {
    try {
      this.#observer?.(event);
    } catch {
      // Observability is best effort and must never alter patch behavior.
    }
  }
}

/** Creates one runtime and active page-local region registry. */
export function createWogePatchRuntime(root = document, options = {}) {
  return new WogePatchRuntime(root, options);
}

function patchOutcome(problem) {
  if (problem?.code === "WOGE_CANCELLED") return "cancelled";
  if (problem?.code === "WOGE_STALE_PAGE_EPOCH") return "stale";
  if (typeof problem?.code === "string" && problem.code.startsWith("WOGE_")) return "rejected";
  return "failed";
}

export {
  AFTER_REPLACE_EVENT,
  BEFORE_REPLACE_EVENT,
  WogePatchError,
  WogeRemotePatchError,
};
