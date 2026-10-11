import { expect, test } from "../test-support/strict-csp.mjs";

test("primitives stay useful without JavaScript", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  await page.getByText("What happens when I add a task?").click();
  await expect(page.getByText("Without it, the browser posts the form")).toBeVisible();

  await page.getByRole("button", { name: "Live updates" }).click();
  await expect(page.locator("#live-updates")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.locator("#live-updates")).toBeHidden();

  await page.getByRole("link", { name: "Board help" }).click();
  await expect(page).toHaveURL(/\/projects\/woge\/tasks\?view=help$/);
  await expect(page.getByRole("heading", { level: 2, name: "Board help" })).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
});
