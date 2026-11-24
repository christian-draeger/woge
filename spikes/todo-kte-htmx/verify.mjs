import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createRequire } from "node:module";
import { join } from "node:path";

const require = createRequire(new URL("../../client/woge-fallback-client/package.json", import.meta.url));
const { chromium } = require("playwright");
const jar = process.argv[2] ?? new URL("build/libs/todo-kte-htmx.jar", import.meta.url).pathname;
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin", "java") : "java";
const app = spawn(java, ["-jar", jar, "--server.port=0"], { stdio: ["ignore", "pipe", "pipe"] });
const exited = once(app, "exit");
let logs = "";

async function listenPort() {
    return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => reject(new Error("WebFlux did not start within 30 seconds")), 30_000);
        exited.then(([code]) => {
            clearTimeout(timeout);
            reject(new Error(`WebFlux exited during startup: ${code}`));
        });
        app.stderr.on("data", chunk => { logs += chunk; });
        app.stdout.on("data", chunk => {
            logs += chunk;
            const match = logs.match(/Netty started on port (\d+)/);
            if (match) {
                clearTimeout(timeout);
                resolve(Number(match[1]));
            }
        });
    });
}

async function add(page, text, status) {
    await page.getByLabel("New todo").fill(text);
    const response = page.waitForResponse(result =>
        result.request().method() === "POST" && new URL(result.url()).pathname === "/woge-actions/add-todo",
    );
    await page.getByRole("button", { name: "Add todo" }).click();
    assert.equal((await response).status(), status);
}

let browser;
try {
    const origin = `http://127.0.0.1:${await listenPort()}`;
    browser = await chromium.launch();
    const native = await browser.newContext({ javaScriptEnabled: false });
    const plain = await native.newPage();
    await plain.goto(origin);
    await plain.getByText("Todo <1> & Kotlin", { exact: true }).waitFor();
    assert.equal(await plain.locator("#todo-count").textContent(), "10 remaining · 10 total");
    await plain.getByRole("link", { name: "Completed" }).click();
    assert.equal(await plain.locator("#todo-board li").count(), 0);
    await plain.getByRole("link", { name: "Active" }).click();
    await add(plain, "   ", 400);
    await plain.getByRole("heading", { name: "Check your todo" }).waitFor();
    assert.equal(await plain.getByLabel("New todo").inputValue(), "   ");
    await add(plain, "Native <strong> & Todo", 303);
    await plain.waitForURL(`${origin}/?filter=active`);
    await plain.getByText("Native <strong> & Todo", { exact: true }).waitFor();
    assert.equal(await plain.locator("#todo-count").textContent(), "11 remaining · 11 total");
    await native.close();

    const enhanced = await browser.newContext();
    const tab = await enhanced.newPage();
    const second = await enhanced.newPage();
    const browserErrors = [];
    for (const page of [tab, second]) page.on("pageerror", error => browserErrors.push(error.stack));
    await tab.goto(origin);
    await add(tab, "  ", 400);
    const errors = tab.locator("#todo-errors");
    await tab.waitForFunction(() => document.activeElement?.id === "todo-errors");
    assert.equal(await tab.getByLabel("New todo").inputValue(), "  ");
    assert.equal(await errors.getByRole("heading").textContent(), "Check your todo");

    await second.goto(origin);
    const draft = second.getByLabel("New todo");
    await draft.fill("Draft from the second tab");
    await draft.focus();
    await draft.evaluate(input => input.setSelectionRange(5, 10));
    const expected = "Enhanced <strong> & Todo";
    await add(tab, expected, 200);
    assert.equal(tab.url(), `${origin}/`);
    await tab.getByText(expected, { exact: true }).waitFor();
    assert.equal(await tab.locator("#todo-status").textContent(), "Todo added.");
    assert.equal(await tab.locator("#todo-count").textContent(), "12 remaining · 12 total");
    assert.equal(await tab.getByLabel("New todo").inputValue(), "");
    await second.getByText(expected, { exact: true }).waitFor();
    assert.deepEqual(await draft.evaluate(input => [input.value, input.selectionStart, input.selectionEnd,
        input === document.activeElement]), ["Draft from the second tab", 5, 10, true]);

    let hold;
    const heldRequest = new Promise(resolve => { hold = resolve; });
    await tab.route("**/woge-actions/add-todo", route => hold(route), { times: 1 });
    await tab.getByLabel("New todo").fill("Submitted title");
    const committed = tab.waitForResponse(response => response.request().method() === "POST");
    await tab.getByRole("button", { name: "Add todo" }).click();
    const held = await heldRequest;
    await tab.getByLabel("New todo").fill("New draft during the POST");
    await held.continue();
    assert.equal((await committed).status(), 200);
    await tab.getByText("Submitted title", { exact: true }).waitFor();
    assert.equal(await tab.getByLabel("New todo").inputValue(), "New draft during the POST");
    assert.deepEqual(browserErrors, []);
    await enhanced.close();

    const denied = await fetch(`${origin}/woge-actions/add-todo`, {
        method: "POST",
        headers: { Origin: "https://foreign.invalid" },
        body: new URLSearchParams({ title: "Denied", filter: "all" }),
    });
    assert.equal(denied.status, 403);
    console.log(`Chromium ${browser.version()}: native filter/escaping/validation/303, enhanced focus/form/board/status, `
        + "two-tab SSE with draft/selection/focus, pending POST draft, foreign Origin denial");
} catch (error) {
    process.stderr.write(logs);
    throw error;
} finally {
    await browser?.close();
    if (app.exitCode === null) {
        app.kill("SIGTERM");
        const kill = setTimeout(() => app.kill("SIGKILL"), 5_000);
        await exited;
        clearTimeout(kill);
    }
}
