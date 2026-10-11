import { expect, test } from "../test-support/strict-csp.mjs";

test("quickstart enhances the same safe form without navigating", async ({ page }) => {
  const response = await page.goto("/notes");
  expect(response.headers()["cache-control"]).toBe("no-store");
  const title = `Enhanced ${test.info().project.name}`;
  await page.getByLabel("New note").fill(title);
  const post = page.waitForResponse((response) =>
    response.request().method() === "POST" && response.url().endsWith("/woge-actions/add-note"));
  const navigations = [];
  page.on("framenavigated", (frame) => { if (frame === page.mainFrame()) navigations.push(frame.url()); });
  await page.getByLabel("New note").press("Enter");
  const result = await post;
  expect(result.status()).toBe(200);
  expect(result.headers()["content-type"]).toContain("application/vnd.woge.patch-stream");
  await expect(page.locator("li", { hasText: title })).toHaveCount(1);
  await expect(page.getByRole("status")).toHaveText("Note added");
  await expect(page.getByLabel("New note")).toBeFocused();
  expect(navigations).toEqual([]);
  await page.reload();
  await expect(page.locator("li", { hasText: title })).toHaveCount(1);
});

test("quickstart rejects cross-origin and invalid native posts before mutation", async ({ page, request, baseURL }) => {
  await page.goto("/notes");
  const title = `Forbidden ${test.info().project.name}`;
  const forbidden = await request.post("/woge-actions/add-note", {
    headers: { Origin: "https://untrusted.example" }, form: { text: title },
  });
  expect(forbidden.status()).toBe(403);
  const invalid = await request.post("/woge-actions/add-note", {
    headers: { Origin: baseURL }, form: { text: " " },
  });
  expect(invalid.status()).toBe(400);
  await page.reload();
  await expect(page.locator("li", { hasText: title })).toHaveCount(0);
});
