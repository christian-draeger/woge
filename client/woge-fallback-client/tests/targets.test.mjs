import assert from "node:assert/strict";
import test from "node:test";
import { PageRegionRegistry } from "../src/dom.js";

const options = { timeout: 5_000 };

test("page epochs, unknown regions, cross-region interactions and revision regressions never mutate", options, () => {
  const { root, regions } = fakeDocument(["alpha", "beta"]);
  const registry = new PageRegionRegistry(root);
  const interaction = registry.beginInteraction(["alpha"]);
  const writes = regions.reduce((sum, region) => sum + region.writes, 0);

  for (const [candidate, code] of [
    [patch({ epoch: "other-page", target: "alpha", interactionSequence: 1n }), "WOGE_STALE_PAGE_EPOCH"],
    [patch({ target: "outside", interactionSequence: 1n }), "WOGE_UNKNOWN_TARGET"],
    [patch({ target: "beta", interactionSequence: 1n }), "WOGE_INTERACTION_MISMATCH"],
    [patch({ target: "alpha", interactionSequence: 1n, baseRevision: 1n, nextRevision: 2n }),
      "WOGE_REVISION_MISMATCH"],
  ]) {
    assert.throws(() => registry.applyPatch(candidate), { code });
  }
  assert.equal(regions.reduce((sum, region) => sum + region.writes, 0), writes);
  assert.deepEqual(interaction.targets.map((target) => target.target), ["alpha"]);
  assert.equal(registry.applyPatch(patch({ target: "alpha", interactionSequence: 0n })), "stale");
  assert.equal(regions.reduce((sum, region) => sum + region.writes, 0), writes);
});

test("duplicate and nested duplicate region identifiers fail during page registration", options, () => {
  for (const ids of [["same", "same"], ["outer", "same", "same"]]) {
    const { root } = fakeDocument(ids);
    assert.throws(() => new PageRegionRegistry(root), { code: "WOGE_DUPLICATE_TARGET" });
  }
});

function fakeDocument(ids) {
  const regions = ids.map((id) => {
    const attributes = new Map([
      ["data-woge-region", id],
      ["data-woge-revision", "0"],
      ["data-woge-interaction-sequence", "0"],
    ]);
    return {
      nodeType: 1,
      ownerDocument: null,
      isConnected: true,
      writes: 0,
      getAttribute(name) { return attributes.get(name) ?? null; },
      setAttribute(name, value) { this.writes += 1; attributes.set(name, value); },
    };
  });
  const epoch = { getAttribute: () => "epoch-a" };
  const root = {
    nodeType: 9,
    head: { querySelectorAll: () => [epoch] },
    querySelectorAll: () => regions,
  };
  regions.forEach((region) => { region.ownerDocument = root; });
  return { root, regions };
}

function patch({
  epoch = "epoch-a", target = "alpha", interactionSequence = 0n, baseRevision = 0n, nextRevision = 1n,
} = {}) {
  return {
    epoch, target, interactionSequence, baseRevision, nextRevision,
    operation: "replace", patchId: "patch-fuzz", html: "<p>unchanged</p>",
  };
}
