import { expect, test } from "@playwright/test";

const report = "/backlog";
const island = '[data-woge-state-key="warehouse-backlog"]';

test.use({
  channel: "chromium",
  launchOptions: { ignoreDefaultArgs: ["--disable-back-forward-cache"] },
});

test("returning to the report restores or remounts a working chart", async ({ page, browser }) => {
  await page.addInitScript(() => {
    window.backlogDocument = crypto.randomUUID();
    window.backlogPageShows = [];
    addEventListener("pageshow", (event) => window.backlogPageShows.push(event.persisted));
  });
  await page.goto(report);
  await page.getByRole("heading", { name: "Backlog trend" }).scrollIntoViewIfNeeded();
  const packing = page.getByRole("button", { name: "Show packing orders" });
  await packing.click();
  await expect(packing).toHaveAttribute("aria-pressed", "false");
  const original = await page.locator(island).evaluate((root) => {
    window.originalChart = root;
    return window.backlogDocument;
  });

  await page.goto(`${report}?window=late`);
  await expect(page.getByRole("table", { name: "Open orders by day, June 15–28" })).toBeVisible();
  await page.goBack({ waitUntil: "commit" });
  await expect(page.getByRole("table", { name: "Open orders by day, June 1–14" })).toBeVisible();
  await page.getByRole("heading", { name: "Backlog trend" }).scrollIntoViewIfNeeded();
  await expect.poll(() => page.evaluate(() => window.backlogPageShows.at(-1))).toBeDefined();

  const returned = await page.locator(island).evaluate((root) => ({
    document: window.backlogDocument,
    sameRoot: root === window.originalChart,
    persisted: window.backlogPageShows.at(-1),
  }));
  expect(returned.document === original).toBe(returned.persisted);
  expect(returned.sameRoot).toBe(returned.persisted);
  await expect(packing).toHaveAttribute("aria-pressed", returned.persisted ? "false" : "true");
  await packing.click();
  await expect(packing).toHaveAttribute("aria-pressed", returned.persisted ? "true" : "false");
  await page.getByLabel("Date range").selectOption("late");
  const submitted = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/woge-actions/select-backlog-snapshot",
  );
  await page.getByRole("button", { name: "Show backlog" }).click();
  const status = (await submitted).status();
  expect([200, 303]).toContain(status);
  await expect(page.getByRole("table", { name: "Open orders by day, June 15–28" })).toBeVisible();
  console.log(JSON.stringify({
    chromium: browser.version(),
    backlogHistoryRestored: returned.persisted,
    dateSubmissionAfterReturn: status,
  }));
});
