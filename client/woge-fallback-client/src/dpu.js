import { PageRegionRegistry } from "./dom.js";
import { PatchStreamDecoder, WogePatchError } from "./protocol.js";

const documents = new WeakMap();

/** Advertises this browser's current capability for future navigations, never by User-Agent. */
export function recordWogeDpuCapability(root = document) {
  const supported = "htmlFor" in root.defaultView.HTMLTemplateElement.prototype;
  const secure = root.defaultView.location.protocol === "https:" ? "; Secure" : "";
  root.cookie = `woge_dpu=${supported ? "1" : "0"}; Path=/; Max-Age=86400; SameSite=Lax${secure}`;
  return supported;
}

/**
 * Consumes only complete inert envelopes from the original document stream.
 * Native replacements never enter the detached fragment decoder. Each fallback patch snapshots the
 * current registry, since the native parser may have replaced sibling region wrappers meanwhile.
 */
export function installWogeDpuDocument(root = document) {
  const existing = documents.get(root);
  if (existing) return existing;
  const decoder = new PatchStreamDecoder();
  let stopped = false;
  const view = root.defaultView;
  const observer = new view.MutationObserver(drain);
  function dispose() {
    stopped = true;
    observer.disconnect();
    view.removeEventListener("pagehide", dispose);
    root.removeEventListener("DOMContentLoaded", finishDocument);
  }
  function problem(error) {
    dispose();
    root.dispatchEvent(new view.CustomEvent("woge:dpu-error", {
      detail: { code: error instanceof WogePatchError ? error.code : "WOGE_DPU_DOCUMENT_FAILED" },
    }));
  }
  function drain() {
    if (stopped) return;
    try {
      for (const ready of root.querySelectorAll("[data-woge-dpu-ready]")) {
        const frame = ready.previousElementSibling;
        if (frame?.localName !== "template" || !frame.hasAttribute("data-woge-dpu-frame")) {
          throw new WogePatchError("WOGE_DPU_ENVELOPE", "Missing initial-document frame");
        }
        const value = frame.content.textContent;
        if (value.length > 12 * 1024 * 1024 || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/u.test(value)) {
          throw new WogePatchError("WOGE_DPU_ENVELOPE", "Invalid initial-document frame");
        }
        const binary = view.atob(value);
        const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
        for (let offset = 0; offset < bytes.length; offset += 65536) {
          for (const event of decoder.push(bytes.subarray(offset, offset + 65536))) {
            if (event.type === "patch") new PageRegionRegistry(root).applyPatch(event.patch);
            if (event.type === "error") throw new WogePatchError("WOGE_DPU_STREAM_FAILED", "Document stream failed");
          }
        }
        const terminal = frame.getAttribute("data-woge-dpu-frame") === "end";
        frame.remove();
        ready.remove();
        if (terminal) {
          decoder.finish();
          dispose();
          root.dispatchEvent(new view.CustomEvent("woge:dpu-complete"));
          return;
        }
      }
    } catch (error) {
      problem(error);
    }
  }
  function finishDocument() {
    drain();
    if (!stopped) problem(new WogePatchError("WOGE_DPU_TRUNCATED", "Document ended without a terminal frame"));
  }
  const installation = Object.freeze({ dispose });
  documents.set(root, installation);
  observer.observe(root, { childList: true, subtree: true });
  view.addEventListener("pagehide", dispose, { once: true });
  root.addEventListener("DOMContentLoaded", finishDocument, { once: true });
  drain();
  if (root.readyState !== "loading") queueMicrotask(finishDocument);
  return installation;
}

if (typeof document !== "undefined") {
  recordWogeDpuCapability(document);
  installWogeDpuDocument(document);
}
