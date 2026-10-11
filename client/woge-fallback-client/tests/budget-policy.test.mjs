import { test } from "node:test";
import assert from "node:assert/strict";
import { sizes, verifyBudgets, verifyGraph } from "../scripts/budget-policy.mjs";

test("byte gates reject drift, ceiling changes, unregistered assets and Tailwind growth", () => {
  const actual = { "plain-card.css": sizes(Buffer.from("abc")), "tailwind-card.css": sizes(Buffer.from("abcd")) };
  const baseline = {
    tolerance: .1, tailwindMaxRatio: 10, tailwindAllowanceBytes: { raw: 100, gzip: 100, brotli: 100 },
    assets: Object.fromEntries(Object.entries(actual).map(([name, measured]) => [name, {
      measured, ceiling: { raw: 100, gzip: 100, brotli: 100 },
    }])),
  };
  verifyBudgets(actual, baseline);
  assert.throws(() => verifyBudgets({ ...actual, extra: sizes(Buffer.from("x")) }, baseline), /inventory/);
  assert.throws(() => verifyBudgets({ ...actual, "plain-card.css": { ...actual["plain-card.css"], raw: 5 } }, baseline), /exceeds/);
  baseline.assets["plain-card.css"].ceiling.raw = 2;
  assert.throws(() => verifyBudgets(actual, baseline), /exceeds/);
  baseline.assets["plain-card.css"].ceiling.raw = 100;
  baseline.tailwindMaxRatio = 0;
  baseline.tailwindAllowanceBytes = { raw: 0, gzip: 0, brotli: 0 };
  assert.throws(() => verifyBudgets(actual, baseline), /Tailwind/);
});

test("runtime graphs cannot hide bundled frameworks or external imports", () => {
  verifyGraph({ metafile: { inputs: { "dialog.js": {} }, outputs: { "dialog.js": { imports: [] } } } });
  assert.throws(() => verifyGraph({ metafile: { inputs: { "node_modules/react/index.js": {} }, outputs: {} } }), /dependency/);
  assert.throws(() => verifyGraph({ metafile: { inputs: {}, outputs: { "x.js": { imports: [{}] } } } }), /external/);
});
