import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

async function apply(page, patch) {
  const bytes = encodeStream([patchFrame(patch), completeFrame()]);
  return page.evaluate((bytes) => runtime.applyPatchStream(byteStream(Uint8Array.from(bytes))), Array.from(bytes));
}

for (const variant of ["css", "tailwind"]) test.describe(`${variant} component contracts`, () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/");
    await page.waitForFunction(() => globalThis.Woge !== undefined);
    await page.evaluate(async (variant) => {
      document.head.innerHTML = '<title>Component contracts</title><meta name="woge-page-epoch" content="epoch-a">';
      document.body.innerHTML = await (await fetch(`/component-${variant}`)).text();
      globalThis.originalComponents = document.querySelector("#components").innerHTML;
      globalThis.runtime = Woge.createWogePatchRuntime(document);
    }, variant);
    for (const name of variant === "css" ? ["navigation", "overlay", "feedback"] : ["tailwind"]) {
      await page.addStyleTag({ url: `/component-${name}.css` });
    }
  });

  test("dialog and drawer trap focus, close on Escape and return focus without changing history", async ({ page }) => {
    const requests = [];
    page.on("request", (request) => requests.push(request.url()));
    await page.evaluate(async () => (await import("/dialog.js")).installWogeDialogs());
    for (const name of ["help", "drawer"]) {
      const trigger = page.getByRole("link", { name: `Open ${name}` });
      await trigger.click();
      await expect(page.locator(`#${name}`)).toBeVisible();
      const close = page.getByRole("button", { name: `Close ${name}` });
      await expect(close).toBeFocused();
      await page.keyboard.press("Tab");
      // Some engines include a document focus stop between cycles; a second Tab re-enters the dialog.
      await page.keyboard.press("Tab");
      expect(await page.evaluate((name) => document.querySelector(`#${name}`).contains(document.activeElement), name))
        .toBe(true);
      await page.mouse.click(1, 1);
      // Modal dialogs deliberately require an explicit close or Escape, unlike auto popovers.
      await expect(page.locator(`#${name}`)).toBeVisible();
      await page.keyboard.press("Escape");
      await expect(page.locator(`#${name}`)).toBeHidden();
      await expect(trigger).toBeFocused();
      await trigger.click();
      await close.click();
      await expect(trigger).toBeFocused();
    }
    await expect(page).toHaveURL("http://127.0.0.1:4173/");
    expect(requests.filter((url) => url.endsWith("/dialog.js"))).toHaveLength(1);
  });

  test("native popover and explicit tooltip support keyboard, outside dismissal and description", async ({ page }) => {
    const button = page.getByRole("button", { name: "Options" });
    await button.focus();
    await page.keyboard.press("Enter");
    await expect(page.locator("#options")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.locator("#options")).toBeHidden();
    await expect(button).toBeFocused();
    await button.click();
    await page.mouse.click(1, page.viewportSize().height - 1);
    await expect(page.locator("#options")).toBeHidden();
    const tip = page.getByRole("button", { name: "Tip" });
    await expect(tip).toHaveAccessibleDescription("Helpful description");
    await tip.focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("tooltip")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(tip).toBeFocused();
  });

  test("server replacement closes removed overlays and focuses the replacement trigger", async ({ page }) => {
    await page.evaluate(async () => (await import("/dialog.js")).installWogeDialogs());
    await page.getByRole("link", { name: "Open help" }).click();
    await page.evaluate(() => { globalThis.removedDialog = document.querySelector("#help"); });
    const html = await page.evaluate(() => originalComponents);
    await apply(page, { target: "components", html });
    await expect(page.getByRole("link", { name: "Open help" })).toBeFocused();
    expect(await page.evaluate(() => [removedDialog.isConnected, removedDialog.open])).toEqual([false, false]);
    await expect(page.locator("dialog[open]")).toHaveCount(0);
    await page.getByRole("button", { name: "Options" }).click();
    await page.evaluate(() => { globalThis.removedPopover = document.querySelector("#options"); });
    await apply(page, { target: "components", baseRevision: 1, nextRevision: 2, html: "Updated content" });
    expect(await page.evaluate(() => [removedPopover.isConnected, removedPopover.matches(":popover-open")]))
      .toEqual([false, false]);
    await expect(page.locator(":popover-open")).toHaveCount(0);
  });

  test("stable announcement roots survive repeat and stale patches without nested live regions", async ({ page }) => {
    await expect(page.getByRole("status")).toHaveText("");
    await expect(page.getByRole("alert")).toHaveText("");
    await apply(page, { target: "notice", html: "Saved" });
    await apply(page, { target: "notice", html: "Saved" }); // stale replay is ignored
    await apply(page, { target: "notice", baseRevision: 1, nextRevision: 2, html: "Saved again" });
    await expect(page.getByRole("status")).toHaveCount(1);
    await expect(page.getByRole("status")).toHaveText("Saved again");
    await expect(page.getByRole("alert")).toHaveCount(1);
    await expect(page.locator("#toast [role], #notice [role]")).toHaveCount(0);
    await expect(page.locator("#notice")).toHaveAttribute("data-woge-revision", "2");
    await expect(apply(page, { target: "components", html:
      '<div data-woge-region="notice" data-woge-revision="0" role="status">Duplicate</div>' }))
      .rejects.toThrow();
    await expect(page.getByRole("status")).toHaveCount(1);
  });

  test("reduced motion disables skeleton animation and drawer uses logical RTL positioning", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await expect(page.locator('[data-part="skeleton"]')).toHaveCSS("animation-name", "none");
    await page.evaluate(async () => {
      document.documentElement.dir = "rtl";
      (await import("/dialog.js")).installWogeDialogs();
    });
    await page.getByRole("link", { name: "Open drawer" }).click();
    const box = await page.locator("#drawer").boundingBox();
    expect(box.x).toBeLessThan(2);
    await expect(page.locator("#drawer")).toHaveCSS("transition-duration", "0s");
  });

  test("native URL tabs navigate and return with optional navigation installed", async ({ page }) => {
    await page.evaluate(async () => (await import("/woge-navigation.js")).installWogeNavigation());
    await page.getByRole("navigation", { name: "Views" }).getByRole("link", { name: "Next" }).click();
    await expect(page).toHaveURL(/\/navigation\/b$/);
    await page.goBack();
    await expect(page).toHaveURL("http://127.0.0.1:4173/");
  });

  test("static and open overlay markup passes automated accessible-name contracts", async ({ page }) => {
    expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze()).violations)
      .toEqual([]);
    await page.evaluate(async () => (await import("/dialog.js")).installWogeDialogs());
    await page.getByRole("link", { name: "Open help" }).click();
    expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze()).violations)
      .toEqual([]);
  });
});

test.describe("no JavaScript", () => {
  test.use({ javaScriptEnabled: false });
  test("native disclosure, popover and dialog fallback links need no optional behavior", async ({ page }) => {
    const requests = [];
    page.on("request", (request) => requests.push(request.url()));
    await page.goto("/component-css");
    await page.getByText("More", { exact: true }).click();
    await expect(page.locator("details")).toHaveAttribute("open", "");
    await page.getByRole("button", { name: "Options" }).click();
    await expect(page.locator("#options")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.locator("#options")).toBeHidden();
    await page.getByRole("link", { name: "Open help" }).click();
    await expect(page).toHaveURL(/\/navigation\/b$/);
    expect(requests.some((url) => url.endsWith(".js"))).toBe(false);
  });
});
