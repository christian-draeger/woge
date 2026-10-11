import { expect, test, STRICT_CSP } from "../test-support/strict-csp.mjs";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

test("strict CSP permits replace and append through the named Trusted Types policy", async ({ page }) => {
  const response = await page.goto("/strict");
  expect(response.headers()["content-security-policy"]).toBe(STRICT_CSP);
  await page.waitForFunction(() => globalThis.Woge !== undefined);
  const bytes = encodeStream([
    patchFrame({ html: "<p>Replaced under CSP</p>" }),
    patchFrame({
      operation: "append", patchId: "append-1", baseRevision: 1, nextRevision: 2,
      itemId: "item-1", html: '<section data-woge-item="item-1">Appended under CSP</section>',
    }),
    completeFrame(2),
  ]);
  await page.evaluate(async (bytes) => {
    const runtime = Woge.createWogePatchRuntime(document);
    await runtime.applyPatchStream(byteStream(Uint8Array.from(bytes), "one-byte"));
  }, Array.from(bytes));
  await expect(page.locator("main")).toContainText("Replaced under CSP");
  await expect(page.locator('[data-woge-item="item-1"]')).toHaveText("Appended under CSP");
  expect(await page.evaluate(() => globalThis.trustedTypes?.defaultPolicy ?? null)).toBeNull();
  // A second runtime reuses the policy: CSP does not allow duplicate policy creation.
  const next = encodeStream([
    patchFrame({ patchId: "replace-2", baseRevision: 2, nextRevision: 3, html: "<p>Second runtime</p>" }),
    completeFrame(),
  ]);
  await page.evaluate(async (bytes) => {
    await Woge.createWogePatchRuntime(document).applyPatchStream(byteStream(Uint8Array.from(bytes), "one-byte"));
  }, Array.from(next));
  await expect(page.locator("main")).toHaveText("Second runtime");
});

test("Trusted Types does not bypass inert fragment checks", async ({ page }) => {
  await page.goto("/strict");
  await page.waitForFunction(() => globalThis.Woge !== undefined);
  const bytes = encodeStream([
    patchFrame({ html: '<p onclick="alert(1)">Not permitted</p>' }), completeFrame(),
  ]);
  const code = await page.evaluate(async (bytes) => {
    try {
      await Woge.createWogePatchRuntime(document).applyPatchStream(byteStream(Uint8Array.from(bytes), "one-byte"));
    } catch (error) { return error.code; }
  }, Array.from(bytes));
  expect(code).toBe("WOGE_ACTIVE_CONTENT");
  await expect(page.locator("main")).toHaveText("Original");
  await expect(page.locator("main")).toHaveAttribute("data-woge-revision", "0");
});
