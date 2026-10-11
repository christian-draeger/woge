import { expect, test } from "../test-support/strict-csp.mjs";

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
  await expect(page.getByText("Server adapter contract verified")).toBeVisible();
  await expect(page.locator("[data-woge-region]")).toHaveCount(0);
  await expect(page.getByText("Loading from the server…")).toHaveCount(0);
});

test("the same board action uses POST redirect GET and refresh never resubmits", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  const title = `Native ${test.info().project.name}`;
  const count = Number((await page.locator("#task-count").textContent()).split(" ")[0]);
  await page.getByLabel("Task title").fill(title);
  const response = page.waitForResponse((response) =>
    response.request().method() === "POST" && response.url().endsWith("/woge-actions/add-board-task"));
  await page.getByRole("button", { name: "Add task" }).click();
  expect((await response).status()).toBe(303);
  await expect(page).toHaveURL(/\/projects\/woge\/tasks$/);
  await expect(page.locator("#task-count")).toHaveText(`${count + 1} tasks`);
  await expect(page.locator("#board-tasks li", { hasText: title })).toHaveCount(1);
  await page.reload();
  await expect(page.locator("#task-count")).toHaveText(`${count + 1} tasks`);
  await expect(page.locator("#board-tasks li", { hasText: title })).toHaveCount(1);
});
