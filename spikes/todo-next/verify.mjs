import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(new URL("../../client/woge-fallback-client/package.json", import.meta.url));
const { chromium } = require("playwright");
const origin = new URL(process.argv[2]).origin;
const addPath = "/woge-actions/add-todo";
const timings = {};

async function time(name, action) {
    const started = performance.now();
    await action();
    timings[name] = performance.now() - started;
}

async function add(page, value, status) {
    await page.getByLabel("New todo").fill(value);
    const response = page.waitForResponse(result =>
        result.request().method() === "POST" && new URL(result.url()).pathname === addPath,
    );
    await page.getByRole("button", { name: "Add todo" }).click();
    assert.equal((await response).status(), status);
}

const browser = await chromium.launch();
try {
    const native = await browser.newContext({ javaScriptEnabled: false });
    const plain = await native.newPage();
    plain.setDefaultTimeout(8_000);
    await time("nativeInitial", async () => {
        await plain.goto(origin);
        await plain.getByText("Todo <1> & Kotlin", { exact: true }).waitFor();
    });
    assert.equal(await plain.locator("#todo-count").textContent(), "10 remaining · 10 total");
    await plain.getByRole("link", { name: "Completed" }).click();
    assert.equal(await plain.locator("#todo-board li").count(), 0);
    await plain.getByRole("link", { name: "Active" }).click();
    await time("nativeInvalid", async () => {
        await add(plain, "   ", 400);
        await plain.getByRole("heading", { name: "Check your todo" }).waitFor();
    });
    assert.equal(await plain.getByLabel("New todo").inputValue(), "   ");
    await time("nativeAddVisible", async () => {
        await add(plain, "Native <strong> & Todo", 303);
        await plain.waitForURL(`${origin}/?filter=active`);
        await plain.getByText("Native <strong> & Todo", { exact: true }).waitFor();
    });
    assert.equal(await plain.locator("#todo-count").textContent(), "11 remaining · 11 total");
    await native.close();

    const enhanced = await browser.newContext();
    const tab = await enhanced.newPage();
    const second = await enhanced.newPage();
    const browserErrors = [];
    for (const page of [tab, second]) {
        page.setDefaultTimeout(8_000);
        page.on("pageerror", error => browserErrors.push(error.stack));
    }
    await tab.goto(origin);
    await tab.waitForFunction(() => performance.getEntriesByType("resource").some(entry => entry.initiatorType === "script"));
    const resources = await tab.evaluate(() =>
        [...performance.getEntriesByType("navigation"), ...performance.getEntriesByType("resource")]
            .filter(entry => ["navigation", "script", "link", "css"].includes(entry.initiatorType))
            .map(entry => ({
                path: new URL(entry.name).pathname, type: entry.initiatorType, transferBytes: entry.transferSize,
                encodedBodyBytes: entry.encodedBodySize, decodedBodyBytes: entry.decodedBodySize,
            })),
    );
    await time("enhancedInvalidFocus", async () => {
        await add(tab, "  ", 400);
        await tab.waitForFunction(() => document.activeElement?.id === "todo-errors");
    });
    assert.equal(await tab.getByLabel("New todo").inputValue(), "  ");

    await second.goto(origin);
    const draft = second.getByLabel("New todo");
    await draft.fill("Draft from the second tab");
    await draft.focus();
    await draft.evaluate(input => input.setSelectionRange(5, 10));
    const expected = "Enhanced <strong> & Todo";
    const began = performance.now();
    await time("enhancedAddVisible", async () => {
        await add(tab, expected, 200);
        await tab.getByText(expected, { exact: true }).waitFor();
        await tab.waitForFunction(() =>
            document.querySelector("#todo-status")?.textContent === "Todo added."
            && document.querySelector("#todo-count")?.textContent === "12 remaining · 12 total"
            && document.querySelector("#todo-title")?.value === "",
        );
    });
    assert.equal(tab.url(), `${origin}/`);
    assert.equal(await tab.locator("#todo-status").textContent(), "Todo added.");
    assert.equal(await tab.locator("#todo-count").textContent(), "12 remaining · 12 total");
    assert.equal(await tab.getByLabel("New todo").inputValue(), "");
    await second.getByText(expected, { exact: true }).waitFor();
    timings.peerSseVisible = performance.now() - began;
    assert.deepEqual(await draft.evaluate(input => [input.value, input.selectionStart, input.selectionEnd,
        input === document.activeElement]), ["Draft from the second tab", 5, 10, true]);
    assert.deepEqual(browserErrors, []);
    await enhanced.close();

    const denied = await fetch(`${origin}${addPath}`, {
        method: "POST",
        headers: { Origin: "https://foreign.invalid" },
        body: new URLSearchParams({ title: "Denied", filter: "all" }),
    });
    assert.equal(denied.status, 403);
    console.log(JSON.stringify({ browser: browser.version(), timingsMs: timings, freshEnhancedContextResources: resources }));
} finally {
    await browser.close();
}
