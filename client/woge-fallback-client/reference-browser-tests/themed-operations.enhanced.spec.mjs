import { expect, test } from "../test-support/strict-csp.mjs";
import { assertAccessibilityContract } from "../test-support/accessibility-contract.mjs";

for (const theme of ["", "?theme=tailwind"]) {
  test(`operations layout and preferences ${theme || "plain CSS"}`, async ({ page, browserName }) => {
    await page.goto(`/projects/woge/tasks${theme}`);
    for (const width of [320, 768, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
      await expect(page.getByRole("button", { name: "Add task" })).toBeVisible();
      await expect(page.getByRole("link", { name: "Board help" })).toBeVisible();
    }
    await page.emulateMedia({ colorScheme: "dark", contrast: "more", reducedMotion: "reduce" });
    expect(await page.locator(".project-region").evaluate((node) => getComputedStyle(node).transitionDuration)).toBe("0s");
    expect(await page.locator("#board-form fieldset").first().evaluate((node) => getComputedStyle(node).borderTopWidth)).toBe("2px");
    expect(await page.locator("body").evaluate((node) => getComputedStyle(node).backgroundColor)).toBe(
      theme ? "rgb(4, 47, 46)" : "rgb(17, 24, 39)",
    );
    await assertAccessibilityContract(page);
    if (browserName !== "webkit") {
      await page.emulateMedia({ forcedColors: "active" });
      await expect(page.getByRole("button", { name: "Add task" })).toBeVisible();
      expect(await page.evaluate(() => matchMedia("(forced-colors: active)").matches)).toBe(true);
    }
    const summary = page.getByText("What happens when I add a task?");
    await summary.focus();
    await page.keyboard.press("Enter");
    await expect(page.getByText("Northstar operations handbook")).toBeVisible();
    const scope = page.locator('[data-woge-scope="woge-disclosure-card"]');
    expect(await scope.evaluate((node) => getComputedStyle(node).getPropertyValue("--woge-card-radius").trim())).toBe("0.85rem");
  });
}

test("themed keyboard create, detail, edit and live update keep their patch targets", async ({ page, context }, testInfo) => {
  await page.goto("/projects/woge/tasks?theme=tailwind");
  const title = `Northstar ${testInfo.project.name}`;
  const targets = await page.locator("[data-woge-region]").evaluateAll((nodes) => nodes.map((node) => node.dataset.wogeRegion));
  await page.getByLabel("Task title", { exact: true }).focus();
  await page.keyboard.type(title);
  await page.getByLabel("Owner", { exact: true }).fill("Rhea");
  await page.getByLabel("Priority", { exact: true }).selectOption("Urgent");
  await page.getByRole("button", { name: "Add task" }).focus();
  await page.keyboard.press("Enter");
  await expect(page.locator("#board-status")).toHaveText("Task added");
  expect(await page.locator("[data-woge-region]").evaluateAll((nodes) => nodes.map((node) => node.dataset.wogeRegion))).toEqual(targets);
  const detail = page.getByRole("link", { name: `Details for ${title}`, exact: true });
  await detail.focus();
  await page.keyboard.press("Enter");
  const dialog = page.getByRole("dialog", { name: title, exact: true });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText("Rhea · Urgent · Open");
  await page.keyboard.press("Escape");
  await expect(detail).toBeFocused();
  await page.keyboard.press("Enter");
  await dialog.getByRole("link", { name: "Edit task", exact: true }).focus();
  await page.keyboard.press("Enter");
  await page.getByLabel("Status", { exact: true }).selectOption("Done");
  await page.getByRole("button", { name: "Save task" }).focus();
  await page.keyboard.press("Enter");
  await expect(page.locator("#board-status")).toHaveText("Task updated");
  const row = page.getByRole("row").filter({ has: page.getByRole("rowheader", { name: title, exact: true }) });
  await expect(row).toContainText("Done");
  const other = await context.newPage();
  try {
    await other.goto("/projects/woge/tasks");
    await other.getByLabel("Task title", { exact: true }).fill(`Activity ${testInfo.project.name}`);
    await other.getByRole("button", { name: "Add task" }).click();
    await expect(other.locator("#board-status")).toHaveText("Task added");
    await expect(page.locator("#board-activity")).toContainText("task changed.");
    await expect(row).toContainText("Done");
  } finally { await other.close(); }
});
