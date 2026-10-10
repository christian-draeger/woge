import { compatible, controlValue, writeControlValue, selection } from "./state-controls.js";

const STORAGE = "woge-development-refresh-v1";
const MAX_SIZE = 65536;
const MAX_CONTROLS = 200;
const MAX_VALUE = 4096;
const MAX_AGE = 60000;
const warn = () => console.warn("Woge development state handoff unavailable; using ordinary refresh.");
const identity = (element) => {
  const key = element.getAttribute("data-woge-state-key");
  if (key !== null) {
    if (!/^[A-Za-z0-9_-]{1,256}$/.test(key)) throw new TypeError("Invalid state key");
    return `key:${key}`;
  }
  return element.id && element.id.length <= 256 ? `id:${element.id}` : null;
};
const reset = (element) => Boolean(element.closest('[data-woge-state="reset"]'));
const excluded = (element) => Boolean(element.closest("[data-woge-island], [contenteditable]")) ||
  ["password", "hidden", "file"].includes(element.type) ||
  /(?:^|\s)(?:off|current-password|new-password|one-time-code|cc-\S+)(?:\s|$)/i.test(
    element.autocomplete || element.form?.autocomplete || "");
const optedIn = (element) => element.hasAttribute("data-woge-development-preserve") &&
  !reset(element) && !excluded(element);

function elements() {
  const result = new Map();
  for (const element of document.querySelectorAll("[data-woge-state-key], [id]")) {
    const key = identity(element);
    if (!key) continue;
    if (result.has(key)) throw new TypeError("Ambiguous state identity");
    result.set(key, element);
  }
  return result;
}

function descriptor(element) {
  return { localName: element.localName, type: element.type, multiple: element.multiple };
}

function validValue(control) {
  if (control.kind === "text") return typeof control.value === "string" && control.value.length <= MAX_VALUE;
  if (control.kind === "checked") return typeof control.value === "boolean";
  return control.kind === "select" && Array.isArray(control.value) && control.value.length <= MAX_CONTROLS &&
    control.value.every((value) => typeof value === "string" && value.length <= MAX_VALUE);
}

/** Only explicitly opted-in, non-sensitive dirty values cross a document reload. */
export function saveRefreshState(next) {
  try {
    sessionStorage.removeItem(STORAGE);
    elements();
    const controls = [];
    for (const element of document.querySelectorAll("input, textarea, select")) {
      if (!optedIn(element)) continue;
      const value = controlValue(element);
      if (!value?.dirty) continue;
      const key = identity(element);
      if (!key || !validValue(value)) throw new TypeError("Unbounded or unkeyed dirty control");
      controls.push({ key, ...descriptor(element), kind: value.kind, value: value.value });
    }
    if (controls.length > MAX_CONTROLS) throw new TypeError("Too many dirty controls");
    const active = document.activeElement;
    const focus = active && !reset(active) && !excluded(active) &&
      !active.closest("dialog, [popover]") ? identity(active) : null;
    const state = JSON.stringify({
      version: 1, url: location.href, session: next.session,
      build: next.renderedBuild, generation: next.generation, created: Date.now(),
      controls, focus, focusType: focus ? descriptor(active) : null,
      selection: focus ? selection(active) : null,
      scroll: reset(document.body) ? null : [scrollX, scrollY],
    });
    if (state.length > MAX_SIZE) throw new TypeError("State handoff too large");
    sessionStorage.setItem(STORAGE, state);
  } catch {
    warn();
  }
}

/** Consume before validation, including navigation and invalid/stale entries: no replay loops. */
export function takeRefreshState(config) {
  try {
    const raw = sessionStorage.getItem(STORAGE);
    sessionStorage.removeItem(STORAGE);
    if (!raw) return null;
    if (performance.getEntriesByType("navigation")[0]?.type !== "reload") return null;
    if (raw.length > MAX_SIZE) throw new TypeError("State handoff too large");
    const state = JSON.parse(raw);
    if (state.version !== 1 || state.url !== location.href || state.build !== config.build ||
        state.generation !== config.generation || !Number.isFinite(state.created) ||
        Date.now() - state.created < 0 || Date.now() - state.created > MAX_AGE) return null;
    return state;
  } catch {
    warn();
    return null;
  }
}

export function restoreRefreshState(state, next) {
  if (!state || state.session !== next.session) return;
  try {
    if (!Array.isArray(state.controls) || state.controls.length > MAX_CONTROLS) throw new TypeError("Invalid controls");
    const keys = elements();
    const plans = [];
    const seen = new Set();
    for (const control of state.controls) {
      if (typeof control.key !== "string" || seen.has(control.key) || !validValue(control)) {
        throw new TypeError("Invalid control state");
      }
      seen.add(control.key);
      const target = keys.get(control.key);
      if (target && reset(target)) continue;
      if (!target || !optedIn(target) || !compatible(control, target) ||
          controlValue(target)?.kind !== control.kind) throw new TypeError("Changed control");
      const prepared = target.cloneNode(true);
      if (!writeControlValue(prepared, control)) throw new TypeError("Changed selected option");
      plans.push([target, control]);
    }
    if (state.scroll !== null && (!Array.isArray(state.scroll) || state.scroll.length !== 2 ||
        !state.scroll.every((value) => Number.isFinite(value) && value >= 0))) throw new TypeError("Invalid scroll");
    if (state.selection !== null && (!Number.isInteger(state.selection.start) ||
        !Number.isInteger(state.selection.end) || state.selection.start < 0 ||
        state.selection.end < state.selection.start ||
        !["forward", "backward", "none"].includes(state.selection.direction))) throw new TypeError("Invalid selection");
    for (const [target, control] of plans) writeControlValue(target, control);
    const target = keys.get(state.focus);
    if (target && state.focusType && compatible(state.focusType, target) &&
        !reset(target) && !excluded(target) && !target.closest("dialog, [popover]") &&
        [document.body, document.documentElement].includes(document.activeElement)) {
      target.focus({ preventScroll: true });
      if (document.activeElement === target && state.selection && selection(target)) {
        target.setSelectionRange(Math.min(state.selection.start, target.value.length),
          Math.min(state.selection.end, target.value.length), state.selection.direction);
      }
    }
    if (state.scroll && !reset(document.body)) {
      window.scrollTo({ left: state.scroll[0], top: state.scroll[1], behavior: "instant" });
    }
  } catch {
    warn();
  }
}
