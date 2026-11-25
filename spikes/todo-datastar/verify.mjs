import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(new URL("../../client/woge-fallback-client/package.json", import.meta.url));
const { chromium, expect } = require("@playwright/test");
const origin = new URL(process.argv[2]).origin;
const contender = process.argv[3];
const addPath = "/woge-actions/add-todo";
const timings = {};

async function time(name, action) {
    if (process.env.WOGE_TODO_STUDY_TRACE) console.error(name);
    const started = performance.now();
    await action();
    timings[name] = performance.now() - started;
}

async function add(page, value, status) {
    await page.getByLabel("New todo").fill(value);
    const result = page.waitForResponse(response =>
        response.request().method() === "POST" && new URL(response.url()).pathname === addPath);
    await page.getByRole("button", { name: "Add todo", exact: true }).click();
    const response = await result;
    if (process.env.WOGE_TODO_STUDY_TRACE) console.error(response.status(), response.headers()["content-type"]);
    assert.equal(response.status(), status);
    return {
        status,
        contentType: response.headers()["content-type"] ?? null,
        contentLength: response.headers()["content-length"] ?? null,
    };
}

const browser = await chromium.launch();
try {
    const native = await browser.newContext({ javaScriptEnabled: false });
    const plain = await native.newPage();
    plain.setDefaultTimeout(8_000);
    await time("nativeInitial", async () => {
        await plain.goto(`${origin}/`);
        await expect(plain.getByText("Todo <1> & Kotlin", { exact: true })).toBeVisible();
        await expect(plain.locator("#todo-count")).toHaveText("10 remaining · 10 total");
    });
    await time("nativeFilteredDocument", async () => {
        await plain.getByRole("link", { name: "Completed", exact: true }).click();
        await expect(plain.locator("#todo-board li")).toHaveCount(0);
        await expect(plain.getByRole("link", { name: "Completed", exact: true })).toHaveAttribute("aria-current", "page");
    });
    await plain.getByRole("link", { name: "Active", exact: true }).click();
    await time("nativeInvalid", async () => {
        await add(plain, "   ", 400);
        await expect(plain.getByRole("heading", { name: "Check your todo" })).toBeVisible();
        await expect(plain.getByLabel("New todo")).toHaveValue("   ");
        await expect(plain.locator("#todo-errors a")).toHaveAttribute("href", "#todo-title");
    });
    await time("nativeAddAndRedirect", async () => {
        await add(plain, "Native <strong> & Todo", 303);
        await plain.waitForURL(`${origin}/?filter=active`);
        await expect(plain.getByText("Native <strong> & Todo", { exact: true })).toHaveCount(1);
        await expect(plain.locator("#todo-count")).toHaveText("11 remaining · 11 total");
    });
    await plain.reload();
    await expect(plain.getByText("Native <strong> & Todo", { exact: true })).toHaveCount(1);
    await native.close();

    const enhanced = await browser.newContext();
    const tab = await enhanced.newPage();
    const second = await enhanced.newPage();
    const errors = [];
    for (const page of [tab, second]) {
        page.setDefaultTimeout(8_000);
        page.on("pageerror", error => {
            errors.push(error.message);
            if (process.env.WOGE_TODO_STUDY_TRACE) console.error(error.message);
        });
        if (process.env.WOGE_TODO_STUDY_TRACE) page.on("console", message => console.error(message.text()));
    }
    await time("enhancedInitial", async () => {
        await tab.goto(`${origin}/`);
        await expect(tab.getByText("Todo <1> & Kotlin", { exact: true })).toBeVisible();
        await tab.waitForFunction(() => performance.getEntriesByType("resource").some(entry => entry.initiatorType === "script"));
    });
    const resources = await tab.evaluate(() =>
        [...performance.getEntriesByType("navigation"), ...performance.getEntriesByType("resource")]
            .filter(entry => ["navigation", "script", "link", "css"].includes(entry.initiatorType))
            .map(entry => ({ path: new URL(entry.name).pathname, type: entry.initiatorType,
                transferBytes: entry.transferSize, encodedBodyBytes: entry.encodedBodySize,
                decodedBodyBytes: entry.decodedBodySize })),
    );
    let validation;
    await time("enhancedInvalidFocus", async () => {
        validation = await add(tab, "  ", contender === "datastar" ? 200 : 400);
        await expect(tab.locator("#todo-errors")).toBeFocused();
        await expect(tab.getByLabel("New todo")).toHaveValue("  ");
        await expect(tab.locator("#todo-errors a")).toHaveAttribute("href", "#todo-title");
    });

    await second.goto(`${origin}/`);
    const peerDraft = second.getByLabel("New todo");
    await peerDraft.fill("Draft from the second tab");
    await peerDraft.focus();
    await peerDraft.evaluate(input => input.setSelectionRange(5, 10));
    const title = "Enhanced <strong> & Todo";
    let action;
    const began = performance.now();
    const peerVisible = second.getByText(title, { exact: true }).waitFor().then(() => performance.now() - began);
    await time("enhancedAddVisible", async () => {
        action = await add(tab, title, 200);
        await expect(tab.getByText(title, { exact: true })).toHaveCount(1);
        await expect(tab.locator("#todo-status")).toHaveText("Todo added.");
        await expect(tab.locator("#todo-count")).toHaveText("12 remaining · 12 total");
        await expect(tab.getByRole("heading", { name: "Check your todo" })).toBeHidden();
    });
    assert.equal(tab.url(), `${origin}/`);
    timings.peerSseVisible = await peerVisible;
    const inputAfterSuccess = await tab.getByLabel("New todo").inputValue();
    assert.ok(inputAfterSuccess === "" || inputAfterSuccess === title);
    assert.deepEqual(await peerDraft.evaluate(input => [input.value, input.selectionStart,
        input.selectionEnd, input === document.activeElement]), ["Draft from the second tab", 5, 10, true]);

    let release;
    let arrived;
    const saveRequested = new Promise(resolve => { arrived = resolve; });
    const saveAllowed = new Promise(resolve => { release = resolve; });
    await tab.route(`**${addPath}`, async route => {
        arrived();
        await saveAllowed;
        await route.continue();
    }, { times: 1 });
    const pending = add(tab, "Submitted title", 200);
    const requestDeadline = setTimeout(() => release(), 5_000);
    try {
        await Promise.race([saveRequested, pending.then(() => { throw new Error("Expected the request to stay pending"); })]);
        const draft = tab.getByLabel("New todo");
        await draft.fill("New draft during the POST");
        await draft.focus();
        await draft.evaluate(input => input.setSelectionRange(4, 8));
    } finally {
        clearTimeout(requestDeadline);
        release();
    }
    await pending;
    await expect(tab.getByText("Submitted title", { exact: true })).toHaveCount(1);
    const draft = tab.getByLabel("New todo");
    await expect(draft).toHaveValue("New draft during the POST");
    assert.deepEqual(await draft.evaluate(input => [input.selectionStart, input.selectionEnd,
        input === document.activeElement]), [4, 8, true]);
    assert.deepEqual(errors, []);

    const blocked = await fetch(`${origin}${addPath}`, {
        method: "POST",
        headers: { Origin: "https://foreign.invalid" },
        body: new URLSearchParams({ title: "Denied", filter: "all" }),
    });
    assert.equal(blocked.status, 403);
    await tab.reload();
    await expect(tab.locator("#todo-count")).toHaveText("13 remaining · 13 total");
    await expect(tab.getByText("Denied", { exact: true })).toHaveCount(0);
    if (process.env.WOGE_TODO_STUDY_TRACE) console.error("lostCommitResponse");
    const recovery = await enhanced.newPage();
    recovery.setDefaultTimeout(8_000);
    await recovery.goto(`${origin}/`);
    const lostTitle = "Committed before the response was lost";
    let lostPostAttempts = 0;
    let committedStatus;
    let responseDropped;
    const dropped = new Promise(resolve => { responseDropped = resolve; });
    await recovery.route(`**${addPath}`, async route => {
        lostPostAttempts++;
        const response = await route.fetch({ maxRetries: 0, maxRedirects: 0 });
        committedStatus = response.status();
        await response.dispose();
        await route.abort("connectionreset");
        responseDropped();
    });
    await recovery.getByLabel("New todo").fill(lostTitle);
    await recovery.getByRole("button", { name: "Add todo", exact: true }).click();
    const dropTimeout = setTimeout(() => responseDropped(), 5_000);
    await dropped;
    clearTimeout(dropTimeout);
    assert.equal(committedStatus, 200);
    await expect(recovery.locator("#todo-alert")).toHaveText("Could not confirm the result. Reload before submitting again.");
    await expect(recovery.locator("#todo-status")).not.toHaveText("Todo added.");
    await expect(recovery.getByLabel("New todo")).toHaveValue(lostTitle);
    await recovery.reload();
    await expect(recovery.getByText(lostTitle, { exact: true })).toHaveCount(1);
    await expect(recovery.locator("#todo-count")).toHaveText("14 remaining · 14 total");
    assert.equal(lostPostAttempts, 1);
    await enhanced.close();
    console.log(JSON.stringify({ browser: browser.version(), timingsMs: timings, resources, validation,
        action, lostResponse: { upstreamStatus: committedStatus, submitAttempts: lostPostAttempts,
            warning: true, safeReloadCount: 14 }, inputAfterSuccess: inputAfterSuccess === "" ? "cleared" : "retained" }));
} finally {
    await browser.close();
}
