import { expect, test } from "@playwright/test";

test("an island resolves optimistic success and validation rejection from actual server patches", async ({ page }) => {
  let release;
  await page.route("**/signals-action", async (route) => {
    await new Promise((resolve) => { release = resolve; });
    await route.continue();
  });
  await page.goto("/signals");
  await expect(page.locator('[data-woge-island]')).toHaveAttribute("data-ready", "true");
  await page.getByRole("button", { name: "Add note" }).click();
  await expect(page.locator('[data-woge-island] [role="status"]')).toHaveText("1 notes (1 pending)");
  await expect(page.locator("[data-authoritative-count]")).toHaveText("0 saved notes");
  await expect.poll(() => Boolean(release)).toBe(true);
  release();
  await expect(page.locator('[data-woge-island] [role="status"]')).toHaveText("1 notes (0 pending)");
  await expect(page.locator("[data-authoritative-count]")).toHaveAttribute("data-authoritative-count", "1");
  release = undefined;
  await page.getByRole("textbox", { name: "Note" }).fill("reject");
  await page.getByRole("button", { name: "Add note" }).click();
  await expect(page.locator('[data-woge-island] [role="status"]')).toHaveText("2 notes (1 pending)");
  await expect.poll(() => Boolean(release)).toBe(true);
  release();
  await expect(page.locator('[data-woge-island] [role="status"]')).toHaveText("1 notes (0 pending)");
  await expect(page.getByRole("alert")).toHaveText("NOTE_REJECTED");
  await expect(page.locator("#errors")).toHaveText("The server rejected this note");
});

test("a newer applied response supersedes older optimism and ignores its late stale patch", async ({ page }) => {
  let release;
  await page.route("**/signals-action", async (route) => {
    if (route.request().postData() === "note=first") await new Promise((resolve) => { release = resolve; });
    await route.continue();
  });
  await page.goto("/signals");
  await expect(page.locator('[data-woge-island]')).toHaveAttribute("data-ready", "true");
  await page.getByRole("button").click();
  await expect(page.locator('[data-woge-island] [role="status"]')).toHaveText("1 notes (1 pending)");
  await expect.poll(() => Boolean(release)).toBe(true);
  await page.getByRole("textbox").fill("second");
  await page.getByRole("button").click();
  await expect(page.locator('[data-woge-island] [role="status"]')).toHaveText("2 notes (0 pending)");
  const olderResponse = page.waitForResponse((response) => response.url().endsWith("/signals-action") &&
    response.request().postData() === "note=first");
  release(); await olderResponse;
  await expect(page.locator("[data-woge-island]")).toHaveAttribute("data-settled", "2");
  await expect(page.locator("[data-woge-region]")).toHaveAttribute("data-woge-revision", "1");
  await expect(page.locator('[data-woge-island] [role="status"]')).toHaveText("2 notes (0 pending)");
  await expect(page.getByRole("alert")).toBeEmpty();
  await expect(page.locator("[data-authoritative-count]")).toHaveAttribute("data-authoritative-count", "2");
  await page.evaluate(() => islands.dispose());
  await page.getByRole("textbox").fill("first");
  await expect(page.locator('[data-woge-island] [role="status"]')).toHaveText("2 notes (0 pending)");
});
