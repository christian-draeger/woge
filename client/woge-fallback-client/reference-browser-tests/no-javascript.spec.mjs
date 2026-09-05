import { expect, test } from "@playwright/test";

test("uses an ordinary full navigation when JavaScript is unavailable", async ({ page }) => {
  const response = await page.goto("/projects/woge");
  expect(response.status()).toBe(200);
  const completePageLink = page.getByRole("link", { name: "Load all project data as one complete page." });
  await expect(completePageLink).toBeVisible();
  await expect(page.getByRole("button", { name: "Load the complete page instead" })).toBeVisible();
  await expect(page.locator('[data-woge-region][data-woge-revision="0"]')).toHaveCount(3);
  await expect(page.getByText("Loading from the server…")).toHaveCount(3);

  await completePageLink.click();

  await expect(page).toHaveURL(/\/projects\/woge\?view=complete$/);
  await expect(page.getByRole("table", { name: "Current tasks for Woge" })).toBeVisible();
  await expect(page.getByText("Spring Boot adapter selected")).toBeVisible();
  await expect(page.locator("[data-woge-region]")).toHaveCount(0);
  await expect(page.getByText("Loading from the server…")).toHaveCount(0);
});
