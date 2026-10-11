import { PatchStreamDecoder, fail } from "./protocol.js";

/** Decodes the complete bounded envelope before any member can mutate the active document. */
export function decodeTransaction(patch) {
  if (!/^\{"viewTransition":(true|false)\}$/.test(patch.metadata)) {
    fail("WOGE_INVALID_METADATA", "Transaction metadata is non-canonical");
  }
  // The outer decoder buffers nested frames; membership rejects them without recursively decoding.
  const decoder = new PatchStreamDecoder({ maxPatches: 128, maxStreamBytes: 8 * 1024 * 1024 });
  const events = decoder.push(patch.html);
  decoder.finish();
  const members = events.filter((event) => event.type === "patch").map((event) => event.patch);
  if (!members.length || events.length !== members.length + 1 || events.at(-1).type !== "complete" ||
      members.some((member) => member.operation !== "replace" || member.epoch !== members[0].epoch ||
        member.interactionSequence !== members[0].interactionSequence) ||
      new Set(members.map((member) => member.target)).size !== members.length) {
    fail("WOGE_TRANSACTION_REJECTED", "Transaction requires distinct replacements in one epoch and interaction");
  }
  return { members, viewTransition: patch.metadata === '{"viewTransition":true}' };
}

/** Animation is only a presentation wrapper; completion means the DOM callback ran, not animation end. */
export async function applyTransaction(registry, patch, document, signal) {
  const transaction = decodeTransaction(patch);
  const apply = () => {
    if (signal?.aborted) fail("WOGE_CANCELLED", "Transaction was cancelled");
    let plans;
    try {
      plans = transaction.members.map((member) => registry.preparePatch(member));
      // Never split stale intent: ignore the complete unit, not selected members.
      if (plans.includes("stale")) return "stale";
      const occupied = new Set();
      for (const plan of plans) {
        if (plans.some((other) => other !== plan && plan.target.contains(other.target))) {
          fail("WOGE_TRANSACTION_REJECTED", "Transaction targets cannot overlap");
        }
        for (const entry of plan.change.addedEntries) {
          if (occupied.has(entry.id)) fail("WOGE_TRANSACTION_REJECTED", "Transaction introduces duplicate regions");
          occupied.add(entry.id);
        }
      }
      // Before listeners can deliberately move focus. Validate browser state after all of them.
      plans.forEach((plan) => plan.before());
      plans.forEach((plan) => plan.check());
    } catch {
      fail("WOGE_TRANSACTION_REJECTED", "Transaction validation failed; no member was applied");
    }
    try {
      plans.forEach((plan) => { plan.check(false); plan.commit(); });
      plans.forEach((plan) => plan.focus());
      plans.forEach((plan) => plan.after());
      plans.forEach((plan) => plan.check(false));
    } catch {
      // Native/custom-element/controller failures after commit are visible, never successful completion.
      fail("WOGE_TRANSACTION_APPLY_FAILED", "Transaction commit failed; reload authoritative document state");
    }
  };
  return withViewTransition(document, transaction.viewTransition, apply);
}

export async function withViewTransition(document, enabled, apply) {
  if (!enabled || typeof document.startViewTransition !== "function" ||
      document.defaultView?.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return apply();
  let applied = false;
  let outcome;
  let problem;
  const update = () => {
    if (applied) return;
    applied = true;
    try { outcome = apply(); } catch (error) { problem = error; throw error; }
  };
  try {
    const transition = document.startViewTransition(update);
    transition.ready.catch(() => {});
    transition.finished.catch(() => {});
    await transition.updateCallbackDone;
  } catch {
    if (!applied) update();
  }
  if (problem) throw problem;
  return outcome;
}
