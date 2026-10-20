import { chromium, firefox, webkit, expect } from "@playwright/test";
import { writeFileSync, mkdirSync } from "node:fs";
import { randomUUID } from "node:crypto";
const results = [];
for (const [engine, type] of Object.entries({ chromium, firefox, webkit })) {
  const browser = await type.launch();
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto("about:blank");
    await page.evaluate(() => {
      document.body.innerHTML = '<div id="pointer-fixture"><button>Complete</button></div><p id="activations">0</p>';
      document.addEventListener("click", event => {
        if (event.target.closest("#pointer-fixture button")) document.querySelector("#activations").textContent = "1";
      });
    });
    const native = page.getByRole("button", { name: "Complete", exact: true });
    await native.hover();
    await page.mouse.down();
    await page.locator("#pointer-fixture").evaluate(element => element.innerHTML = '<button>Complete</button>');
    await page.mouse.up();
    await expect(page.locator("#activations")).toHaveText("0");
    await native.click();
    await expect(page.locator("#activations")).toHaveText("1");
    await page.goto(process.env.WOGE_TODO_URL);
    const title = `Pointer ${randomUUID()}`;
    await page.getByLabel("New todo").fill(title);
    await page.getByRole("button", { name: "Add todo", exact: true }).click();
    const row = page.locator("[data-todo-id]").filter({ hasText: title });
    await expect(row).toHaveCount(1);
    const button = row.getByRole("button", { name: "Complete", exact: true });
    const posts = [];
    page.on("request", request => { if (request.method() === "POST" && request.url().includes("/toggle-todo")) posts.push(request.url()); });
    await button.hover();
    await page.evaluate(() => document.addEventListener("pointerdown", event => { window.probeDownTarget = event.target; }, { once: true }));
    await page.mouse.down();
    const update = await context.request.post(`${process.env.WOGE_TODO_URL}/woge-actions/add-todo`, {
      form: { title: `External pointer ${randomUUID()}` }, headers: { Origin: new URL(process.env.WOGE_TODO_URL).origin }, maxRedirects: 0,
    });
    expect(update.status()).toBe(303);
    await expect.poll(() => page.evaluate(() => !!window.probeDownTarget && !window.probeDownTarget.isConnected)).toBe(true);
    await page.mouse.up();
    await expect(row).not.toContainText("(completed)");
    expect(posts).toHaveLength(0);
    await button.click();
    await expect(row).toContainText("(completed)");
    expect(posts).toHaveLength(1);
    results.push({ engine, version: browser.version(), nativeReplacement: "activation canceled", wogeLiveReplacement: "activation canceled; no POST", deliberateNewClick: "one successful POST" });
  } finally { await browser.close(); }
}
mkdirSync("build/evidence", { recursive: true });
writeFileSync("build/evidence/pointer-probe.json", JSON.stringify(results, null, 2));
console.log(JSON.stringify(results));
