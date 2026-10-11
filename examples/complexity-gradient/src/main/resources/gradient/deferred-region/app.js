import { createWogePatchRuntime, WOGE_PATCH_PROTOCOL_VERSION } from "/assets/woge/index.js";

const patchUrl = document.body.dataset.wogePatchUrl;
if (patchUrl) {
  const response = await fetch(patchUrl, {
    headers: { Accept: `application/vnd.woge.patch-stream; version=${WOGE_PATCH_PROTOCOL_VERSION}` },
  });
  if (!response.ok || !response.body) throw new Error(`Patch request failed: ${response.status}`);
  await createWogePatchRuntime(document).applyPatchStream(response.body);
}
