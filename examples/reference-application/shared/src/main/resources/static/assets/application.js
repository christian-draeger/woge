import { createWogePatchRuntime, WOGE_PATCH_PROTOCOL_VERSION } from "./woge/index.js";

const page = document.querySelector("[data-woge-patch-url]");

if (page) {
  void loadDeferredRegions(page.dataset.wogePatchUrl);
}

async function loadDeferredRegions(url) {
  try {
    const response = await fetch(url, {
      headers: { Accept: `application/vnd.woge.patch-stream; version=${WOGE_PATCH_PROTOCOL_VERSION}` },
    });
    if (!response.ok || !response.body) {
      throw new Error(`Deferred request failed with HTTP ${response.status}`);
    }
    await createWogePatchRuntime(document).applyPatchStream(response.body);
  } catch (problem) {
    console.error("Woge enhancement stopped; the full-page form remains available.", problem);
  }
}
