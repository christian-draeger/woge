import { expect, test } from "../test-support/strict-csp.mjs";

test("quickstart native form uses POST 303 GET and refresh does not repeat the command", async ({ page }) => {
  await page.goto("/notes");
  await expect(page.locator("#note-count")).toHaveText(
    "Up to 120 UTF-16 code units. Your note is checked on the server.");
  const title = `Native ${test.info().project.name}`;
  await page.getByLabel("New note").fill(title);
  const post = page.waitForResponse((response) =>
    response.request().method() === "POST" && response.url().endsWith("/woge-actions/add-note"));
  await page.getByRole("button", { name: "Add note" }).click();
  expect((await post).status()).toBe(303);
  await expect(page).toHaveURL(/\/notes$/);
  await expect(page.locator("li", { hasText: title })).toHaveCount(1);
  await page.reload();
  await expect(page.locator("li", { hasText: title })).toHaveCount(1);
});

test("quickstart native validation returns the submitted value for correction", async ({ page }) => {
  await page.goto("/notes");
  const draft = "   ";
  const input = page.getByLabel("New note");
  await input.fill(draft);
  const post = page.waitForResponse((response) =>
    response.request().method() === "POST" && response.url().endsWith("/woge-actions/add-note"));
  await page.getByRole("button", { name: "Add note" }).click();

  expect((await post).status()).toBe(400);
  await expect(page.locator("#note-errors")).toContainText("Enter a note.");
  await expect(input).toHaveValue(draft);
});
