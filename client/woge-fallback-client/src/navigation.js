import { WOGE_PATCH_PROTOCOL_VERSION } from "./version.js";
import { captureWogeBrowserState, prepareWogeBrowserState } from "./state.js";

const REGION = "[data-woge-navigation-region]";
const META = 'meta[data-woge-navigation-meta][name]';
const MANAGED = 'meta[name="woge-render-session"], [data-woge-region], [data-woge-live-url], ' +
  '[data-woge-action], [data-woge-deferred]';
const installed = new WeakMap();

/** Explicit opt-in; complete HTML routes remain the only source of document truth. */
export function installWogeNavigation(root = document) {
  if (installed.has(root)) return installed.get(root);
  const view = root.defaultView;
  const history = view.history;
  const previousRestoration = history.scrollRestoration;
  history.scrollRestoration = "manual";
  let pending;
  let disposed = false;
  let current = view.location.href;
  const positions = new Map();
  let sequence = 0;
  let fragmentNavigation = false;
  const entry = () => history.state?.wogeNavigation;
  const mark = () => {
    if (!entry()) history.replaceState({ ...history.state, wogeNavigation: `${Date.now()}-${++sequence}` }, "");
  };
  mark();
  let key = entry();
  const save = () => {
    positions.set(key, [view.scrollX, view.scrollY]);
    if (positions.size > 128) positions.delete(positions.keys().next().value);
  };
  const fallback = (url) => view.location.assign(url);
  const navigate = async (url, traversal = false) => {
    pending?.abort();
    const controller = new AbortController();
    pending = controller;
    let destination = url;
    const timer = view.setTimeout(() => controller.abort(), 10000);
    try {
      const response = await view.fetch(url, {
        headers: { Accept: "text/html" }, credentials: "same-origin", signal: controller.signal,
      });
      const final = new URL(response.url);
      if (!final.hash) final.hash = new URL(url).hash;
      if (final.origin === view.location.origin) destination = final.href;
      if (!response.ok || final.origin !== view.location.origin ||
          !/^text\/html(?:;|$)/i.test(response.headers.get("Content-Type") ?? "")) throw Error();
      const html = await response.text();
      if (html.length > 2 * 1024 * 1024) throw Error();
      const next = new view.DOMParser().parseFromString(html, "text/html");
      const old = route(root);
      const incoming = route(next);
      if (!old || !incoming || old.getAttribute("data-woge-navigation-region") !==
          incoming.getAttribute("data-woge-navigation-region") || root.querySelector(MANAGED) ||
          next.querySelector(MANAGED) || !compatible(root, next) ||
          !incoming.querySelector("[data-woge-heading], h1") ||
          incoming.querySelector("script, base, iframe, object, embed, style, link, meta") ||
          [incoming, ...incoming.querySelectorAll("*")].some((node) => [...node.attributes].some((a) => /^on/i.test(a.name)))) {
        throw Error();
      }
      const metadata = [...next.head.querySelectorAll(META)];
      if (new Set(metadata.map((node) => node.name)).size !== metadata.length) throw Error();
      const replacement = root.importNode(incoming, true);
      const state = prepareWogeBrowserState(captureWogeBrowserState(old), replacement, {
        reset: incoming.getAttribute("data-woge-state") === "reset",
      });
      if (pending !== controller || disposed) return;
      save();
      root.dispatchEvent(new view.CustomEvent("woge:before-navigation"));
      if (pending !== controller || disposed || !old.isConnected) throw Error();
      state.commit();
      old.replaceWith(replacement);
      root.title = next.title;
      root.head.querySelectorAll(META).forEach((node) => node.remove());
      metadata.forEach((node) => root.head.append(root.importNode(node, true)));
      if (traversal) {
        if (destination !== view.location.href) history.replaceState(history.state, "", destination);
      } else {
        history.pushState({ wogeNavigation: `${Date.now()}-${++sequence}` }, "", destination);
      }
      mark();
      key = entry();
      current = destination;
      const heading = replacement.querySelector("[data-woge-heading], h1");
      if (heading) {
        const previous = heading.getAttribute("tabindex");
        heading.setAttribute("tabindex", "-1");
        heading.focus({ preventScroll: true });
        heading.addEventListener("blur", () => {
          if (previous === null) heading.removeAttribute("tabindex");
          else heading.setAttribute("tabindex", previous);
        }, { once: true });
      }
      const status = root.querySelector("[data-woge-navigation-status][role=status]");
      if (status) status.textContent = root.title;
      // Resolve new document layout before restoring absolute document scroll.
      replacement.getBoundingClientRect();
      const saved = traversal && positions.get(key);
      const hash = new URL(destination).hash;
      const fragment = hash && root.getElementById(decodeURIComponent(hash.slice(1)));
      if (saved) view.scrollTo({ left: saved[0], top: saved[1], behavior: "instant" });
      else if (fragment) fragment.scrollIntoView({ behavior: "instant" });
      else view.scrollTo({ left: 0, top: 0, behavior: "instant" });
      root.dispatchEvent(new view.CustomEvent("woge:after-navigation"));
    } catch {
      if (pending === controller && !disposed) fallback(destination);
    } finally {
      view.clearTimeout(timer);
      if (pending === controller) pending = undefined;
    }
  };
  const click = (event) => {
    const link = event.target.closest?.("a[href]");
    if (!link || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey ||
        event.shiftKey || event.altKey || link.hasAttribute("download") || link.hasAttribute("target") ||
        root.querySelector("base[target]") || link.hasAttribute("ping") || link.relList.contains("external") ||
        link.closest("[data-woge-navigation]")?.getAttribute("data-woge-navigation") !== "true") return;
    const url = new URL(link.href);
    if (url.origin === view.location.origin && url.pathname === view.location.pathname &&
        url.search === view.location.search && url.hash !== view.location.hash) {
      save();
      fragmentNavigation = true;
      return;
    }
    if (!/^https?:$/.test(url.protocol) || url.origin !== view.location.origin || url.username || url.password ||
        url.pathname === view.location.pathname && url.search === view.location.search ||
        !route(root) || root.querySelector(MANAGED)) return;
    event.preventDefault();
    void navigate(url.href);
  };
  const pop = () => {
    if (fragmentNavigation) return;
    if (view.location.href === current) {
      key = entry();
      const saved = positions.get(key);
      if (saved) view.scrollTo({ left: saved[0], top: saved[1], behavior: "instant" });
      return;
    }
    if (!entry()) {
      const previous = new URL(current);
      if (previous.pathname === view.location.pathname && previous.search === view.location.search) hash();
      else fallback(view.location.href);
      return;
    }
    void navigate(view.location.href, true);
  };
  const scroll = () => { if (!fragmentNavigation) save(); };
  const hash = () => {
    if (pending) return;
    if (!entry() || entry() === key && view.location.href !== current) {
      history.replaceState({ ...history.state, wogeNavigation: `${Date.now()}-${++sequence}` }, "");
    }
    key = entry();
    current = view.location.href;
    fragmentNavigation = false;
    save();
  };
  root.addEventListener("click", click);
  view.addEventListener("popstate", pop);
  view.addEventListener("hashchange", hash);
  view.addEventListener("scroll", scroll, { passive: true });
  const handle = Object.freeze({
    dispose() {
      disposed = true;
      pending?.abort();
      root.removeEventListener("click", click);
      view.removeEventListener("popstate", pop);
      view.removeEventListener("hashchange", hash);
      view.removeEventListener("scroll", scroll);
      view.removeEventListener("pagehide", handle.dispose);
      history.scrollRestoration = previousRestoration;
      installed.delete(root);
    },
  });
  view.addEventListener("pagehide", handle.dispose);
  installed.set(root, handle);
  return handle;
}

function route(root) {
  const regions = root.querySelectorAll(REGION);
  return regions.length === 1 ? regions[0] : null;
}

function compatible(old, next) {
  const value = (doc, name) => doc.querySelector(`meta[name="${name}"]`)?.content;
  const token = value(old, "woge-navigation-version");
  const protocol = value(next, "woge-patch-protocol-version");
  const assets = (doc) => [...doc.head.querySelectorAll(`script, link, style, base, meta:not(${META})`)]
    .map((node) => node.outerHTML).join("");
  return token && token === value(next, "woge-navigation-version") &&
    protocol === String(WOGE_PATCH_PROTOCOL_VERSION) &&
    value(old, "woge-patch-protocol-version") === protocol && old.title && next.title &&
    old.documentElement.lang === next.documentElement.lang && old.documentElement.dir === next.documentElement.dir &&
    assets(old) === assets(next);
}
