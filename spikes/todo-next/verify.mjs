import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(new URL("../../client/woge-fallback-client/package.json", import.meta.url));
const { chromium } = require("playwright");
const origin = new URL(process.argv[2]).origin;
const matchedDraft = process.argv[3] === "matched-draft";
const addPath = "/woge-actions/add-todo";
const timings = {};

async function time(name, action) {
    const started = performance.now();
    await action();
    timings[name] = performance.now() - started;
}

async function add(page, value, status) {
    await page.getByLabel("New todo").fill(value);
    const response = addResponse(page);
    await page.getByRole("button", { name: "Add todo" }).click();
    assert.equal((await response).status(), status);
}

function addResponse(page) {
    return page.waitForResponse(result =>
        result.request().method() === "POST" && new URL(result.url()).pathname === addPath,
    );
}

async function waitForAdd(tab, expected, draftValue) {
    await tab.getByText(expected, { exact: true }).waitFor();
    await tab.waitForFunction(value =>
        document.querySelector("#todo-status")?.textContent === "Todo added."
        && document.querySelector("#todo-count")?.textContent === "12 remaining · 12 total"
        && document.querySelector("#todo-title")?.value === value,
    draftValue);
}

async function enterDraft(input, value, start, end) {
    await input.fill(value);
    await input.focus();
    await input.evaluate((node, [left, right]) => node.setSelectionRange(left, right), [start, end]);
}

async function checkDraft(input, value, start, end) {
    assert.deepEqual(await input.evaluate(node => [node.value, node.selectionStart, node.selectionEnd,
        node === document.activeElement]), [value, start, end, true]);
}

async function addWithNewerDraft(tab, second, expected) {
    const input = tab.getByLabel("New todo");
    const value = "Draft typed while the POST is pending";
    await input.fill(expected);
    const response = addResponse(tab);
    const intercepted = Promise.withResolvers();
    const release = Promise.withResolvers();
    await tab.route(`${origin}${addPath}`, async route => {
        intercepted.resolve(route.request());
        await release.promise;
        await route.continue();
    }, { times: 1 });

    try {
        await tab.getByRole("button", { name: "Add todo" }).click();
        const request = await intercepted.promise;
        assert.equal(request.method(), "POST");
        assert.equal(new URLSearchParams(request.postData()).get("title"), expected);
        await enterDraft(input, value, 6, 11);
        await checkDraft(input, value, 6, 11);
        const began = performance.now();
        release.resolve();
        const visible = (async () => {
            assert.equal((await response).status(), 200);
            await waitForAdd(tab, expected, value);
            await checkDraft(input, value, 6, 11);
            assert.equal(await tab.locator("#todo-errors").count(), 0);
            timings.enhancedAddVisible = performance.now() - began;
        })();
        const peer = (async () => {
            await second.getByText(expected, { exact: true }).waitFor();
            await second.waitForFunction(() =>
                document.querySelector("#todo-count")?.textContent === "12 remaining · 12 total",
            );
            timings.peerSseVisible = performance.now() - began;
        })();
        await Promise.all([visible, peer]);
    } finally {
        release.resolve();
    }
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
    if (matchedDraft) {
        assert.equal(await plain.locator("#todo-board li").count(), 10);
        assert.equal(await plain.getByRole("link", { name: "Active" }).getAttribute("aria-current"), "page");
    }
    await time("nativeInvalid", async () => {
        await add(plain, "   ", 400);
        await plain.getByRole("heading", { name: "Check your todo" }).waitFor();
    });
    assert.equal(await plain.getByLabel("New todo").inputValue(), "   ");
    if (matchedDraft) {
        assert.equal(await plain.locator("#todo-count").textContent(), "10 remaining · 10 total");
        assert.equal(await plain.getByLabel("New todo").getAttribute("aria-invalid"), "true");
    }
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

    if (matchedDraft) {
        assert.equal(await tab.locator("#todo-count").textContent(), "11 remaining · 11 total");
        assert.equal(await tab.getByLabel("New todo").getAttribute("aria-invalid"), "true");
        const subscribed = second.waitForResponse(response =>
            response.request().resourceType() === "eventsource"
            && response.headers()["content-type"]?.startsWith("text/event-stream"),
        );
        await second.goto(origin);
        assert.equal((await subscribed).status(), 200);
    } else {
        await second.goto(origin);
    }
    const draft = second.getByLabel("New todo");
    const draftValue = "Draft from the second tab";
    await enterDraft(draft, draftValue, 5, 10);
    const expected = "Enhanced <strong> & Todo";
    if (matchedDraft) {
        await addWithNewerDraft(tab, second, expected);
    } else {
        const began = performance.now();
        await time("enhancedAddVisible", async () => {
            await add(tab, expected, 200);
            await waitForAdd(tab, expected, "");
        });
        assert.equal(await tab.getByLabel("New todo").inputValue(), "");
        await second.getByText(expected, { exact: true }).waitFor();
        timings.peerSseVisible = performance.now() - began;
    }
    assert.equal(tab.url(), `${origin}/`);
    assert.equal(await tab.locator("#todo-status").textContent(), "Todo added.");
    assert.equal(await tab.locator("#todo-count").textContent(), "12 remaining · 12 total");
    await checkDraft(draft, draftValue, 5, 10);
    assert.deepEqual(browserErrors, []);
    await enhanced.close();

    const denied = await fetch(`${origin}${addPath}`, {
        method: "POST",
        headers: { Origin: "https://foreign.invalid" },
        body: new URLSearchParams({ title: "Denied", filter: "all" }),
    });
    assert.equal(denied.status, 403);
    if (matchedDraft) {
        const html = (await (await fetch(origin)).text()).replaceAll(/<!--.*?-->/g, "");
        assert.match(html, /12 remaining/);
        assert.doesNotMatch(html, /Denied/);
    }
    console.log(JSON.stringify({
        browser: browser.version(), timingsMs: timings, freshEnhancedContextResources: resources,
        ...(matchedDraft && { outcomes: { submittingDraftPreserved: true, peerDraftPreserved: true } }),
    }));
} finally {
    await browser.close();
}
