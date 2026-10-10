import { fail } from "./protocol.js";
import { compatible, controlValue, writeControlValue, selection } from "./state-controls.js";

const KEY = "data-woge-state-key";
const KEYS = `[${KEY}]`;
const SNAPSHOTS = new WeakMap();

/** Captures browser-owned state in memory. Snapshots are opaque and must not be persisted or logged. */
export function captureWogeBrowserState(root) {
  if (!root || typeof root.querySelectorAll !== "function") {
    fail("WOGE_INVALID_BROWSER_STATE", "Browser state requires a DOM root");
  }
  const document = root.ownerDocument ?? root;
  const keys = keyedElements(root);
  const controls = [];
  for (const element of root.querySelectorAll("input, textarea, select")) {
    const value = controlValue(element);
    if (value) controls.push({ element, key: element.getAttribute(KEY), ...value });
  }
  const active = document.activeElement;
  const ownsFocus = root.contains(active);
  const snapshot = Object.freeze({});
  SNAPSHOTS.set(snapshot, {
    root, keys, controls, active: ownsFocus ? active : null,
    focusKey: ownsFocus ? active.getAttribute(KEY) : null,
    selection: ownsFocus ? selection(active) : null,
  });
  return snapshot;
}

/** Prepares a replacement without changing the active document. */
export function prepareWogeBrowserState(snapshot, destination, { reset = false } = {}) {
  const state = SNAPSHOTS.get(snapshot);
  if (!state) fail("WOGE_INVALID_BROWSER_STATE", "Unknown browser state snapshot");
  if (!destination || typeof destination.querySelectorAll !== "function" || destination.isConnected) {
    fail("WOGE_INVALID_BROWSER_STATE", "Prepare browser state in a detached DOM destination");
  }
  const keys = keyedElements(destination);
  const retained = [];
  if (!reset) {
    for (const [key, element] of state.keys) {
      if (!element.hasAttribute("data-woge-island") && element.type !== "file") continue;
      if (element.type === "file" && retained.some(([node]) => node.contains(element))) continue;
      const replacement = keys.get(key);
      if (element.type === "file" && replacement?.getAttribute("data-woge-state") === "reset") continue;
      if (!replacement || !compatible(element, replacement) || !replacement.hasAttribute("data-woge-island") &&
          element.type !== "file") {
        fail("WOGE_BROWSER_STATE_CONFLICT", "A browser-owned node needs its matching replacement");
      }
      if (element.matches("[data-woge-region]") || element.querySelector("[data-woge-region]") ||
          replacement.matches("[data-woge-region]") || replacement.querySelector("[data-woge-region]")) {
        fail("WOGE_BROWSER_STATE_CONFLICT", "Local islands cannot own registered Woge regions");
      }
      if (retained.some(([old]) => old.contains(element) || element.contains(old))) {
        fail("WOGE_BROWSER_STATE_CONFLICT", "Browser-owned islands must not overlap");
      }
      retained.push([element, replacement]);
    }
    for (const control of state.controls) {
      if (!control.dirty || retained.some(([node]) => node.contains(control.element))) continue;
      const replacement = keys.get(control.key);
      if (!control.key || !replacement || !compatible(control.element, replacement)) {
        fail("WOGE_BROWSER_STATE_CONFLICT", "A dirty control needs a compatible stable state key or explicit reset");
      }
      if (replacement.getAttribute("data-woge-state") === "reset") continue;
      if (control.kind === "file") {
        fail("WOGE_BROWSER_STATE_CONFLICT", "Selected files must retain their native input node");
      }
      if (!writeControlValue(replacement, control)) {
        fail("WOGE_BROWSER_STATE_CONFLICT", "A dirty selection is unavailable in the replacement");
      }
    }
    for (const editable of state.root.querySelectorAll("[contenteditable]")) {
      if (editable.isContentEditable && !retained.some(([node]) => node.contains(editable))) {
        fail("WOGE_BROWSER_STATE_CONFLICT", "Contenteditable must be an explicit local island or reset");
      }
    }
  }
  const focus = state.focusKey ? keys.get(state.focusKey) : null;
  let committed = false;
  return Object.freeze({
    commit() {
      if (committed) fail("WOGE_INVALID_BROWSER_STATE", "Browser state plan was already committed");
      committed = true;
      for (const [element, replacement] of retained) replacement.replaceWith(element);
    },
    restoreFocus(fallback) {
      if (!committed) fail("WOGE_INVALID_BROWSER_STATE", "Commit browser state before restoring focus");
      if (!state.active || reset) return;
      const document = fallback.ownerDocument;
      if (document.activeElement === state.active) return;
      // A lifecycle listener or native overlay may have moved focus deliberately.
      if (document.activeElement !== document.body && document.activeElement !== document.documentElement) return;
      const retainedFocus = retained.some(([element]) => element.contains(state.active));
      if (!retainedFocus && !state.focusKey) return;
      const target = retainedFocus ? state.active : focus ?? fallback;
      if (typeof target.focus !== "function") {
        fail("WOGE_BROWSER_STATE_CONFLICT", "Focus recovery needs a focusable target");
      }
      target.focus({ preventScroll: true });
      if (document.activeElement !== target) {
        fail("WOGE_BROWSER_STATE_CONFLICT", "Focus recovery target is not focusable");
      }
      if (state.selection && compatible(state.active, target)) {
        const length = target.value.length;
        target.setSelectionRange(Math.min(state.selection.start, length),
          Math.min(state.selection.end, length), state.selection.direction);
      }
    },
  });
}

function keyedElements(root) {
  const keys = new Map();
  for (const element of root.querySelectorAll(KEYS)) {
    const key = element.getAttribute(KEY);
    if (!/^[A-Za-z0-9_-]{1,256}$/.test(key) || keys.has(key)) {
      fail("WOGE_INVALID_BROWSER_STATE", "Browser state keys must be valid and unique inside the replacement");
    }
    keys.set(key, element);
  }
  return keys;
}
