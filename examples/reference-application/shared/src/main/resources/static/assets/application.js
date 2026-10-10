import {
  AFTER_REPLACE_EVENT,
  classifyWogeFailure,
  connectWogeLive,
  createWogePatchRuntime,
  createWogeRecoveryBudget,
  installWogeActionForms,
  WOGE_PATCH_PROTOCOL_VERSION,
} from "./woge/index.js";
import { installWogeDialogs } from "./woge-ui/dialog.js";

installWogeDialogs(document);

const page = document.querySelector("[data-woge-patch-url]");
const budget = createWogeRecoveryBudget();
let runtime;

if (page) {
  void loadDeferredRegions(page.dataset.wogePatchUrl);
} else if (document.querySelector("[data-woge-action]")) {
  runtime = createWogePatchRuntime(document);
  installWogeActionForms(document, runtime);
  connectBoardActivity(runtime);
}

// Live updates are optional: without EventSource or the stream, the board works as before.
function connectBoardActivity(runtime) {
  const url = document.querySelector('meta[name="woge-live-url"]')?.content;
  const notice = document.getElementById("board-activity")?.closest("[data-woge-region]");
  if (!url || !notice || typeof EventSource !== "function") return;
  const shownVersion = () => document.querySelector('input[name="version"][form="board-form"]').value;
  const live = connectWogeLive(runtime, url, {
    load: async (context, { signal }) => {
      const parts = [context.pageEpoch, notice.dataset.wogeRegion, context.targets[0].baseRevision,
        context.interactionSequence];
      const response = await fetch(
        `/projects/woge/tasks/activity/${parts.map(encodeURIComponent).join("/")}?since=${shownVersion()}`,
        { headers: { Accept: `application/vnd.woge.patch-stream; version=${WOGE_PATCH_PROTOCOL_VERSION}` }, signal },
      );
      if (!response.ok || !response.body) throw response;
      return response.body;
    },
    onError: (problem) => console.warn("Live board notice was not refreshed.", problem),
  });
  // The page's own task makes the board current again, so a notice about it would be stale.
  document.addEventListener(AFTER_REPLACE_EVENT, (event) => {
    if (event.target.querySelector?.('input[name="version"]')) live.refresh(notice.dataset.wogeRegion);
  });
}

async function loadDeferredRegions(url) {
  try {
    const response = await fetch(url, {
      headers: { Accept: `application/vnd.woge.patch-stream; version=${WOGE_PATCH_PROTOCOL_VERSION}` },
    });
    if (!response.ok || !response.body) throw response;
    runtime ??= createWogePatchRuntime(document);
    await runtime.applyPatchStream(response.body);
  } catch (problem) {
    recover(url, classifyWogeFailure(problem, { safeRequest: true }));
  }
}

// Deferred regions are a safe GET, so one retry is allowed. Every other outcome keeps the
// server-rendered fallback, which already links to the full-page version.
function recover(url, failure) {
  if (failure.outcome === "retry-safe" && budget.tryRetry(url)) {
    void loadDeferredRegions(url);
    return;
  }
  const epoch = document.querySelector('meta[name="woge-page-epoch"]')?.content;
  if (failure.outcome === "reload-page" && epoch && budget.tryReload(epoch)) {
    location.reload();
    return;
  }
  console.warn("Woge enhancement stopped; the full-page version remains available.", failure.code, failure.outcome);
}
