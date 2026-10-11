import {
  AFTER_REPLACE_EVENT, BEFORE_REPLACE_EVENT, AFTER_APPEND_EVENT, BEFORE_APPEND_EVENT,
  AFTER_REMOVE_EVENT, BEFORE_REMOVE_EVENT, PageRegionRegistry,
} from "./dom.js";
import {
  PATCH_STREAM_MEDIA_TYPE,
  PatchStreamDecoder,
  WogePatchError,
  WogeRemotePatchError,
  fail,
  patchStreamLimits,
  exceedBudget,
} from "./protocol.js";
import { classifyWogeFailure, createWogeRecoveryBudget, reloadForIncompatibleVersion } from "./recovery.js";
import { WOGE_PATCH_PROTOCOL_VERSION } from "./version.js";
import { installWogeActionForms, ACTION_ERROR_EVENT } from "./actions.js";
import { captureWogeBrowserState, prepareWogeBrowserState } from "./state.js";
import { connectWogeLive } from "./live.js";

/** Owns one active document's region registry and applies validated patch streams to it. */
class WogePatchRuntime {
  #registry;
  #root;
  #observer;
  #nextObservationId = 1;
  #refetched = new Map();
  #limits;
  #activeStreams = 0;

  constructor(root = document, { observer, limits } = {}) {
    this.#root = root;
    if (observer !== undefined && typeof observer !== "function") {
      fail("WOGE_INVALID_OBSERVER", "Patch observer must be a function");
    }
    this.#registry = new PageRegionRegistry(root);
    this.#observer = observer;
    this.#limits = patchStreamLimits(limits);
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
    return this.#replaceFrom(target, load, signal);
  }

  async refreshRegion(target, load, { signal } = {}) {
    if (typeof load !== "function") fail("WOGE_INVALID_INTERACTION", "Region refresh requires an explicit safe loader");
    if (signal?.aborted) fail("WOGE_CANCELLED", "Region refresh was cancelled");
    return this.#replaceFrom(target, load, signal);
  }

  async #replaceFrom(target, load, signal) {
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

    const decoder = new PatchStreamDecoder(this.#limits);
    const reader = stream.getReader();
    const cancel = () => void reader.cancel(signal.reason).catch(() => {});
    signal?.addEventListener("abort", cancel, { once: true });
    let completion;
    let recoveryPatches = 0;
    let stalePatchCount = 0;
    let admitted = false;

    try {
      if (this.#activeStreams === this.#limits.maxConcurrentStreams) {
        exceedBudget("CONCURRENT_PATCH_STREAMS", this.#limits.maxConcurrentStreams);
      }
      this.#activeStreams++;
      admitted = true;
      while (true) {
        const { value, done } = await reader.read();
        if (signal?.aborted) fail("WOGE_CANCELLED", "Patch stream application was cancelled");
        if (done) break;
        for (const event of decodeTransportChunk(decoder, value)) {
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
            if (await this.#applyObserved(event.patch, signal) === "stale") stalePatchCount++;
          }
          if (event.type === "complete") completion = Object.freeze({ patchCount: event.patchCount });
          if (event.type === "error") throw new WogeRemotePatchError(event.failure);
        }
      }
      decoder.finish();
      if (recovery && recoveryPatches !== 1) fail("WOGE_INVALID_RESYNC", "Region recovery must contain one replacement");
      return stalePatchCount ? Object.freeze({ ...completion, stalePatchCount }) : completion;
    } catch (problem) {
      if (problem?.code === "WOGE_UNSUPPORTED_VERSION") reloadForIncompatibleVersion(this.#root);
      try {
        await reader.cancel(problem);
      } catch {
        // Preserve the original protocol, application, DOM, or cancellation failure.
      }
      throw problem;
    } finally {
      if (admitted) this.#activeStreams--;
      signal?.removeEventListener("abort", cancel);
      reader.releaseLock();
    }
  }

  async #applyObserved(patch, signal) {
    const observationId = this.#nextObservationId++;
    const context = Object.freeze({
      pageEpoch: patch.epoch,
      target: patch.target,
      patchId: patch.patchId,
    });
    const started = performance.now();
    this.#emit(Object.freeze({ observationId, phase: "started", operation: "patch.apply", context }));
    try {
      const outcome = patch.operation === "transaction" ?
        await (await import("./transactions.js").catch(() =>
          fail("WOGE_TRANSACTION_APPLY_FAILED", "Transaction module unavailable")))
          .applyTransaction(this.#registry, patch, this.#root, signal) :
        this.#registry.applyPatch(patch);
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

function* decodeTransportChunk(decoder, value) {
  if (!(value instanceof Uint8Array)) fail("WOGE_INVALID_CHUNK", "Patch stream chunks must be Uint8Array values");
  // One fixed byte slice limits each decode/apply batch even if Fetch supplies a very large chunk.
  const sliceBytes = 64 * 1024;
  for (let offset = 0; offset < value.byteLength; offset += sliceBytes) {
    yield* decoder.push(value.subarray(offset, offset + sliceBytes));
  }
}

const managedRuntimes = new WeakMap();

/** Creates a runtime; managed documents share their one active region registry. */
export function createWogePatchRuntime(root = document, options = {}) {
  let runtime = managedRuntimes.get(root);
  if (!runtime) {
    runtime = new WogePatchRuntime(root, options);
    if (root.querySelector?.('meta[name="woge-render-session"]')) managedRuntimes.set(root, runtime);
  }
  return runtime;
}

function patchOutcome(problem) {
  if (problem?.code === "WOGE_CANCELLED") return "cancelled";
  if (problem?.code === "WOGE_STALE_PAGE_EPOCH") return "stale";
  if (typeof problem?.code === "string" && problem.code.startsWith("WOGE_")) return "rejected";
  return "failed";
}

export {
  connectWogeLive,
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

// Managed documents use the shipped module directly, without an application bootstrap script.
if (typeof document !== "undefined") {
  const start = () => {
    const root = document;
    const managed = root.querySelector('meta[name="woge-render-session"]');
    const region = root.querySelector("[data-woge-live-url]");
    if (!region && !managed) return;
    const runtime = createWogePatchRuntime(root);
    const actions = managed && installWogeActionForms(root, runtime);
    let live;
    if (region) {
      const page = new URL(region.getAttribute("data-woge-live-url"), root.baseURI);
      if (page.origin !== root.defaultView.location.origin) return;
      const stream = new URL(page);
      stream.searchParams.set("_woge_live", region.getAttribute("data-woge-live-epoch"));
      live = connectWogeLive(runtime, stream.href, {
        async load(state, { signal }) {
          const url = new URL(page);
          const values = { refresh: "1", epoch: state.pageEpoch, target: state.targets[0].target,
            revision: state.targets[0].baseRevision, interaction: state.interactionSequence };
          for (const name in values) url.searchParams.set(`_woge_${name}`, values[name]);
          const response = await fetch(url, { headers: { Accept: PATCH_STREAM_MEDIA_TYPE },
            redirect: "error", signal });
          if (!response.ok) throw new WogePatchError("WOGE_LIVE_CONNECTION", "Live region refresh failed");
          return response.body;
        },
      });
    }
    root.defaultView.addEventListener("pagehide", () => { live?.close(); if (actions) actions.dispose(); }, { once: true });
  };
  start();
}
