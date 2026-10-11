import { expect, test } from "@playwright/test";

test("forced native DPU and fallback have the same accessible outcome", async ({ page, browserName }) => {
  test.skip(browserName !== "chromium", "Native DPU conformance runs in Chromium only");
  await page.goto("/native-dpu?mode=fallback");
  const fallback = await semanticOutcome(page);
  expect(fallback.text).toContain("AlphaBetaGamma");
  expect(fallback.items).toEqual(["Alpha", "Beta", "Gamma"]);

  await page.goto("/native-dpu?mode=native");
  const supported = await page.evaluate(() => "htmlFor" in HTMLTemplateElement.prototype);
  test.skip(!supported, "This Chromium does not expose native declarative partial updates");
  const native = await semanticOutcome(page);
  expect(native).toEqual(fallback);
});

async function semanticOutcome(page) {
  const region = page.getByRole("region", { name: "Project results" });
  await expect(region.getByRole("listitem")).toHaveCount(3);
  return {
    text: (await region.innerText()).replaceAll(/\s+/gu, ""),
    items: await region.getByRole("listitem").allTextContents(),
    region: await region.ariaSnapshot().then((snapshot) => snapshot.split("\n")[0]),
  };
}
