import { createWogePatchRuntime } from "/assets/woge/index.js";

const abort = new AbortController();
addEventListener("pagehide", () => abort.abort(), { once: true });

try {
    const response = await fetch(document.body.dataset.wogePatchUrl, {
        headers: { Accept: "application/vnd.woge.patch-stream; version=1" },
        redirect: "error",
        signal: abort.signal,
    });
    if (!response.ok || !response.body) throw new Error("The Todo summary stream was unavailable.");
    await createWogePatchRuntime(document).applyPatchStream(response.body, { signal: abort.signal });
} catch (error) {
    if (!abort.signal.aborted) {
        document.querySelector("#todo-summary p").textContent = "Todo summary unavailable.";
    }
}
