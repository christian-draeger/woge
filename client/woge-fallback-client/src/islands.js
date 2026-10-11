const ROOT = "[data-woge-island]";
const installed = new WeakMap();

/** Validate an initial-document allowlist. Patch attributes never supply imports. */
export function parseIslandManifest(text) {
  const input = JSON.parse(text);
  if (!input || Array.isArray(input) || typeof input !== "object") throw new Error("manifest");
  const entries = new Map();
  for (const [name, entry] of Object.entries(input)) {
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(name) || !entry || typeof entry.url !== "string" || typeof entry.mount !== "string" ||
        !/^\/(?!\/)[A-Za-z0-9_/-]+\.m?js\?v=[A-Za-z0-9_-]{1,64}$/.test(entry.url) ||
        !/^[A-Za-z_$][A-Za-z0-9_$]{0,63}$/.test(entry.mount) ||
        [name, entry.url, entry.mount].some((value) => value.trim() !== value) ||
        !Number.isSafeInteger(entry.propsVersion) || entry.propsVersion < 1) throw new Error("manifest");
    entries.set(name, Object.freeze({ ...entry }));
  }
  return entries;
}

export function parseIslandProps(text, version) {
  const envelope = JSON.parse(text);
  if (!envelope || envelope.version !== version || !envelope.value ||
      Array.isArray(envelope.value) || typeof envelope.value !== "object" ||
      Object.values(envelope.value).some((value) => typeof value !== "string")) throw new Error("props");
  return Object.freeze(envelope.value);
}

/** One root-local lifecycle manager per document; no client UI tree or global application state. */
export function installWogeIslands({ document = globalThis.document,
  importModule = (url) => import(url) } = {}) {
  if (installed.has(document)) return installed.get(document);
  const window = document.defaultView;
  const records = new Map();
  const imports = new Map();
  let manifest;
  let stopped = false;
  function report(code, root = document.documentElement) {
    root.dispatchEvent(new window.CustomEvent("woge:island-error", {
      bubbles: true, composed: true, detail: Object.freeze({ code }),
    }));
  }
  try {
    const nodes = document.head.querySelectorAll('script#woge-islands[type="application/json"]');
    if (nodes.length !== 1) throw new Error("manifest");
    manifest = parseIslandManifest(nodes[0].textContent);
  } catch {
    report("WOGE_ISLAND_MANIFEST_INVALID");
    manifest = new Map();
  }
  const observer = window.IntersectionObserver ? new window.IntersectionObserver((entries) => {
    for (const entry of entries) if (entry.isIntersecting) void mount(entry.target);
  }) : null;
  function dispose(root) {
    const record = records.get(root);
    if (!record) return;
    records.delete(root);
    observer?.unobserve(root);
    record.abort.abort();
    try { record.controller?.dispose?.(); } catch { report("WOGE_ISLAND_DISPOSE_FAILED", root); }
  }
  async function mount(root) {
    const record = records.get(root);
    if (!record || record.started || !root.isConnected || stopped) return;
    record.started = true;
    observer?.unobserve(root);
    const entry = manifest.get(root.getAttribute("data-woge-island"));
    let props;
    try {
      if (!entry) throw new Error("unknown");
      const templates = [...root.children].filter((node) => node.matches("template[data-woge-island-props]"));
      if (templates.length !== 1) throw new Error("props");
      props = parseIslandProps(templates[0].content.textContent, entry.propsVersion);
    } catch {
      report(entry ? "WOGE_ISLAND_PROPS_INVALID" : "WOGE_ISLAND_UNKNOWN_TYPE", root);
      return;
    }
    let module;
    try {
      if (!imports.has(entry.url)) imports.set(entry.url, Promise.resolve().then(() => importModule(entry.url)));
      module = await imports.get(entry.url);
    } catch {
      if (records.get(root) === record) report("WOGE_ISLAND_IMPORT_FAILED", root);
      return;
    }
    if (records.get(root) !== record || !root.isConnected || stopped) return;
    // Snapshot before calling application code so synchronous mount errors can restore useful HTML.
    const fallback = [...root.childNodes].map((node) => node.cloneNode(true));
    try {
      if (typeof module[entry.mount] !== "function") throw new Error("export");
      const controller = module[entry.mount](root, props, Object.freeze({
        signal: record.abort.signal, propsVersion: entry.propsVersion,
      }));
      if (controller?.then || controller &&
          ["update", "dispose"].some((name) => controller[name] != null && typeof controller[name] !== "function")) {
        throw new Error("controller");
      }
      record.controller = controller;
    } catch {
      record.abort.abort();
      root.replaceChildren(...fallback);
      report("WOGE_ISLAND_MOUNT_FAILED", root);
    }
  }
  function scan(scope = document) {
    if (stopped) return;
    const roots = [...scope.querySelectorAll(ROOT)];
    if (scope.matches?.(ROOT)) roots.unshift(scope);
    for (const root of roots) {
      if (records.has(root)) continue;
      if (root.parentElement?.closest(ROOT) || root.matches("[data-woge-region]") ||
          root.querySelector("[data-woge-region], [data-woge-island]")) {
        report("WOGE_ISLAND_OWNERSHIP_INVALID", root);
        continue;
      }
      records.set(root, { abort: new window.AbortController(), started: false });
      const loading = root.getAttribute("data-woge-island-loading") ?? "visible";
      if (!["visible", "eager"].includes(loading)) {
        report("WOGE_ISLAND_PROPS_INVALID", root);
      } else if (loading === "eager" || !observer) void mount(root);
      else observer.observe(root);
    }
  }
  function beforeReplace(event) {
    for (const root of records.keys()) {
      if (event.target !== root && event.target.contains(root) &&
          (!root.hasAttribute("data-woge-state-key") || event.target.getAttribute("data-woge-state") === "reset")) {
        dispose(root);
      }
    }
  }
  function beforeRemove(event) {
    const item = [...event.target.children].find((node) => node.getAttribute("data-woge-item") === event.detail.itemId);
    for (const root of records.keys()) if (item?.contains(root)) dispose(root);
  }
  function after(event) {
    for (const root of records.keys()) if (!root.isConnected) dispose(root);
    scan(event.target);
  }
  function pageHide(event) { if (!event.persisted) api.dispose(); }
  const listeners = [
    ["woge:before-replace", beforeReplace], ["woge:after-replace", after],
    ["woge:before-remove", beforeRemove], ["woge:after-remove", after], ["woge:after-append", after],
  ];
  const api = Object.freeze({
    scan,
    update(root, props) {
      const record = records.get(root);
      try {
        if (!record?.controller?.update || !props || Array.isArray(props) || typeof props !== "object" ||
            Object.values(props).some((value) => typeof value !== "string")) throw new Error("update");
        record.controller.update(Object.freeze({ ...props }));
      } catch { report("WOGE_ISLAND_UPDATE_FAILED", root); }
    },
    dispose() {
      stopped = true;
      for (const root of records.keys()) dispose(root);
      observer?.disconnect();
      for (const [name, listener] of listeners) document.removeEventListener(name, listener);
      window.removeEventListener("pagehide", pageHide);
      installed.delete(document);
    },
  });
  installed.set(document, api);
  for (const [name, listener] of listeners) document.addEventListener(name, listener);
  window.addEventListener("pagehide", pageHide);
  scan();
  return api;
}

if (globalThis.document) {
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", () => installWogeIslands(), { once: true });
  else installWogeIslands();
}
