import { expect, test } from "@playwright/test";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

const markup = (type = "counter", key = "") => `<div data-woge-island="${type}" data-woge-island-loading="eager" ${key ? `data-woge-state-key="${key}"` : ""}>
<label>Note <input name="note" maxlength="120"></label><p role="status">Up to 120 characters</p>
<template data-woge-island-props>{"version":1,"value":{"maximum":"120"}}</template></div>`;
async function ready(page) {
  await page.goto("/islands");
  await page.waitForFunction(() => globalThis.islands !== undefined);
}
async function patch(page, html, revision = 0, options = {}) {
  const bytes = encodeStream([patchFrame({ epoch: "islands-epoch", target: "notes", html,
    baseRevision: revision, nextRevision: revision + 1, ...options }), completeFrame()]);
  return page.evaluate(async (bytes) => {
    try {
      await runtime.applyPatchStream(new ReadableStream({ start(controller) {
        controller.enqueue(Uint8Array.from(bytes)); controller.close();
      } }));
      return null;
    } catch (error) { return error.code; }
  }, Array.from(bytes));
}

test("server fallback is useful before loading; visible strategy imports only when revealed", async ({ page }) => {
  const imports = [];
  page.on("request", (request) => { if (request.url().includes("island-counter.js")) imports.push(request.url()); });
  await ready(page);
  await expect(page.locator('[role="status"]')).toHaveText("Up to 120 characters");
  expect(imports).toHaveLength(0);
  await page.locator("input").scrollIntoViewIfNeeded();
  await expect(page.locator('[role="status"]')).toHaveText("0 / 120");
  await page.locator("input").fill("Draft");
  await expect(page.locator('[role="status"]')).toHaveText("5 / 120");
  expect(imports).toHaveLength(1);
});

test("import errors and unknown types retain useful fallback without importing patch URLs", async ({ page }) => {
  await ready(page);
  expect(await patch(page, markup("missing"))).toBeNull();
  await expect.poll(() => page.evaluate(() => islandErrors)).toContain("WOGE_ISLAND_IMPORT_FAILED");
  await expect(page.locator('[role="status"]')).toHaveText("Up to 120 characters");
  const imports = [];
  page.on("request", (request) => { if (request.url().includes("evil")) imports.push(request.url()); });
  expect(await patch(page, markup("unknown").replace('<div ', '<div data-woge-module="/evil.js" '), 1)).toBeNull();
  await expect.poll(() => page.evaluate(() => islandErrors)).toContain("WOGE_ISLAND_UNKNOWN_TYPE");
  await expect(page.locator('[role="status"]')).toHaveText("Up to 120 characters");
  expect(imports).toHaveLength(0);
});

test("dispose happens before replacement; new root mounts; preserved keys retain controller and local DOM", async ({ page }) => {
  await ready(page);
  expect(await patch(page, markup())).toBeNull();
  await expect(page.locator('[role="status"]')).toHaveText("0 / 120");
  expect(await patch(page, markup(), 1)).toBeNull();
  await expect.poll(() => page.evaluate(() => disposals)).toEqual([true]);
  await expect.poll(() => page.evaluate(() => mountCalls)).toBe(2);
  await page.locator("input").fill("Draft");
  await page.locator("main").evaluate((node) => node.setAttribute("data-woge-state", "reset"));
  expect(await patch(page, markup("counter", "counter-1"), 2)).toBeNull();
  await page.locator("main").evaluate((node) => node.removeAttribute("data-woge-state"));
  await expect.poll(() => page.evaluate(() => mountCalls)).toBe(3);
  await page.locator("input").fill("Local");
  await page.locator("[data-woge-island]").evaluate((root) => { globalThis.originalIsland = root; });
  expect(await patch(page, markup("counter", "counter-1"), 3)).toBeNull();
  await expect(page.locator("input")).toHaveValue("Local");
  expect(await page.evaluate(() => document.querySelector("[data-woge-island]") === originalIsland)).toBe(true);
  expect(await page.evaluate(() => mountCalls)).toBe(3);
  await page.evaluate(() => islands.update(originalIsland, { maximum: "150" }));
  await expect(page.locator('[role="status"]')).toHaveText("5 / 150");
  await page.locator("main").evaluate((node) => node.setAttribute("data-woge-state", "reset"));
  expect(await patch(page, markup("counter", "counter-1"), 4)).toBeNull();
  await expect.poll(() => page.evaluate(() => mountCalls)).toBe(4);
  await expect(page.locator("input")).toHaveValue("");
});

test("failed mount restores fallback and aborts registered listeners", async ({ page }) => {
  await page.route("**/island-counter.js?v=1", (route) => route.fulfill({ contentType: "text/javascript", body:
    'export function mount(root, props, {signal}) { root.replaceChildren(document.createElement("button")); throw new Error("mount"); }' }));
  await ready(page);
  expect(await patch(page, markup())).toBeNull();
  await expect.poll(() => page.evaluate(() => islandErrors)).toContain("WOGE_ISLAND_MOUNT_FAILED");
  await expect(page.locator('[role="status"]')).toHaveText("Up to 120 characters");
  await expect(page.locator("input")).toBeVisible();
});

test("pending lazy import cannot mount a removed root", async ({ page }) => {
  let release;
  await page.route("**/island-counter.js?v=1", async (route) => {
    await new Promise((resolve) => { release = resolve; });
    await route.fulfill({ contentType: "text/javascript", body: 'export function mount() { globalThis.lateMount = true; }' });
  });
  await ready(page);
  expect(await patch(page, markup())).toBeNull();
  await expect.poll(() => Boolean(release)).toBe(true);
  expect(await patch(page, '<p>Still useful HTML</p>', 1)).toBeNull();
  release();
  await expect(page.locator("main")).toHaveText("Still useful HTML");
  expect(await page.evaluate(() => globalThis.lateMount ?? false)).toBe(false);
});

test("malformed props and ownership conflicts are reported without changing fallback", async ({ page }) => {
  await ready(page);
  expect(await patch(page, markup().replace('"version":1', '"version":2'))).toBeNull();
  await expect.poll(() => page.evaluate(() => islandErrors)).toContain("WOGE_ISLAND_PROPS_INVALID");
  await expect(page.locator('[role="status"]')).toHaveText("Up to 120 characters");
  expect(await patch(page, markup().replace("<label>", '<section data-woge-region="nested" data-woge-revision="0"></section><label>'), 1)).toBeNull();
  await expect.poll(() => page.evaluate(() => islandErrors)).toContain("WOGE_ISLAND_OWNERSHIP_INVALID");
});

test("collection removal disposes keyed roots before disconnect; append discovers only the new root", async ({ page }) => {
  await ready(page);
  const html = markup("counter", "row-counter").replace('<div ', '<div data-woge-item="row" ');
  expect(await patch(page, html, 0, { operation: "append", itemId: "row" })).toBeNull();
  await expect.poll(() => page.evaluate(() => mountCalls)).toBe(1);
  expect(await patch(page, "", 1, { operation: "remove", itemId: "row", focusTarget: "notes" })).toBeNull();
  await expect.poll(() => page.evaluate(() => disposals)).toEqual([true]);
  await expect(page.locator('[data-woge-state-key="row-counter"]')).toHaveCount(0);
});

test("manifest is captured once; patch scripts remain forbidden; repeated scan never double mounts", async ({ page }) => {
  await ready(page);
  await page.evaluate(() => {
    document.querySelector("#woge-islands").textContent = '{"unknown":{"url":"/evil.js?v=1","mount":"mount","propsVersion":1}}';
  });
  expect(await patch(page, markup("unknown"))).toBeNull();
  await expect.poll(() => page.evaluate(() => islandErrors)).toContain("WOGE_ISLAND_UNKNOWN_TYPE");
  expect(await patch(page, '<script type="application/json" id="woge-islands">{}</script>', 1))
    .toBe("WOGE_ACTIVE_CONTENT");
  expect(await patch(page, markup(), 1)).toBeNull();
  await expect.poll(() => page.evaluate(() => mountCalls)).toBe(1);
  await page.evaluate(() => { islands.scan(); islands.scan(); });
  expect(await page.evaluate(() => mountCalls)).toBe(1);
  await page.evaluate(() => { islands.dispose(); islands.dispose(); });
  expect(await page.evaluate(() => disposals)).toEqual([true]);
});

test("server HTML remains useful with JavaScript disabled", async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  try {
    const page = await context.newPage();
    await page.goto("http://127.0.0.1:4173/islands");
    await expect(page.getByRole("heading")).toHaveText("Useful server HTML");
    await expect(page.locator('[role="status"]')).toHaveText("Up to 120 characters");
    await page.locator("input").fill("Native draft");
    await expect(page.locator("input")).toHaveValue("Native draft");
  } finally { await context.close(); }
});
