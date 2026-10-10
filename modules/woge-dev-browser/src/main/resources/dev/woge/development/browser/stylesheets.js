// Swaps the page's own stylesheets in place, so focus, form values, scroll, open dialogs and media
// keep their state. Each new <link> loads before the old one is removed, so the page never shows
// unstyled content. Stylesheets from other origins, such as a Vite dev server, are left alone:
// their owner updates them.

const marker = "data-woge-development-stylesheet";

function ownStylesheets() {
  return [...document.querySelectorAll('link[rel~="stylesheet"][href]')].filter((link) => {
    if (link.hasAttribute(`${marker}-pending`)) return false;
    try {
      return new URL(link.href).origin === location.origin;
    } catch {
      return false;
    }
  });
}

let pending = [];

/**
 * Loads every same-origin stylesheet again for [build]. Calls [failed] once if any of them does not
 * load, so the caller can fall back to a document refresh. A newer call cancels an unfinished one.
 */
export function swapStylesheets(build, failed) {
  for (const link of pending) link.remove();
  pending = [];
  let reported = false;
  const swaps = ownStylesheets().map((current) => {
    const next = current.cloneNode();
    const url = new URL(current.href);
    url.searchParams.set("woge-development-build", String(build));
    next.href = url.href;
    next.setAttribute(`${marker}-pending`, "");
    pending.push(next);
    return new Promise((resolve) => {
      next.addEventListener("load", () => {
        if (!next.isConnected) return resolve();
        next.removeAttribute(`${marker}-pending`);
        current.remove();
        pending = pending.filter((link) => link !== next);
        resolve();
      }, { once: true });
      next.addEventListener("error", () => {
        if (next.isConnected && !reported) {
          reported = true;
          failed();
        }
        resolve();
      }, { once: true });
      current.after(next);
    });
  });
  return Promise.all(swaps);
}
