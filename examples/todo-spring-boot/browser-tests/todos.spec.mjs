import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";

test("one form journey adds, filters, completes and deletes a todo", async ({ page }, testInfo) => {
  const title = `Read <HTML> & CSS ${randomUUID()}`;
  await page.goto("/");
  const epoch = await page.locator('meta[name="woge-page-epoch"]').getAttribute("content");
  await page.getByLabel("New todo").fill(title);
  await page.getByRole("button", { name: "Add todo", exact: true }).click();
  let row = page.locator("[data-todo-id]").filter({ hasText: title });
  await expect(row).toHaveCount(1);
  if (testInfo.project.name.endsWith("-enhanced")) {
    await expect(page.locator('meta[name="woge-page-epoch"]')).toHaveAttribute("content", epoch);
    await expect(page.locator("#todo-status")).toHaveText("Todo added.");
  } else {
    await expect(page.locator('meta[name="woge-page-epoch"]')).not.toHaveAttribute("content", epoch);
  }
  await row.getByRole("button", { name: "Complete", exact: true }).click();
  await expect(row).toContainText("(completed)");
  await page.getByRole("link", { name: "Active", exact: true }).click();
  await expect(page.locator("[data-todo-id]").filter({ hasText: title })).toHaveCount(0);
  await page.getByRole("link", { name: "Completed", exact: true }).click();
  row = page.locator("[data-todo-id]").filter({ hasText: title });
  await expect(row).toHaveCount(1);
  await row.getByRole("button", { name: "Delete", exact: true }).click();
  await expect(row).toHaveCount(0);
});

test("server validation preserves text and connects error summary to the field", async ({ page }, testInfo) => {
  await page.goto("/");
  await page.getByLabel("New todo").fill("   ");
  await page.getByRole("button", { name: "Add todo", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Check your todo" })).toBeVisible();
  await expect(page.getByLabel("New todo")).toHaveValue("   ");
  await expect(page.getByLabel("New todo")).toHaveAttribute("aria-invalid", "true");
  await expect(page.locator("#todo-errors a")).toHaveAttribute("href", "#todo-title");
  if (testInfo.project.name.endsWith("-enhanced")) {
    await expect(page.locator("#todo-errors")).toBeFocused();
  }
  await page.getByLabel("New todo").fill(`Recovered ${randomUUID()}`);
  await page.getByRole("button", { name: "Add todo", exact: true }).click();
  await expect(page.locator("#todo-errors")).toHaveCount(0);
  await expect(page.getByLabel("New todo")).not.toHaveAttribute("aria-invalid", "true");
});

test("another tab updates the board while an unsaved draft remains intact", async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name.endsWith("-native"), "Native pages refresh through ordinary navigation.");
  await page.goto("/");
  const draft = `Unsubmitted ${randomUUID()}`;
  const count = (await page.locator("#todo-count").textContent()).match(/(\d+) remaining · (\d+) total/);
  expect(count).not.toBeNull();
  await page.getByLabel("New todo").fill(draft);
  const other = await context.newPage();
  await other.goto("/");
  const title = `From another tab ${randomUUID()}`;
  await other.getByLabel("New todo").fill(title);
  await other.getByRole("button", { name: "Add todo", exact: true }).click();
  await expect(page.locator("[data-todo-id]").filter({ hasText: title })).toHaveCount(1);
  await expect(page.locator("#todo-count")).toHaveText(`${Number(count[1]) + 1} remaining · ${Number(count[2]) + 1} total`);
  await expect(page.getByLabel("New todo")).toHaveValue(draft);
  await expect(page.getByLabel("New todo")).toBeFocused();
  await other.close();
});

for (const input of ["pointer", "keyboard"]) {
  test(`the stable add form submits once through a live update by ${input}`, async ({ page, context }, testInfo) => {
    test.skip(testInfo.project.name.endsWith("-native"), "Native pages refresh through ordinary navigation.");
    const draft = `Local ${randomUUID()}`;
    const external = `External ${randomUUID()}`;
    const button = page.getByRole("button", { name: "Add todo", exact: true });
    const other = await context.newPage();
    await page.goto("/");
    await other.goto("/");
    await page.getByLabel("New todo").fill(draft);
    if (input === "pointer") {
      await button.hover();
      await page.mouse.down();
    } else {
      await button.focus();
      await page.keyboard.down("Space");
    }
    await other.getByLabel("New todo").fill(external);
    await other.getByRole("button", { name: "Add todo", exact: true }).click();
    await expect(page.locator("[data-todo-id]").filter({ hasText: external })).toHaveCount(1);
    await expect(page.getByLabel("New todo")).toHaveValue(draft);

    const submissions = [];
    page.on("request", (request) => {
      if (request.method() === "POST" && new URL(request.url()).pathname === "/woge-actions/add-todo") {
        submissions.push(request);
      }
    });
    if (input === "pointer") {
      await page.mouse.up();
    } else {
      await expect(button).toBeFocused();
      await page.keyboard.up("Space");
    }
    await expect(page.locator("[data-todo-id]").filter({ hasText: draft })).toHaveCount(1);
    expect(submissions).toHaveLength(1);
    await other.close();
  });
}

test("a retained form accepts a touch after cancellation while the live board converges", async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== "chromium-enhanced", "Split touchscreen gestures use Chromium CDP.");
  const draft = `Touch draft ${randomUUID()}`;
  const cancelledUpdate = `During cancelled touch ${randomUUID()}`;
  const completedUpdate = `During completed touch ${randomUUID()}`;
  const input = page.getByLabel("New todo");
  const button = page.getByRole("button", { name: "Add todo", exact: true });
  const other = await context.newPage();
  await page.goto("/");
  await other.goto("/");
  await input.fill(draft);
  await button.scrollIntoViewIfNeeded();
  const bounds = await button.boundingBox();
  expect(bounds).not.toBeNull();
  const touchPoints = [{ x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 }];
  const cdp = await context.newCDPSession(page);
  await cdp.send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 1 });
  await button.evaluate((element) => {
    element.addEventListener("pointercancel", (event) => {
      if (event.isTrusted && event.pointerType === "touch") element.dataset.touchCancelled = "true";
    }, { once: true });
  });

  const submissions = [];
  page.on("request", (request) => {
    if (request.method() === "POST" && new URL(request.url()).pathname === "/woge-actions/add-todo") {
      submissions.push(request);
    }
  });

  await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints });
  await other.getByLabel("New todo").fill(cancelledUpdate);
  await other.getByRole("button", { name: "Add todo", exact: true }).click();
  await expect(page.locator("[data-todo-id]").filter({ hasText: cancelledUpdate })).toHaveCount(1);
  await cdp.send("Input.dispatchTouchEvent", { type: "touchCancel", touchPoints: [] });
  await expect(button).toHaveAttribute("data-touch-cancelled", "true");
  await expect(input).toHaveValue(draft);
  expect(submissions).toHaveLength(0);

  await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints });
  await other.getByLabel("New todo").fill(completedUpdate);
  await other.getByRole("button", { name: "Add todo", exact: true }).click();
  await expect(page.locator("[data-todo-id]").filter({ hasText: completedUpdate })).toHaveCount(1);
  await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
  await expect(page.locator("[data-todo-id]").filter({ hasText: draft })).toHaveCount(1);
  expect(submissions).toHaveLength(1);
  await other.close();
});

test("removing a pressed live row cancels activation without submitting", async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name.endsWith("-native"), "Native pages refresh through ordinary navigation.");
  const title = `Removed ${randomUUID()}`;
  const other = await context.newPage();
  await other.goto("/");
  await other.getByLabel("New todo").fill(title);
  await other.getByRole("button", { name: "Add todo", exact: true }).click();
  const otherRow = other.locator("[data-todo-id]").filter({ hasText: title });
  await expect(otherRow).toHaveCount(1);

  await page.goto("/");
  const row = page.locator("[data-todo-id]").filter({ hasText: title });
  await row.getByRole("button", { name: "Complete", exact: true }).hover();
  const posts = [];
  page.on("request", (request) => {
    if (request.method() === "POST") posts.push(request);
  });
  await page.mouse.down();
  await otherRow.getByRole("button", { name: "Delete", exact: true }).click();
  await expect(row).toHaveCount(0);
  await page.mouse.up();
  expect(posts).toHaveLength(0);
  await other.close();
});
