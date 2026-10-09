import {
  classifyWogeFailure,
  createWogePatchRuntime,
  createWogeRecoveryBudget,
  WOGE_PATCH_PROTOCOL_VERSION,
} from "./woge/index.js";

const page = document.querySelector("[data-woge-patch-url]");
const budget = createWogeRecoveryBudget();

if (page) {
  void loadDeferredRegions(page.dataset.wogePatchUrl);
}

async function loadDeferredRegions(url) {
  try {
    const response = await fetch(url, {
      headers: { Accept: `application/vnd.woge.patch-stream; version=${WOGE_PATCH_PROTOCOL_VERSION}` },
    });
    if (!response.ok || !response.body) throw response;
    await createWogePatchRuntime(document).applyPatchStream(response.body);
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
