import { test, expect } from "@playwright/test";
test("intentional missing button produces a locator diagnostic and retained trace", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Expected missing button", exact: true })).toBeVisible({ timeout: 250 });
});
