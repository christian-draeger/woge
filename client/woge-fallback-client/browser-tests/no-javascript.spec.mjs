import { expect, test } from "@playwright/test";

test("the marked form submits normally without executing its module", async ({ page }) => {
  await page.route("**/action", async (route) => {
    expect(route.request().method()).toBe("POST");
    expect(new URLSearchParams(route.request().postData()).get("intent")).toBe("save");
    expect(route.request().headers().accept).not.toContain("application/vnd.woge.patch-stream");
    await route.fulfill({ contentType: "text/html", body: "<h1>Native result</h1>" });
  });
  await page.goto("/action-forms");
  expect(await page.evaluate(() => globalThis.Woge)).toBeUndefined();
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("heading")).toHaveText("Native result");
});
