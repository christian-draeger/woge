import { fail } from "./protocol.js";
import { captureWogeBrowserState, prepareWogeBrowserState } from "./state.js";

const REGION_ATTRIBUTE = "data-woge-region";
const REVISION_ATTRIBUTE = "data-woge-revision";
const INTERACTION_ATTRIBUTE = "data-woge-interaction-sequence";
const REGION_SELECTOR = `[${REGION_ATTRIBUTE}]`;
const OPAQUE_ID_PATTERN = /^[A-Za-z0-9_-]{1,256}$/;
const COUNTER_PATTERN = /^(0|[1-9][0-9]{0,18})$/;
const MAX_SIGNED_LONG = 9_223_372_036_854_775_807n;
const BLOCKED_ELEMENTS = new Set(["base", "embed", "iframe", "link", "meta", "object", "script", "style"]);
const MULTI_URL_ATTRIBUTES = new Set(["imagesrcset", "ping", "srcset"]);
const URL_ATTRIBUTES = new Set([
  "action",
  "background",
  "cite",
  "data",
  "formaction",
  "href",
  "manifest",
  "poster",
  "src",
  "xlink:href",
]);
const SAFE_EXTERNAL_SCHEMES = new Set(["http", "https", "mailto", "tel"]);
const SCHEME_PATTERN = /^([A-Za-z][A-Za-z0-9+.-]*):/;
const INVALID_PERCENT_PATTERN = /%(?![0-9A-Fa-f]{2})/;
const INVALID_URL_CHARACTER_PATTERN = /[\u0000-\u0020\u007f\s\u061c\u200e\u200f\u202a-\u202e\u2066-\u2069]/u;

export const BEFORE_REPLACE_EVENT = "woge:before-replace";
export const AFTER_REPLACE_EVENT = "woge:after-replace";
export const BEFORE_APPEND_EVENT = "woge:before-append";
export const AFTER_APPEND_EVENT = "woge:after-append";
export const BEFORE_REMOVE_EVENT = "woge:before-remove";
export const AFTER_REMOVE_EVENT = "woge:after-remove";

export class PageRegionRegistry {
  #document;
  #regions = new Map();
  #interaction = 0n;

  constructor(root) {
    if (!root || root.nodeType !== 9 || typeof root.querySelectorAll !== "function") {
      fail("WOGE_INVALID_DOCUMENT", "The patch runtime requires one active Document");
    }
    this.#document = root;
    this.epoch = readPageEpoch(root);
    this.#registerInitialRegions();
    for (const entry of this.#regions.values()) {
      if (entry.interactionSequence > this.#interaction) this.#interaction = entry.interactionSequence;
    }
  }

  beginInteraction(targets) {
    this.#assertRegistryIntegrity();
    if (!Array.isArray(targets) || targets.length === 0 || targets.length > 128 ||
        new Set(targets).size !== targets.length) {
      fail("WOGE_INVALID_INTERACTION", "An interaction requires distinct registered targets");
    }
    const entries = targets.map((target) => {
      const entry = this.#regions.get(target);
      if (!entry) fail("WOGE_UNKNOWN_TARGET", "Interaction target is not registered");
      this.#assertEntryState(entry);
      return entry;
    });
    let latest = this.#interaction;
    for (const entry of this.#regions.values()) {
      if (entry.interactionSequence > latest) latest = entry.interactionSequence;
    }
    if (latest === MAX_SIGNED_LONG) {
      fail("WOGE_INTERACTION_EXHAUSTED", "Interaction overflow requires a new page epoch");
    }
    const sequence = latest + 1n;
    for (const entry of entries) {
      entry.element.setAttribute(INTERACTION_ATTRIBUTE, sequence.toString());
      entry.interactionSequence = sequence;
    }
    this.#interaction = sequence;
    return Object.freeze({
      pageEpoch: this.epoch,
      interactionSequence: sequence.toString(),
      targets: Object.freeze(entries.map((entry) => Object.freeze({
        target: entry.id, baseRevision: entry.revision.toString(),
      }))),
    });
  }

  interactionContext(target) {
    this.#assertRegistryIntegrity();
    const entry = this.#regions.get(target);
    if (!entry) fail("WOGE_UNKNOWN_TARGET", "Recovery target is not registered");
    this.#assertEntryState(entry);
    return {
      pageEpoch: this.epoch,
      targets: [{ target: entry.id, baseRevision: entry.revision.toString() }],
    };
  }

  applyPatch(patch) {
    this.#assertRegistryIntegrity();
    if (patch.epoch !== this.epoch) {
      fail("WOGE_STALE_PAGE_EPOCH", "Patch belongs to another page epoch");
    }

    const entry = this.#regions.get(patch.target);
    if (!entry) fail("WOGE_UNKNOWN_TARGET", "Patch target is not registered in the active page");
    this.#assertEntryState(entry);
    if (patch.interactionSequence < entry.interactionSequence || patch.nextRevision <= entry.revision) {
      return "stale";
    }
    if (patch.interactionSequence !== entry.interactionSequence) {
      fail("WOGE_INTERACTION_MISMATCH", "Patch does not belong to the active target interaction");
    }
    if (patch.baseRevision !== entry.revision || patch.nextRevision !== entry.revision + 1n) {
      fail("WOGE_REVISION_MISMATCH", "Patch does not continue the active target revision");
    }
    if (patch.operation === "append") return this.#append(entry, patch);
    if (patch.operation === "remove") return this.#remove(entry, patch);
    if (patch.operation !== "replace") fail("WOGE_INVALID_METADATA", "Patch operation is unsupported");

    const template = this.#document.createElement("template");
    template.innerHTML = patch.html;
    validateInertFragment(template.content);
    const registryChange = this.#planRegistryChange(entry.element, template.content);
    const detail = lifecycleDetail(patch);

    dispatchLifecycle(entry.element, BEFORE_REPLACE_EVENT, detail);
    this.#assertTargetStillActive(entry);
    const state = prepareWogeBrowserState(captureWogeBrowserState(entry.element), template.content, {
      reset: entry.element.getAttribute("data-woge-state") === "reset",
    });
    state.commit();
    closeRemovedOverlays(entry.element);
    entry.element.replaceChildren(template.content);
    entry.element.setAttribute(REVISION_ATTRIBUTE, patch.nextRevision.toString());
    entry.revision = patch.nextRevision;
    this.#commitRegistryChange(registryChange);
    state.restoreFocus(entry.element);
    dispatchLifecycle(entry.element, AFTER_REPLACE_EVENT, detail);
  }

  #append(entry, patch) {
    const template = this.#document.createElement("template");
    template.innerHTML = patch.html;
    validateInertFragment(template.content);
    const root = template.content.firstElementChild;
    if (template.content.children.length !== 1 || !root ||
        root.getAttribute("data-woge-item") !== patch.itemId ||
        [...template.content.childNodes].some((node) => node.nodeType !== 1 && node.textContent.trim() !== "")) {
      fail("WOGE_INVALID_ITEM", "Append requires exactly one identified item root");
    }
    const existing = this.#findItem(entry, patch.itemId);
    if (existing) {
      this.#advance(entry, patch);
      return;
    }
    const change = this.#planRegistryChange(null, template.content);
    const detail = lifecycleDetail(patch);
    dispatchLifecycle(entry.element, BEFORE_APPEND_EVENT, detail);
    this.#assertTargetStillActive(entry);
    if (this.#findItem(entry, patch.itemId)) fail("WOGE_ITEM_CHANGED", "The collection changed during append");
    entry.element.append(template.content);
    this.#advance(entry, patch);
    this.#commitRegistryChange(change);
    dispatchLifecycle(entry.element, AFTER_APPEND_EVENT, detail);
  }

  #remove(entry, patch) {
    const item = this.#findItem(entry, patch.itemId);
    const focus = this.#regions.get(patch.focusTarget);
    if (!focus) fail("WOGE_UNKNOWN_TARGET", "Removal focus target is not registered");
    if (!item) {
      this.#advance(entry, patch);
      return;
    }
    if (item.contains(focus.element)) fail("WOGE_INVALID_ITEM", "Removal cannot focus a region it removes");
    const detail = lifecycleDetail(patch);
    dispatchLifecycle(entry.element, BEFORE_REMOVE_EVENT, detail);
    this.#assertTargetStillActive(entry);
    this.#assertTargetStillActive(focus);
    if (this.#findItem(entry, patch.itemId) !== item) fail("WOGE_ITEM_CHANGED", "The item changed during removal");
    const moveFocus = item.contains(this.#document.activeElement);
    closeRemovedOverlays(item, true);
    if (moveFocus) {
      if (typeof focus.element.focus !== "function") {
        fail("WOGE_INVALID_ITEM", "Removal requires a focusable fallback region");
      }
      focus.element.focus();
      if (this.#document.activeElement !== focus.element) {
        fail("WOGE_INVALID_ITEM", "Removal requires a focusable fallback region");
      }
    }
    this.#assertTargetStillActive(entry);
    this.#assertTargetStillActive(focus);
    if (this.#findItem(entry, patch.itemId) !== item) fail("WOGE_ITEM_CHANGED", "The item changed during focus recovery");
    const removedIds = [...item.querySelectorAll(REGION_SELECTOR)]
      .map((element) => element.getAttribute(REGION_ATTRIBUTE));
    if (item.hasAttribute(REGION_ATTRIBUTE)) removedIds.push(item.getAttribute(REGION_ATTRIBUTE));
    item.remove();
    removedIds.forEach((id) => this.#regions.delete(id));
    this.#advance(entry, patch);
    dispatchLifecycle(entry.element, AFTER_REMOVE_EVENT, detail);
  }

  #findItem(entry, id) {
    const items = new Map();
    for (const element of entry.element.children) {
      if (!element.hasAttribute("data-woge-item")) continue;
      const itemId = readOpaqueId(element.getAttribute("data-woge-item"), "item identifier");
      if (items.has(itemId)) fail("WOGE_INVALID_ITEM", "Collection item IDs must be unique");
      items.set(itemId, element);
    }
    return items.get(id);
  }

  #advance(entry, patch) {
    entry.element.setAttribute(REVISION_ATTRIBUTE, patch.nextRevision.toString());
    entry.revision = patch.nextRevision;
  }

  #registerInitialRegions() {
    for (const element of this.#document.querySelectorAll(REGION_SELECTOR)) {
      const entry = regionEntry(element);
      if (this.#regions.has(entry.id)) {
        fail("WOGE_DUPLICATE_TARGET", "The active page contains a duplicate region identifier");
      }
      this.#regions.set(entry.id, entry);
    }
  }

  #assertRegistryIntegrity() {
    const elements = this.#document.querySelectorAll(REGION_SELECTOR);
    if (elements.length !== this.#regions.size) {
      fail("WOGE_REGION_REGISTRY_CHANGED", "The active page region registry changed outside Woge");
    }
    const seen = new Set();
    for (const element of elements) {
      const id = readOpaqueId(element.getAttribute(REGION_ATTRIBUTE), "region identifier");
      if (seen.has(id)) fail("WOGE_DUPLICATE_TARGET", "The active page contains a duplicate region identifier");
      seen.add(id);
      if (this.#regions.get(id)?.element !== element) {
        fail("WOGE_REGION_REGISTRY_CHANGED", "The active page region registry changed outside Woge");
      }
    }
  }

  #assertEntryState(entry) {
    if (readCounter(entry.element.getAttribute(REVISION_ATTRIBUTE), "region revision") !== entry.revision) {
      fail("WOGE_REGION_STATE_CHANGED", "The active region revision changed outside Woge");
    }
    const interaction = entry.element.getAttribute(INTERACTION_ATTRIBUTE) ?? "0";
    if (readCounter(interaction, "interaction sequence") !== entry.interactionSequence) {
      fail("WOGE_REGION_STATE_CHANGED", "The active interaction sequence changed outside Woge");
    }
  }

  #assertTargetStillActive(entry) {
    if (
      !entry.element.isConnected ||
      entry.element.ownerDocument !== this.#document ||
      entry.element.getAttribute(REGION_ATTRIBUTE) !== entry.id
    ) {
      fail("WOGE_TARGET_CHANGED", "The patch target changed during its before-replace lifecycle event");
    }
    this.#assertRegistryIntegrity();
    this.#assertEntryState(entry);
  }

  #planRegistryChange(target, fragment) {
    const removedIds = [];
    for (const element of target?.querySelectorAll(REGION_SELECTOR) ?? []) {
      const id = readOpaqueId(element.getAttribute(REGION_ATTRIBUTE), "region identifier");
      if (this.#regions.get(id)?.element === element) removedIds.push(id);
    }

    const occupied = new Set(this.#regions.keys());
    removedIds.forEach((id) => occupied.delete(id));
    const addedEntries = [];
    for (const element of fragment.querySelectorAll(REGION_SELECTOR)) {
      const entry = regionEntry(element);
      if (occupied.has(entry.id)) {
        fail("WOGE_DUPLICATE_TARGET", "Patch HTML contains a duplicate region identifier");
      }
      occupied.add(entry.id);
      addedEntries.push(entry);
    }
    return { removedIds, addedEntries };
  }

  #commitRegistryChange(change) {
    change.removedIds.forEach((id) => this.#regions.delete(id));
    change.addedEntries.forEach((entry) => this.#regions.set(entry.id, entry));
  }
}

function readPageEpoch(document) {
  const elements = document.head?.querySelectorAll('meta[name="woge-page-epoch"]') ?? [];
  if (elements.length !== 1) fail("WOGE_INVALID_PAGE_EPOCH", "The active document must declare exactly one page epoch");
  return readOpaqueId(elements[0].getAttribute("content"), "page epoch");
}

function regionEntry(element) {
  const id = readOpaqueId(element.getAttribute(REGION_ATTRIBUTE), "region identifier");
  const revision = readCounter(element.getAttribute(REVISION_ATTRIBUTE), "region revision");
  const interactionSequence = readCounter(
    element.getAttribute(INTERACTION_ATTRIBUTE) ?? "0",
    "interaction sequence",
  );
  return { id, element, revision, interactionSequence };
}

function readOpaqueId(value, label) {
  if (typeof value !== "string" || !OPAQUE_ID_PATTERN.test(value)) {
    fail("WOGE_INVALID_TARGET", `The ${label} is not a valid opaque Woge identifier`);
  }
  return value;
}

function readCounter(value, label) {
  if (typeof value !== "string" || !COUNTER_PATTERN.test(value)) {
    fail("WOGE_INVALID_REGION_STATE", `The ${label} is not a canonical non-negative integer`);
  }
  const counter = BigInt(value);
  if (counter > MAX_SIGNED_LONG) fail("WOGE_INVALID_REGION_STATE", `The ${label} exceeds the protocol limit`);
  return counter;
}

function validateInertFragment(fragment) {
  for (const element of fragment.querySelectorAll("*")) {
    const name = element.localName.toLowerCase();
    if (BLOCKED_ELEMENTS.has(name)) {
      fail("WOGE_ACTIVE_CONTENT", "Patch HTML contains a blocked active element");
    }
    for (const attribute of element.attributes) {
      const attributeName = attribute.name.toLowerCase();
      if (
        attributeName.startsWith("on") ||
        attributeName === "srcdoc" ||
        MULTI_URL_ATTRIBUTES.has(attributeName)
      ) {
        fail("WOGE_ACTIVE_CONTENT", "Patch HTML contains a blocked active attribute");
      }
      if (URL_ATTRIBUTES.has(attributeName) && !isSafeUrl(attribute.value)) {
        fail("WOGE_ACTIVE_CONTENT", "Patch HTML contains a blocked active URL");
      }
    }
    if (element.localName === "template") validateInertFragment(element.content);
  }
}

function isSafeUrl(value) {
  if (value === "") return true;
  if (value.includes("\\") || INVALID_URL_CHARACTER_PATTERN.test(value) || INVALID_PERCENT_PATTERN.test(value)) {
    return false;
  }
  const schemeMatch = SCHEME_PATTERN.exec(value);
  if (!schemeMatch) return !value.startsWith("//");

  const scheme = schemeMatch[1].toLowerCase();
  if (!SAFE_EXTERNAL_SCHEMES.has(scheme)) return false;
  if (scheme === "mailto" || scheme === "tel") return value.length > schemeMatch[0].length;
  if (!/^https?:\/\//i.test(value)) return false;
  try {
    const parsed = new URL(value);
    return parsed.protocol === `${scheme}:` && parsed.host !== "" && parsed.username === "" && parsed.password === "";
  } catch {
    return false;
  }
}

function lifecycleDetail(patch) {
  return Object.freeze({
    operation: patch.operation,
    patchId: patch.patchId,
    target: patch.target,
    interactionSequence: patch.interactionSequence.toString(),
    baseRevision: patch.baseRevision.toString(),
    nextRevision: patch.nextRevision.toString(),
    ...(patch.itemId === undefined ? {} : { itemId: patch.itemId }),
    ...(patch.focusTarget === undefined ? {} : { focusTarget: patch.focusTarget }),
  });
}

function dispatchLifecycle(target, name, detail) {
  const EventConstructor = target.ownerDocument.defaultView.CustomEvent;
  target.dispatchEvent(new EventConstructor(name, { bubbles: true, composed: true, detail }));
}

function closeRemovedOverlays(target, includeSelf = false) {
  const dialogs = [...target.querySelectorAll("dialog[open]")];
  if (includeSelf && target.matches("dialog[open]")) dialogs.unshift(target);
  for (const dialog of dialogs) {
    if (typeof dialog.close === "function") dialog.close();
  }
  const popovers = [...target.querySelectorAll("[popover]")];
  if (includeSelf && target.matches("[popover]")) popovers.unshift(target);
  for (const popover of popovers) {
    try {
      if (typeof popover.hidePopover === "function" && popover.matches(":popover-open")) popover.hidePopover();
    } catch {
      // A browser without Popover support has no active popover state to preserve.
    }
  }
}
