import AxeBuilder from "@axe-core/playwright";
import { expect } from "@playwright/test";

// Shared by native and enhanced pages; do not exclude controls or live regions from this contract.
export async function assertAccessibilityContract(page, { javaScriptEnabled = true } = {}) {
  if (javaScriptEnabled) {
    const results = await new AxeBuilder({ page })
      .options({ preload: false })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "best-practice"])
      .analyze();
    await page.bringToFront();
    expect(results.violations.map(({ id, nodes }) => ({ id, targets: nodes.map((node) => node.target) }))).toEqual([]);
  }
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.locator("main")).toHaveCount(1);
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
  const references = await page.evaluate(() => {
    const ids = Array.from(document.querySelectorAll("[id]"), (element) => element.id);
    const problems = ids.filter((id, index) => ids.indexOf(id) !== index);
    for (const element of document.querySelectorAll("[aria-labelledby], [aria-describedby], label[for]")) {
      for (const attribute of ["aria-labelledby", "aria-describedby", "for"]) {
        for (const id of (element.getAttribute(attribute) ?? "").split(/\s+/).filter(Boolean)) {
          if (!document.getElementById(id)) problems.push(`${attribute}: ${id}`);
        }
      }
    }
    const visible = (element) => element.getClientRects().length > 0;
    for (const control of document.querySelectorAll("input:not([type=hidden]), select, textarea")) {
      if (!visible(control)) continue;
      if (!control.getAttribute("aria-label") && !control.getAttribute("aria-labelledby") &&
          !Array.from(control.labels ?? []).some((label) => label.textContent.trim())) {
        problems.push(`unlabelled control: ${control.id}`);
      }
    }
    for (const element of document.querySelectorAll("a, button, summary")) {
      if (visible(element) && !element.textContent.trim() && !element.getAttribute("aria-label") &&
          !element.getAttribute("aria-labelledby")) problems.push(`unnamed ${element.tagName}`);
    }
    return problems;
  });
  expect(references, "unique IDs and existing label/error targets").toEqual([]);
}

// WebKit on macOS defaults to text controls only; Option+Tab enables native all-controls navigation.
export async function pressTab(page, backwards = false) {
  const prefix = page.context().browser().browserType().name() === "webkit" ? "Alt+" : "";
  await page.keyboard.press(`${backwards ? "Shift+" : ""}${prefix}Tab`);
}

export async function tabTo(page, locator, limit = 30) {
  for (let index = 0; index < limit; index++) {
    await pressTab(page);
    if (await locator.evaluate((element) => element === document.activeElement)) return;
  }
  throw new Error(`Not keyboard reachable after ${limit} Tab presses: ${locator}`);
}

export async function assertPageReflow(page) {
  expect(await page.evaluate(() => ({
    viewport: window.innerWidth,
    width: document.documentElement.scrollWidth,
    viewportMeta: document.querySelector('meta[name="viewport"]')?.content,
  }))).toEqual({ viewport: 320, width: 320, viewportMeta: "width=device-width, initial-scale=1" });
}

// DOM evidence of announcement opportunities, not a claim about what a screen reader speaks.
export async function startAnnouncementAudit(page) {
  await page.evaluate(() => {
    globalThis.announcementAudit?.disconnect();
    globalThis.announcementChanges = [];
    const selector = '[role="status"], [role="alert"], [role="log"], [aria-live]:not([aria-live="off"])';
    globalThis.announcementAudit = new MutationObserver((records) => {
      for (const region of document.querySelectorAll(selector)) {
        const writes = records.filter((record) => region === record.target || region.contains(record.target)).length;
        const text = region.textContent.trim();
        if (writes && text) globalThis.announcementChanges.push({ id: region.id, text, writes });
      }
    });
    globalThis.announcementAudit.observe(document.body, { childList: true, characterData: true, subtree: true });
  });
}

export async function announcementChanges(page) {
  return page.evaluate(() => globalThis.announcementChanges);
}
