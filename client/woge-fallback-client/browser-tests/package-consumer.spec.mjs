import { expect, test } from "@playwright/test";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

test("applies a patch through a freshly packed installed and bundled consumer", async ({ page }) => {
  await page.goto("/package-consumer");
  await page.waitForFunction(() => globalThis.WogePackageConsumer !== undefined);
  const bytes = encodeStream([
    patchFrame({
      epoch: "package-page",
      target: "package-region",
      html: "<p>Installed package works</p>",
    }),
    completeFrame(1),
  ]);

  const result = await page.evaluate(async (values) => {
    const runtime = globalThis.WogePackageConsumer.createWogePatchRuntime(document);
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(Uint8Array.from(values));
        controller.close();
      },
    });
    const completion = await runtime.applyPatchStream(stream);
    return {
      protocolVersion: globalThis.WogePackageConsumer.protocolVersion,
      patchCount: completion.patchCount,
    };
  }, Array.from(bytes));

  expect(result).toEqual({ protocolVersion: 1, patchCount: 1 });
  await expect(page.getByText("Installed package works")).toBeVisible();
});
