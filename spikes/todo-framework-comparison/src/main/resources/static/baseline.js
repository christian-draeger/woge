// Fixture-owned behavior; htmx owns action requests, this small adapter owns live reads.
htmx.config.responseHandling = [{ code: "204", swap: false }, { code: "400", swap: true },
  { code: "[23]..", swap: true }, { code: "[45]..", swap: false, error: true }];
let mutations = 0;
let generation = 0;
let dirty = false;
let refreshing = false;
let controller;
const filter = new URL(location.href).searchParams.get("filter") ?? "all";
const alert = () => document.querySelector("#todo-alert");
document.addEventListener("htmx:beforeRequest", event => {
  if (event.detail.elt instanceof HTMLFormElement) {
    mutations++;
    generation++;
    controller?.abort();
  }
});
document.addEventListener("htmx:afterRequest", event => {
  mutations = Math.max(0, mutations - 1);
  if (event.detail.xhr.status >= 400 && event.detail.xhr.status !== 400) {
    alert().textContent = "Could not confirm the result. Reload before submitting again.";
  }
  if (!mutations && dirty) void refresh();
});
document.addEventListener("htmx:afterSwap", event => {
  if (event.detail.xhr?.status === 400) document.querySelector("#todo-errors")?.focus();
});
async function refresh() {
  dirty = true;
  if (mutations || refreshing) return;
  refreshing = true;
  try {
    while (dirty && !mutations) {
      dirty = false;
      const current = generation;
      controller = new AbortController();
      try {
        const response = await fetch(`/board?filter=${encodeURIComponent(filter)}`, { signal: controller.signal, credentials: "same-origin" });
        if (!response.ok || !response.headers.get("content-type")?.startsWith("text/html")) throw new Error("Live read failed");
        const html = await response.text();
        if (current === generation && !mutations) htmx.swap("#todo-board", html, { swapStyle: "outerHTML" });
        else dirty = true;
      } catch (error) {
        if (error.name === "AbortError") dirty = true;
        else { alert().textContent = "Live updates are unavailable. Reload to refresh."; dirty = false; }
      }
    }
  } finally { refreshing = false; }
}
const events = new EventSource(`/events?filter=${encodeURIComponent(filter)}`);
events.addEventListener("todo-change", () => void refresh());
events.addEventListener("open", () => void refresh());
addEventListener("pagehide", () => { controller?.abort(); events.close(); });
