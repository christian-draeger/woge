import { expect, test } from "../test-support/strict-csp.mjs";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

for (const nativeDpuAvailable of [false, true]) {
  test(`forces the fallback with native DPU capability ${nativeDpuAvailable ? "present" : "absent"}`,
    async ({ page }) => {
      await page.addInitScript((available) => {
        Object.defineProperty(HTMLTemplateElement.prototype, "htmlFor", {
          configurable: true, value: available ? "" : undefined,
        });
      }, nativeDpuAvailable);
      await page.goto("/strict?runtime=fallback");
      await page.waitForFunction(() => globalThis.forcedFallbackRuntime !== undefined);
      expect(await page.evaluate(() => typeof HTMLTemplateElement.prototype.htmlFor === "string"))
        .toBe(nativeDpuAvailable);
      const bytes = encodeStream([
        patchFrame({ html: "<p>Fallback applied</p>" }),
        patchFrame({ patchId: "old", html: "<p>Must stay stale</p>" }),
        completeFrame(2),
      ]);
      const result = await page.evaluate(async (bytes) =>
        forcedFallbackRuntime.applyPatchStream(byteStream(Uint8Array.from(bytes), "one-byte")),
      Array.from(bytes));
      expect(result.patchCount).toBe(2);
      expect(result.stalePatchCount).toBe(1);
      await expect(page.locator("main")).toHaveText("Fallback applied");
      await expect(page.locator("main")).toHaveAttribute("data-woge-revision", "1");
    });
}
