import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(new URL("../../client/woge-fallback-client/package.json", import.meta.url));
const { chromium, expect } = require("@playwright/test");
const origin = new URL(process.argv[2]).origin;
const url = `${origin}/todo-study/deferred`;
const success = "10 todos on the board.";
const failure = "Todo summary unavailable.";

async function load(page, fail, expected) {
    await page.addInitScript(() => {
        window.todoStudy = {};
        new MutationObserver(() => {
            if (document.querySelector("main h1")?.textContent === "Todo overview") {
                window.todoStudy.shellMs ??= performance.now();
            }
            const text = document.querySelector("#todo-summary p")?.textContent;
            if (text === "Loading Todo summary…") window.todoStudy.loadingMs ??= performance.now();
            if (text === "10 todos on the board." || text === "Todo summary unavailable.") {
                window.todoStudy.completedMs ??= performance.now();
            }
        }).observe(document, { subtree: true, childList: true, characterData: true });
    });
    const started = performance.now();
    const response = await page.goto(fail ? `${url}?fail=true` : url);
    assert.equal(response.status(), 200);
    await expect(page.getByRole("heading", { name: "Todo overview" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Open the Todo board" })).toBeVisible();
    await expect(page.locator("#todo-summary")).toHaveText(expected);
    const timings = await page.evaluate(() => window.todoStudy);
    assert.ok(timings.shellMs <= timings.loadingMs && timings.loadingMs + 250 < timings.completedMs,
        `Region arrived before the useful shell: ${JSON.stringify(timings)}`);
    return { ...timings, journeyMs: performance.now() - started };
}

const browser = await chromium.launch();
try {
    const enhanced = await browser.newContext();
    const page = await enhanced.newPage();
    const failed = await enhanced.newPage();
    const enhancedSuccess = await load(page, false, success);
    const enhancedFailure = await load(failed, true, failure);
    assert.equal(page.url(), url);
    assert.equal(failed.url(), `${url}?fail=true`);
    await expect(failed.getByRole("link", { name: "Open the complete Todo overview" })).toBeVisible();
    await enhanced.close();

    const native = await browser.newContext({ javaScriptEnabled: false });
    const fallback = await native.newPage();
    await fallback.goto(url);
    await expect(fallback.locator("#todo-summary")).toHaveText("Loading Todo summary…");
    const began = performance.now();
    const nav = fallback.waitForNavigation();
    await fallback.getByRole("link", { name: "Open the complete Todo overview" }).click();
    assert.equal((await nav).status(), 200);
    await expect(fallback.locator("#todo-summary")).toHaveText(success);
    const nativeClickToCompleteMs = performance.now() - began;
    const unavailable = await fallback.goto(`${url}?complete=true&fail=true`);
    assert.equal(unavailable.status(), 503);
    await expect(fallback.locator("#todo-summary")).toHaveText(failure);
    await expect(fallback.getByRole("link", { name: "Open the Todo board" })).toBeVisible();
    await native.close();

    console.log(JSON.stringify({ browser: browser.version(), enhancedSuccess, enhancedFailure,
        nativeClickToCompleteMs, nativeFailureStatus: unavailable.status() }));
} finally {
    await browser.close();
}
