import { expect, test } from "@playwright/test";
import { announcementChanges, startAnnouncementAudit, pressTab, tabTo } from "../test-support/accessibility-contract.mjs";

test.beforeEach(async ({ page }) => {
  await page.goto("/action-forms");
  await page.waitForFunction(() => globalThis.actions !== undefined);
});

test("one user action creates one polite outcome, not progress or patch speech", async ({ page }) => {
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  await page.route("**/action", async (route) => { await gate; await route.continue(); });
  await startAnnouncementAudit(page);
  const submit = page.getByRole("button", { name: "Save", exact: true });
  await tabTo(page, submit);
  await page.keyboard.press("Enter");
  await expect(page.locator("form")).toHaveAttribute("aria-busy", "true");
  await expect(submit).toHaveAttribute("aria-disabled", "true");
  expect(await announcementChanges(page)).toEqual([]);
  release();
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  await expect.poll(() => announcementChanges(page)).toEqual([{ id: "status", text: "Saved", writes: 1 }]);
  await expect(page.locator('[role="status"], [role="alert"], [aria-live]')).toHaveCount(2);
});

test("validation is silent in live regions independently of summary focus", async ({ page }) => {
  await page.locator("form").evaluate((form) => { form.action = "/action?validation"; });
  await startAnnouncementAudit(page);
  await tabTo(page, page.getByRole("button", { name: "Save", exact: true }));
  await page.keyboard.press("Enter");
  await expect(page.locator("#errors")).toHaveText("Check the formTitle is required");
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  expect(await announcementChanges(page)).toEqual([]);
  await expect(page.locator("#errors")).not.toHaveAttribute("role");
  await expect(page.locator("#errors")).not.toHaveAttribute("aria-live");
});

test("validation summary and its field link are keyboard-complete without using live speech", async ({ page }) => {
  // WebKitGTK automation drops typed keys after native fragment focus; Safari on macOS edits the field.
  const editable = !(test.info().project.name === "webkit" && process.platform === "linux");
  await page.locator("form").evaluate((form) => { form.action = "/action?validation"; });
  await tabTo(page, page.getByRole("button", { name: "Save", exact: true }));
  await page.keyboard.press("Enter");
  await expect(page.locator("#errors")).toBeFocused();
  await pressTab(page);
  await expect(page.locator("#errors a")).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.locator("#title")).toBeFocused();
  if (!editable) return;
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("Corrected title");
  await expect(page.locator("#title")).toHaveValue("Corrected title");
});

test("failure has exactly one urgent outcome and leaves the keyboard usable", async ({ page }) => {
  await page.route("**/action", (route) => route.abort("failed"));
  await startAnnouncementAudit(page);
  const submit = page.getByRole("button", { name: "Save", exact: true });
  await tabTo(page, submit);
  await page.keyboard.press("Enter");
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  await expect.poll(() => announcementChanges(page)).toEqual([{
    id: "alert", text: "The result is unknown. Check before submitting again.", writes: 1,
  }]);
  await expect(submit).toBeFocused();
  await pressTab(page, true);
  await expect(page.locator('input[name="_csrf"]')).toBeFocused();
});

test("enhanced navigation uses the native document focus baseline, not summary focus", async ({ page }) => {
  await page.route("**/action", (route) => route.fulfill({
    status: 200, headers: { "Woge-Navigate": "/complete" }, body: "",
  }));
  await page.route("**/complete", (route) => route.fulfill({
    contentType: "text/html",
    body: '<!doctype html><html lang="en"><title>Complete</title><main><h1>Complete</h1><a href="/">Continue</a></main></html>',
  }));
  await tabTo(page, page.getByRole("button", { name: "Save", exact: true }));
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/complete$/);
  await expect(page).toHaveTitle("Complete");
  expect(await page.evaluate(() => document.activeElement === document.body)).toBe(true);
  await pressTab(page);
  await expect(page.getByRole("link", { name: "Continue" })).toBeFocused();
});

test("superseded action and validation responses create no announcement opportunities", async ({ page }) => {
  for (const action of ["/action?stale", "/action?validation&stale"]) {
    await page.reload();
    await page.waitForFunction(() => globalThis.actions !== undefined);
    await page.locator("form").evaluate((form, action) => { form.action = action; }, action);
    await startAnnouncementAudit(page);
    await tabTo(page, page.getByRole("button", { name: "Save", exact: true }));
    await page.keyboard.press("Enter");
    await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
    expect(await announcementChanges(page)).toEqual([]);
    await expect(page.locator("main")).toHaveText("Original");
  }
});

test("enhanced navigation stays silent while waiting and adds no competing live outcome", async ({ page }) => {
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  await page.route("**/action", async (route) => {
    await gate;
    await route.fulfill({ status: 200, headers: { "Woge-Navigate": "/complete" }, body: "" });
  });
  await page.route("**/complete", (route) => route.fulfill({
    contentType: "text/html",
    body: '<!doctype html><html lang="en"><title>Complete</title><main><h1>Complete</h1></main></html>',
  }));
  await startAnnouncementAudit(page);
  await tabTo(page, page.getByRole("button", { name: "Save", exact: true }));
  await page.keyboard.press("Enter");
  await expect(page.locator("form")).toHaveAttribute("aria-busy", "true");
  expect(await announcementChanges(page)).toEqual([]);
  release();
  await expect(page).toHaveURL(/\/complete$/);
  await expect(page.locator('[aria-live], [role="alert"], [role="status"]')).toHaveCount(0);
});
