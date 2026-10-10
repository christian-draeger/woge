import { expect, test } from "@playwright/test";

test("renders a complete standards-native page without JavaScript", async ({ page }) => {
  const response = await page.goto("/");

  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain("text/html");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Hello from Woge");
  await expect(page.locator("noscript")).toHaveCount(1);
  await expect(page.locator('link[rel="stylesheet"]')).toHaveAttribute(
    "href",
    /^\/_woge\/assets\/[0-9a-f]{64}\/styles\.css$/,
  );
  await expect(page.locator("script")).toHaveCount(0);
  await expect(page.locator("main")).toHaveCSS("display", "grid");
  await expect(page.getByRole("listitem")).toHaveCount(3);
});
