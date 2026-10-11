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
  await expect(page.locator("#note-status")).toHaveText("Note added");
  await expect(page.locator("#note-count")).toHaveText(`${title.length} / 120 UTF-16 code units`);
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


test("quickstart keeps its useful form when the app island module cannot load", async ({ page }) => {
  await page.route("**/assets/note-counter.js?v=2", (route) => route.abort());
  await page.goto("/notes");
  await expect(page.locator("#note-count")).toHaveText(
    "Up to 120 UTF-16 code units. Your note is checked on the server.");
  const title = `Fallback ${test.info().project.name}`;
  await page.getByLabel("New note").fill(title);
  await page.getByRole("button", { name: "Add note" }).click();
  await expect(page.locator("li", { hasText: title })).toHaveCount(1);
  await expect(page.locator("#note-count")).toHaveText(
    "Up to 120 UTF-16 code units. Your note is checked on the server.");
});


test("compiled Kotlin rules give immediate errors and the server revalidates bypassed input", async ({ page, request, baseURL }) => {
  const kotlinModule = page.waitForResponse((response) =>
    response.url().includes("/assets/note-rules/woge-woge-reference-shared-rules.mjs"));
  await page.goto("/notes");
  expect((await kotlinModule).status()).toBe(200);
  const input = page.getByLabel("New note");
  await input.fill(" ");
  await expect(page.locator('[data-draft-phase="REJECTED"]')).toHaveCount(1);
  await expect(page.locator("#note-count")).toContainText("text: MISSING — Enter a note.");
  await expect(input).toHaveAttribute("aria-invalid", "true");
  const invalid = await request.post("/woge-actions/add-note", {
    headers: { Origin: baseURL }, form: { text: " " },
  });
  expect(invalid.status()).toBe(400);
  expect(await invalid.text()).toContain("text: MISSING — Enter a note.");
  // Native maxlength and all local handlers are bypassed; the server still rejects the same rule.
  await input.evaluate((element) => element.removeAttribute("maxlength"));
  await input.fill("😀".repeat(61));
  await expect(page.locator("#note-count")).toContainText("text: MALFORMED — Use at most 120 UTF-16 code units.");
  const post = page.waitForResponse((response) => response.request().method() === "POST");
  await page.getByRole("button", { name: "Add note" }).click();
  expect((await post).status()).toBe(400);
  await expect(page.locator("#note-errors")).toBeFocused();
  await expect(page.locator("#note-errors")).toContainText("text: MALFORMED — Use at most 120 UTF-16 code units.");
  await input.fill("Corrected draft");
  await expect(page.locator('[data-draft-phase="READY"]')).toHaveCount(1);
  // READY means locally valid, never saved. Persistence remains a real POST.
  await expect(page.locator("li", { hasText: "Corrected draft" })).toHaveCount(0);
});
