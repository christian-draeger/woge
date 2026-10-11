import { expect, test } from "../test-support/strict-csp.mjs";
import {
  announcementChanges, assertAccessibilityContract, assertPageReflow, startAnnouncementAudit, pressTab, tabTo,
} from "../test-support/accessibility-contract.mjs";

test("the board journey is keyboard-only, including skip link, mutation and native overlays", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  await pressTab(page);
  await expect(page.getByRole("link", { name: "Skip to board content" })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.locator("#board-main")).toBeFocused();
  const input = page.getByLabel("Task title");
  await tabTo(page, input);
  const title = `Keyboard ${test.info().project.name}`;
  await page.keyboard.type(title);
  await pressTab(page);
  await expect(page.getByLabel("Owner", { exact: true })).toBeFocused();
  await pressTab(page);
  await expect(page.getByLabel("Priority", { exact: true })).toBeFocused();
  await pressTab(page);
  await expect(page.getByLabel("Status", { exact: true })).toBeFocused();
  await pressTab(page);
  const submit = page.getByRole("button", { name: "Add task" });
  await expect(submit).toBeFocused();
  await startAnnouncementAudit(page);
  await page.keyboard.press("Enter");
  await expect(page.locator("#board-form")).not.toHaveAttribute("aria-busy");
  await expect(page.locator("#board-tasks li", { hasText: title })).toHaveCount(1);
  await expect.poll(() => announcementChanges(page)).toEqual([{ id: "board-status", text: "Task added", writes: 1 }]);
  await expect(submit).toBeFocused();
  await pressTab(page);
  const summary = page.locator("details.board-faq > summary");
  await expect(summary).toBeFocused();
  await page.keyboard.press("Space");
  await expect(page.locator("details.board-faq")).toHaveAttribute("open", "");
  const help = page.getByRole("link", { name: "Board help" });
  await tabTo(page, help, { backwards: true });
  await page.keyboard.press("Enter");
  const dialog = page.getByRole("dialog", { name: "Board help" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Close" })).toBeFocused();
  // Typing while the native modal is open cannot reach or edit the background form.
  await page.keyboard.type("not in the form");
  await expect(input).toHaveValue(title);
  await assertAccessibilityContract(page);
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(help).toBeFocused();
  await pressTab(page, true);
  const popover = page.getByRole("button", { name: "Live updates" });
  await expect(popover).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.locator("#live-updates")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.locator("#live-updates")).toBeHidden();
  await expect(popover).toBeFocused();
});

test("all shipped primitives and dashboard content share the semantic and reflow contract", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 800 });
  await page.emulateMedia({ reducedMotion: "reduce", colorScheme: "dark" });
  await page.goto("/projects/woge/tasks");
  await assertPageReflow(page);
  await assertAccessibilityContract(page);
  await tabTo(page, page.getByRole("button", { name: "Live updates" }));
  await page.keyboard.press("Enter");
  await expect(page.locator("#live-updates")).toBeVisible();
  await assertPageReflow(page);
  await assertAccessibilityContract(page);
  await page.keyboard.press("Escape");
  await tabTo(page, page.getByRole("link", { name: "Board help" }));
  await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog")).toBeVisible();
  await assertPageReflow(page);
  await assertAccessibilityContract(page);
  await page.keyboard.press("Escape");

  await page.goto("/projects/woge?view=complete");
  await assertPageReflow(page);
  await assertAccessibilityContract(page);
  expect(await page.locator(".project-region").evaluateAll((regions) => regions.map((region) => ({
    animation: getComputedStyle(region).animationDuration,
    transition: getComputedStyle(region).transitionDuration,
  })))).toEqual(Array(3).fill({ animation: "0s", transition: "0s" }));
  await page.emulateMedia({ colorScheme: "light" });
  await assertAccessibilityContract(page);
});

test("dense dashboard rows and forms reflow without replacing native table semantics", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 800 });
  await page.goto("/projects/woge?view=complete");
  // Stress the actual dashboard CSS and table, rather than ship a second dashboard implementation.
  await page.evaluate(() => {
    const tbody = document.querySelector("tbody");
    for (let index = 0; index < 30; index++) {
      const row = tbody.firstElementChild.cloneNode(true);
      row.querySelector("th").textContent = `Task ${index}: ${"LongUnbrokenTitle".repeat(8)}`;
      tbody.append(row);
    }
    const section = document.createElement("section");
    section.className = "project-region";
    section.setAttribute("aria-label", "Dense form audit");
    const form = document.createElement("form");
    form.id = "board-form";
    for (let index = 0; index < 18; index++) {
      const label = document.createElement("label");
      label.htmlFor = `audit-field-${index}`;
      label.textContent = `Task title ${index + 1}`;
      const input = document.createElement("input");
      input.id = label.htmlFor;
      input.name = label.htmlFor;
      form.append(label, input);
    }
    section.append(form);
    document.querySelector("main").append(section);
  });
  await assertPageReflow(page);
  const table = page.getByRole("table", { name: "Current tasks for Woge" });
  await expect(table.locator('thead th[scope="col"]')).toHaveCount(3);
  await expect(table.locator('tbody th[scope="row"]')).toHaveCount(33);
  const scroll = page.getByRole("region", { name: "Tasks table" });
  await tabTo(page, scroll);
  await expect(scroll).toBeFocused();
  expect(await scroll.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
  for (let index = 0; index < 18; index++) {
    await pressTab(page);
    await expect(page.getByLabel(`Task title ${index + 1}`, { exact: true })).toBeFocused();
    await page.keyboard.type(`Draft ${index + 1}`);
  }
  await assertPageReflow(page);
  await assertAccessibilityContract(page);
});

test("background invalidation is silent even when the notice changes", async ({ page, context }) => {
  const connected = page.waitForResponse((response) => response.url().includes("/projects/woge/tasks/live/"));
  await page.goto("/projects/woge/tasks");
  await connected;
  await tabTo(page, page.getByLabel("Task title"));
  await page.keyboard.type("Draft stays");
  await startAnnouncementAudit(page);
  const other = await context.newPage();
  try {
    await other.goto("/projects/woge/tasks");
    await other.getByLabel("Task title").fill(`Quiet ${test.info().project.name}`);
    await other.getByLabel("Task title").press("Enter");
    await expect(other.locator("#board-status")).toHaveText("Task added");
    await expect(page.locator("#board-activity")).toContainText("1 task changed.");
    expect(await announcementChanges(page)).toEqual([]);
    await expect(page.getByLabel("Task title")).toBeFocused();
    await expect(page.getByLabel("Task title")).toHaveValue("Draft stays");
  } finally {
    await other.close();
  }
});

test("recovered full-page data has the same understandable outcome without extra live regions", async ({ page }) => {
  await page.route("**/projects/woge/woge-patches/*", (route) => route.abort("failed"));
  await page.goto("/projects/woge");
  await expect(page.getByText("Loading from the server…")).toHaveCount(3);
  await startAnnouncementAudit(page);
  await tabTo(page, page.getByRole("button", { name: "Load the complete page instead" }));
  expect(await announcementChanges(page)).toEqual([]);
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/view=complete$/);
  await expect(page.getByRole("table", { name: "Current tasks for Woge" })).toBeVisible();
  await expect(page.getByText("Loading from the server…")).toHaveCount(0);
  await expect(page.locator('[aria-live], [role="status"], [role="alert"]')).toHaveCount(0);
  await assertAccessibilityContract(page);
});
