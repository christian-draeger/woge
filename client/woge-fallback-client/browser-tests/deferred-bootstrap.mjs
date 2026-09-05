import { createWogePatchRuntime } from "/woge-fallback.js";

const runId = crypto.randomUUID();
const timeline = {
  shellParsed: performance.now(),
  applied: [],
  completed: null,
};

globalThis.deferredRunId = runId;
globalThis.deferredTimeline = timeline;
globalThis.deferredStarted = false;

document.addEventListener("woge:after-replace", (event) => {
  timeline.applied.push({ target: event.target.dataset.wogeRegion, at: performance.now() });
});

void (async () => {
  try {
    const response = await fetch(`/deferred-patches?run=${encodeURIComponent(runId)}`);
    if (!response.ok || !response.body) throw new Error(`Deferred stream failed with HTTP ${response.status}`);
    globalThis.deferredStarted = true;
    globalThis.deferredCompletion = await createWogePatchRuntime(document).applyPatchStream(response.body);
    timeline.completed = performance.now();
  } catch (problem) {
    globalThis.deferredError = problem;
  }
})();
