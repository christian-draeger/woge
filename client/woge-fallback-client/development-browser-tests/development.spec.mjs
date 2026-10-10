import { test, expect } from "@playwright/test";

test("multiple tabs refresh only on success and failed builds leave dirty controls usable", async ({ page, context, request }) => {
  await page.goto("/");
  await expect(page.locator("#woge-development-status")).toBeVisible();
  const beforeSave = await page.locator("#build").textContent();
  await request.get("/control?save");
  await expect(page.locator("#build")).not.toHaveText(beforeSave);
  await expect(page.locator("#woge-development-status")).toHaveAttribute("data-phase", "READY");
  const second = await context.newPage();
  await second.goto("http://127.0.0.1:4273/");
  await expect(page.locator("#woge-development-status")).toHaveAttribute("data-phase", "READY");
  await expect(second.locator("#woge-development-status")).toHaveAttribute("data-phase", "READY");
  const initial = await page.locator("#build").textContent();
  await page.getByLabel("Name").fill("unsaved");
  await request.get("/control?fail");
  await expect(page.getByRole("status")).toContainText("Build failed");
  await expect(second.getByRole("status")).toContainText("Build failed");
  await expect(page.locator("#build")).toHaveText(initial);
  await expect(page.getByLabel("Name")).toHaveValue("unsaved");
  await expect(page.locator("#woge-development-status ul")).toContainText("src/main/Page.kt:4:8");
  await expect(page.locator("#woge-development-status img")).toHaveCount(0);
  await request.get("/control?save");
  await expect(page.locator("#build")).not.toHaveText(initial);
  await expect(second.locator("#build")).not.toHaveText(initial);
  await expect(page.getByRole("status")).toHaveText("Ready");
  // A complete reload intentionally makes no claim about retaining dirty controls.
  await second.close();
});

test("rapid saves settle on one newest ready document", async ({ page, request }) => {
  await page.goto("/");
  await expect(page.locator("#woge-development-status")).toBeVisible();
  const before = await page.locator("#build").textContent();
  await Promise.all(Array.from({ length: 8 }, () => request.get("/control?save")));
  await expect(page.locator("#build")).not.toHaveText(before);
  await expect(page.getByRole("status")).toHaveText("Ready");
});

test("native EventSource reconnect resumes current state after an offline edit", async ({ page, context, request }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  const before = await page.locator("#build").textContent();
  await context.setOffline(true);
  await request.get("/control?save");
  await page.waitForTimeout(500);
  await context.setOffline(false);
  await expect(page.locator("#build")).not.toHaveText(before, { timeout: 10000 });
  await expect(page.getByRole("status")).toHaveText("Ready");
});

test("the overlay is dismissible by keyboard without stealing form focus", async ({ page, request }) => {
  await page.goto("/");
  await page.getByLabel("Name").focus();
  await request.get("/control?fail");
  await expect(page.getByRole("status")).toContainText("Build failed");
  await expect(page.getByLabel("Name")).toBeFocused();
  await page.getByRole("button", { name: "Hide development status" }).focus();
  await page.keyboard.press("Enter");
  await expect(page.locator("#woge-development-status")).toBeHidden();
  await request.get("/control?save");
});

test("stale, duplicate and out-of-order identities cannot trigger a document reload", async ({ page }) => {
  await page.addInitScript(() => {
    class ControlledEventSource extends EventTarget {
      constructor() { super(); window.testSource = this; }
      close() {}
    }
    window.EventSource = ControlledEventSource;
  });
  await page.goto("/");
  await page.waitForFunction(() => window.testSource);
  await page.evaluate(async () => {
    const { connectDevelopmentClient } = await import("http://127.0.0.1:4274/client.js");
    window.reloads = 0;
    connectDevelopmentClient({
      endpoint: "http://127.0.0.1:4274/events", build: "10", generation: "10", overlay: false,
    }, { reload: () => { window.reloads++; } });
    const emit = (sequence, build, generation) => window.testSource.dispatchEvent(
      new MessageEvent("snapshot", { data: JSON.stringify({
        version: 1, session: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        sequence, build, renderedBuild: build, generation, phase: "READY", diagnostics: [],
      }) }),
    );
    emit("20", "10", "10");
    emit("19", "11", "11");
    emit("20", "11", "11");
    emit("21", "9", "11");
    emit("21", "11", "9");
    emit("21", "11", "11");
    emit("22", "12", "12");
  });
  expect(await page.evaluate(() => window.reloads)).toBe(1);
});
