import { expect, test } from "@playwright/test";

test("renders the useful shell before applying every deferred region", async ({ page }) => {
  const browserProblems = [];
  page.on("console", (message) => {
    if (message.type() === "error") browserProblems.push(message.text());
  });
  page.on("pageerror", (problem) => browserProblems.push(problem.message));

  let releasePatchRequest;
  const patchRequestGate = new Promise((resolve) => {
    releasePatchRequest = resolve;
  });
  await page.route("**/projects/woge/woge-patches", async (route) => {
    await patchRequestGate;
    await route.continue();
  });

  const response = await page.goto("/projects/woge");
  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain("text/html");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Woge");
  await expect(page.locator('[data-woge-region][data-woge-revision="0"]')).toHaveCount(3);
  await expect(page.getByText("Loading from the server…")).toHaveCount(3);
  await expect(page.locator('link[rel="stylesheet"]')).toHaveAttribute("href", "/assets/application.css");
  await expect(page.locator('script[type="module"]')).toHaveAttribute("src", "/assets/application.js");

  releasePatchRequest();

  await expect(page.locator('[data-woge-region][data-woge-revision="1"]')).toHaveCount(3);
  await expect(page.getByRole("table", { name: "Current tasks for Woge" })).toBeVisible();
  await expect(page.getByText("Spring Boot adapter selected")).toBeVisible();
  await expect(page.getByText("Loading from the server…")).toHaveCount(0);
  await expect(page.locator(".is-loading")).toHaveCount(0);
  expect(browserProblems).toEqual([]);
});
