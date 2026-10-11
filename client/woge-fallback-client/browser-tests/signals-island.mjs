import { signal, effect, createOptimisticAction } from "/signals.js";
import { classifyWogeFailure, WogePatchError } from "/woge-fallback.js";

export function mount(root, props, { signal: lifetime }) {
  const count = signal(Number(props.count));
  const action = createOptimisticAction(count);
  const form = root.querySelector("form");
  effect(() => {
    root.querySelector('[role="status"]').textContent = `${count.get()} notes (${action.pendingCount.get()} pending)`;
    root.querySelector('[role="alert"]').textContent = action.failure.get()?.code ?? "";
  }, { signal: lifetime });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const mutation = action.submit((n) => n + 1);
    const intent = runtime.beginInteraction(["saved"]);
    try {
      const response = await fetch(form.action, {
        method: "POST", body: new URLSearchParams(new FormData(form)), signal: lifetime,
        credentials: "same-origin", redirect: "error",
        headers: { Accept: "application/vnd.woge.patch-stream; version=1",
          "Woge-Page-Epoch": intent.pageEpoch, "Woge-Interaction": intent.interactionSequence,
          "Woge-Region-Revisions": `saved:${intent.targets[0].baseRevision}` },
      });
      const validation = response.status === 400 && response.headers.get("Woge-Validation") === "errors";
      if ((!response.ok && !validation) || response.headers.get("Content-Type") !==
          "application/vnd.woge.patch-stream; version=1") {
        await response.body?.cancel();
        throw new WogePatchError("WOGE_ACTION_RESPONSE_REJECTED", "Action response rejected");
      }
      const completion = await runtime.applyPatchStream(response.body, { signal: lifetime });
      if (lifetime.aborted || completion.stalePatchCount) mutation.stale();
      else if (validation) mutation.reject("NOTE_REJECTED");
      else {
        const field = document.querySelector("[data-authoritative-count]")?.dataset.authoritativeCount;
        const authoritative = Number(field);
        if (field === undefined || !Number.isSafeInteger(authoritative) || authoritative < 0) {
          throw new WogePatchError("WOGE_ACTION_RESPONSE_REJECTED", "Invalid authoritative count");
        }
        mutation.resolve(authoritative);
      }
    } catch (problem) {
      const failure = classifyWogeFailure(problem);
      if (lifetime.aborted || failure.outcome === "ignore-stale") mutation.stale();
      else mutation.reject(failure.code);
    } finally {
      root.dataset.settled = String(Number(root.dataset.settled ?? 0) + 1);
    }
  }, { signal: lifetime });
  root.dataset.ready = "true";
}
