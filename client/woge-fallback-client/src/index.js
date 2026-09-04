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

  constructor(root = document) {
    this.#registry = new PageRegionRegistry(root);
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
          if (event.type === "patch") this.#registry.applyReplace(event.patch);
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
}

/** Creates one runtime and active page-local region registry. */
export function createWogePatchRuntime(root = document) {
  return new WogePatchRuntime(root);
}

export {
  AFTER_REPLACE_EVENT,
  BEFORE_REPLACE_EVENT,
  WogePatchError,
  WogeRemotePatchError,
};
