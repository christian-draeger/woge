import { fail } from "./protocol.js";

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

export class PageRegionRegistry {
  #document;
  #regions = new Map();

  constructor(root) {
    if (!root || root.nodeType !== 9 || typeof root.querySelectorAll !== "function") {
      fail("WOGE_INVALID_DOCUMENT", "The patch runtime requires one active Document");
    }
    this.#document = root;
    this.epoch = readPageEpoch(root);
    this.#registerInitialRegions();
  }

  applyReplace(patch) {
    this.#assertRegistryIntegrity();
    if (patch.epoch !== this.epoch) {
      fail("WOGE_STALE_PAGE_EPOCH", "Patch belongs to another page epoch");
    }

    const entry = this.#regions.get(patch.target);
    if (!entry) fail("WOGE_UNKNOWN_TARGET", "Patch target is not registered in the active page");
    this.#assertEntryState(entry);
    if (patch.interactionSequence !== entry.interactionSequence) {
      fail("WOGE_INTERACTION_MISMATCH", "Patch does not belong to the active target interaction");
    }
    if (patch.baseRevision !== entry.revision || patch.nextRevision !== entry.revision + 1n) {
      fail("WOGE_REVISION_MISMATCH", "Patch does not continue the active target revision");
    }

    const template = this.#document.createElement("template");
    template.innerHTML = patch.html;
    validateInertFragment(template.content);
    const registryChange = this.#planRegistryChange(entry.element, template.content);
    const detail = lifecycleDetail(patch);

    dispatchLifecycle(entry.element, BEFORE_REPLACE_EVENT, detail);
    this.#assertTargetStillActive(entry);
    closeRemovedOverlays(entry.element);
    entry.element.replaceChildren(template.content);
    entry.element.setAttribute(REVISION_ATTRIBUTE, patch.nextRevision.toString());
    entry.revision = patch.nextRevision;
    this.#commitRegistryChange(registryChange);
    dispatchLifecycle(entry.element, AFTER_REPLACE_EVENT, detail);
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
    for (const element of target.querySelectorAll(REGION_SELECTOR)) {
      const id = readOpaqueId(element.getAttribute(REGION_ATTRIBUTE), "region identifier");
      if (this.#regions.get(id)?.element === element) removedIds.push(id);
    }

    const occupied = new Set(this.#regions.keys());
    removedIds.forEach((id) => occupied.delete(id));
    const addedEntries = [];
    for (const element of fragment.querySelectorAll(REGION_SELECTOR)) {
      const entry = regionEntry(element);
      if (occupied.has(entry.id)) {
        fail("WOGE_DUPLICATE_TARGET", "Replacement HTML contains a duplicate region identifier");
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
  });
}

function dispatchLifecycle(target, name, detail) {
  const EventConstructor = target.ownerDocument.defaultView.CustomEvent;
  target.dispatchEvent(new EventConstructor(name, { bubbles: true, composed: true, detail }));
}

function closeRemovedOverlays(target) {
  for (const dialog of target.querySelectorAll("dialog[open]")) {
    if (typeof dialog.close === "function") dialog.close();
  }
  for (const popover of target.querySelectorAll("[popover]")) {
    try {
      if (typeof popover.hidePopover === "function" && popover.matches(":popover-open")) popover.hidePopover();
    } catch {
      // A browser without Popover support has no active popover state to preserve.
    }
  }
}
