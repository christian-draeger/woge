import { expect, test } from "@playwright/test";

test("disclosure and popover use native keyboard and focus behavior", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  const summary = page.getByText("What happens when I add a task?");
  await summary.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByText("only the task list, the count and the status line change")).toBeVisible();

  const button = page.getByRole("button", { name: "Live updates" });
  await button.focus();
  await page.keyboard.press("Enter");
  const panel = page.locator("#live-updates");
  await expect(panel).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(panel).toBeHidden();
  await expect(button).toBeFocused();
});

test("the help link opens a modal dialog and returns focus, even to a replaced link", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  const link = page.getByRole("link", { name: "Board help" });
  await link.click();
  const dialog = page.getByRole("dialog", { name: "Board help" });
  await expect(dialog).toBeVisible();
  await expect(page).toHaveURL(/\/projects\/woge\/tasks$/);
  await expect(dialog.getByRole("button", { name: "Close" })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(link).toBeFocused();

  await link.click();
  await expect(dialog).toBeVisible();
  // A patch may replace the link while the dialog is open.
  await page.evaluate(() => {
    const old = document.querySelector("a[data-woge-dialog]");
    old.replaceWith(old.cloneNode(true));
  });
  await dialog.getByRole("button", { name: "Close" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("link", { name: "Board help" })).toBeFocused();
});

test("primitives keep their state while board actions patch the page", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  await page.getByText("What happens when I add a task?").click();
  const count = Number((await page.locator("#task-count").textContent()).split(" ")[0]);
  await page.getByLabel("Task title").fill(`Primitive ${test.info().project.name}`);
  await page.getByRole("button", { name: "Add task" }).click();
  await expect(page.locator("#task-count")).toHaveText(`${count + 1} tasks`);
  await expect(page.locator("details.board-faq")).toHaveAttribute("open", "");

  await page.getByRole("link", { name: "Board help" }).click();
  await expect(page.getByRole("dialog", { name: "Board help" })).toBeVisible();
  await expect(page).toHaveURL(/\/projects\/woge\/tasks$/);
});

test("primitives work under a strict Content-Security-Policy", async ({ page }) => {
  await page.route("**/projects/woge/tasks", async (route) => {
    const response = await route.fetch();
    await route.fulfill({
      response,
      headers: {
        ...response.headers(),
        "content-security-policy":
          "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'none'",
      },
    });
  });
  await page.addInitScript(() => {
    window.wogeViolations = [];
    document.addEventListener("securitypolicyviolation", (event) => {
      window.wogeViolations.push(`${event.violatedDirective} ${event.blockedURI}`);
    });
  });
  await page.goto("/projects/woge/tasks");
  await page.getByRole("link", { name: "Board help" }).click();
  await expect(page.getByRole("dialog", { name: "Board help" })).toBeVisible();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Live updates" }).click();
  await expect(page.locator("#live-updates")).toBeVisible();
  expect(await page.evaluate(() => window.wogeViolations)).toEqual([]);
});
