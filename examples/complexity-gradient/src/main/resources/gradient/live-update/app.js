import {
  connectWogeLive,
  createWogePatchRuntime,
  WOGE_PATCH_PROTOCOL_VERSION,
} from "/assets/woge/index.js";

const liveUrl = document.querySelector('meta[name="woge-live-url"]')?.content;

// Live updates are optional: without EventSource the page still shows current data on reload.
if (liveUrl && typeof EventSource === "function") {
  connectWogeLive(createWogePatchRuntime(document), liveUrl, {
    load: async (context, { signal }) => {
      const parts = [
        context.pageEpoch,
        context.targets[0].target,
        context.targets[0].baseRevision,
        context.interactionSequence,
      ];
      const response = await fetch(`/announcements/regions/${parts.map(encodeURIComponent).join("/")}`, {
        headers: { Accept: `application/vnd.woge.patch-stream; version=${WOGE_PATCH_PROTOCOL_VERSION}` },
        signal,
      });
      if (!response.ok || !response.body) throw response;
      return response.body;
    },
    onError: (problem) => console.warn("The announcements were not refreshed.", problem),
  });
}
