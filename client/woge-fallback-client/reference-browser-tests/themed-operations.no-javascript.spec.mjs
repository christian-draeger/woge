import { expect, test } from "../test-support/strict-csp.mjs";

for (const theme of ["", "?theme=tailwind"]) {
  test(`ordinary themed create, filter, sort, detail and edit ${theme || "plain CSS"}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 320, height: 900 });
    await page.goto(`/projects/woge/tasks${theme}`);
    const title = `HTML Northstar ${theme ? "brand" : "plain"} ${testInfo.project.name}`;
    await page.getByLabel("Task title", { exact: true }).fill(title);
    await page.getByLabel("Owner", { exact: true }).fill("Sam");
    await page.getByRole("button", { name: "Add task" }).click();
    await page.getByLabel("Filter tasks", { exact: true }).fill(title);
    await page.getByRole("button", { name: "Apply filter" }).click();
    await expect(page).toHaveURL(/query=/);
    await expect(page.locator("#board-tasks > li")).toHaveCount(1);
    await page.getByRole("link", { name: "Sort title Z–A" }).click();
    await expect(page).toHaveURL(/descending=true/);
    await page.getByRole("link", { name: `Details for ${title}`, exact: true }).click();
    await expect(page.getByRole("heading", { name: "Task details", exact: true })).toBeVisible();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(page.getByLabel("Owner", { exact: true })).toHaveValue("Sam");
    await page.getByLabel("Status", { exact: true }).selectOption("Done");
    await page.getByRole("button", { name: "Save task" }).click();
    await expect(page.locator("#board-tasks > li").filter({ hasText: title })).toContainText("Done");
    await page.getByRole("link", { name: "Return to all tasks" }).click();
    await expect(page.getByRole("navigation", { name: "Task pages", exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320);
    await page.getByRole("link", { name: "Board help", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Board help", exact: true })).toBeVisible();
    await expect(page.locator("body")).toHaveAttribute("data-theme", theme ? "tailwind" : "css");
  });
}
