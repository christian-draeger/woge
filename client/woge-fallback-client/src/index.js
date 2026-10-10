import {
  AFTER_REPLACE_EVENT, BEFORE_REPLACE_EVENT, AFTER_APPEND_EVENT, BEFORE_APPEND_EVENT,
  AFTER_REMOVE_EVENT, BEFORE_REMOVE_EVENT, PageRegionRegistry,
} from "./dom.js";
import {
  PatchStreamDecoder,
  WogePatchError,
  WogeRemotePatchError,
  fail,
} from "./protocol.js";
import { classifyWogeFailure, createWogeRecoveryBudget } from "./recovery.js";
import { WOGE_PATCH_PROTOCOL_VERSION } from "./version.js";
import { installWogeActionForms, ACTION_ERROR_EVENT } from "./actions.js";
import { captureWogeBrowserState, prepareWogeBrowserState } from "./state.js";

/** Owns one active document's region registry and applies validated patch streams to it. */
class WogePatchRuntime {
  #registry;
  #observer;
  #nextObservationId = 1;
  #refetched = new Map();

  constructor(root = document, { observer } = {}) {
    if (observer !== undefined && typeof observer !== "function") {
      fail("WOGE_INVALID_OBSERVER", "Patch observer must be a function");
    }
    this.#registry = new PageRegionRegistry(root);
    this.#observer = observer;
  }

  beginInteraction(targets) {
    return this.#registry.beginInteraction(targets);
  }

  async refetchRegion(target, load, { signal } = {}) {
    if (typeof load !== "function") fail("WOGE_INVALID_INTERACTION", "Region recovery requires an explicit safe loader");
    if (signal?.aborted) fail("WOGE_CANCELLED", "Region recovery was cancelled");
    const context = this.#registry.interactionContext(target);
    const revision = context.targets[0].baseRevision;
    if (this.#refetched.get(target) === revision ||
        !this.#refetched.has(target) && this.#refetched.size >= 128) {
      fail("WOGE_RESYNC_EXHAUSTED", "Region recovery budget is exhausted");
    }
    this.#refetched.set(target, revision);
    const interaction = this.beginInteraction([target]);
    const stream = await load(interaction, { signal });
    return this.#applyStream(stream, { signal }, interaction);
  }

  async applyPatchStream(stream, { signal } = {}) {
    return this.#applyStream(stream, { signal });
  }

  async #applyStream(stream, { signal }, recovery) {
    if (!stream || typeof stream.getReader !== "function") {
      fail("WOGE_INVALID_STREAM", "Patch input must be a readable byte stream");
    }
    if (signal?.aborted) fail("WOGE_CANCELLED", "Patch stream application was cancelled");

    const decoder = new PatchStreamDecoder();
    const reader = stream.getReader();
    const cancel = () => void reader.cancel(signal.reason).catch(() => {});
    signal?.addEventListener("abort", cancel, { once: true });
    let completion;
    let recoveryPatches = 0;
    let stalePatchCount = 0;

    try {
      while (true) {
        const { value, done } = await reader.read();
        if (signal?.aborted) fail("WOGE_CANCELLED", "Patch stream application was cancelled");
        if (done) break;
        for (const event of decoder.push(value)) {
          if (event.type === "patch") {
            if (recovery) {
              const patch = event.patch;
              const target = recovery.targets[0];
              if (++recoveryPatches !== 1 || patch.operation !== "replace" ||
                  patch.target !== target.target || patch.epoch !== recovery.pageEpoch ||
                  patch.interactionSequence.toString() !== recovery.interactionSequence ||
                  patch.baseRevision.toString() !== target.baseRevision) {
                fail("WOGE_INVALID_RESYNC", "Region recovery must replace its declared target and context");
              }
            }
            if (this.#applyObserved(event.patch) === "stale") stalePatchCount++;
          }
          if (event.type === "complete") completion = Object.freeze({ patchCount: event.patchCount });
          if (event.type === "error") throw new WogeRemotePatchError(event.failure);
        }
      }
      decoder.finish();
      if (recovery && recoveryPatches !== 1) fail("WOGE_INVALID_RESYNC", "Region recovery must contain one replacement");
      return stalePatchCount ? Object.freeze({ ...completion, stalePatchCount }) : completion;
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
      const outcome = this.#registry.applyPatch(patch);
      this.#emitFinished(observationId, context, outcome === "stale" ? "stale" : "succeeded", started,
        outcome === "stale" ? "WOGE_STALE_PATCH" : undefined);
      return outcome;
    } catch (problem) {
      this.#emitFinished(observationId, context, patchOutcome(problem), started);
      throw problem;
    }
  }

  #emitFinished(observationId, context, outcome, started, code) {
    this.#emit(
      Object.freeze({
        observationId,
        phase: "finished",
        operation: "patch.apply",
        outcome,
        durationMs: Math.max(0, performance.now() - started),
        context,
        ...(code ? { code } : {}),
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
  captureWogeBrowserState,
  prepareWogeBrowserState,
  ACTION_ERROR_EVENT,
  installWogeActionForms,
  AFTER_REPLACE_EVENT,
  BEFORE_REPLACE_EVENT,
  AFTER_APPEND_EVENT,
  BEFORE_APPEND_EVENT,
  AFTER_REMOVE_EVENT,
  BEFORE_REMOVE_EVENT,
  classifyWogeFailure,
  createWogeRecoveryBudget,
  WogePatchError,
  WogeRemotePatchError,
  WOGE_PATCH_PROTOCOL_VERSION,
};
