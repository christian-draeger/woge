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

for (const name of ["View fast result", "View slow result"]) {
  test(`deferred content is reachable through ${name} without JavaScript`, async ({ page }) => {
    const patchRequests = [];
    page.on("request", (request) => {
      if (request.url().includes("deferred-patches")) patchRequests.push(request);
    });
    await page.goto("/deferred");
    const [response] = await Promise.all([
      page.waitForNavigation(),
      page.getByRole("link", { name }).click(),
    ]);
    expect(response.request().method()).toBe("GET");
    expect(response.status()).toBe(200);
    expect(response.headers()["content-type"]).toContain("text/html");
    await expect(page.getByRole("heading")).toHaveText("Deferred results");
    await expect(page.locator('[aria-label="Fast result"]')).toHaveText("Fast result");
    await expect(page.locator('[aria-label="Slow result"]')).toHaveText("Slow result");
    await expect(page.locator("script")).toHaveCount(0);
    expect(patchRequests).toEqual([]);
    await page.reload();
    await expect(page.locator('[aria-label="Slow result"]')).toHaveText("Slow result");
  });
}
