import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { once } from "node:events";
import { createRequire } from "node:module";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(new URL("../../client/woge-fallback-client/package.json", import.meta.url));
const { chromium, expect } = require("@playwright/test");
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin", "java") : "java";
const libraries = fileURLToPath(new URL("build/install/woge-micronaut-host-spike/lib/", import.meta.url));
const password = randomUUID();
const app = spawn(java, ["-Dmicronaut.server.port=-1", "-cp", join(libraries, "*"), "example.micronaut.TodoController"], {
    env: { ...process.env, WOGE_SPIKE_WRITER_PASSWORD: password },
    stdio: ["ignore", "pipe", "pipe"],
});
const closed = new Promise(resolve => app.once("close", resolve));
let logs = "";
let browser;

async function submit(page, draft, status) {
    await page.getByLabel("New todo").fill(draft);
    const [response] = await Promise.all([
        page.waitForResponse(candidate =>
            candidate.request().method() === "POST" && new URL(candidate.url()).pathname === "/woge-actions/add-todo"),
        page.getByRole("button", { name: "Add todo", exact: true }).click(),
    ]);
    assert.equal(response.status(), status);
    return response;
}

try {
    const timer = AbortSignal.timeout(30_000);
    const port = await new Promise((resolve, reject) => {
        timer.addEventListener("abort", () => reject(new Error("Micronaut did not start within 30 seconds")), { once: true });
        once(app, "exit").then(([code]) => reject(new Error(`Micronaut exited during startup: ${code}`)), reject);
        app.stderr.on("data", chunk => { logs += chunk; });
        app.stdout.on("data", chunk => {
            logs += chunk;
            const match = logs.match(/Micronaut Todo spike listening at http:\/\/(?:localhost|127\.0\.0\.1):(\d+)/);
            if (match) resolve(Number(match[1]));
        });
    });
    const origin = `http://127.0.0.1:${port}`;
    const authorization = "Basic " + Buffer.from(`writer:${password}`).toString("base64");
    browser = await chromium.launch();

    for (const javaScriptEnabled of [false, true]) {
        const context = await browser.newContext({ javaScriptEnabled });
        await context.route("**/*", route => {
            if (new URL(route.request().url()).origin !== origin) return route.abort();
            return route.continue({ headers: { ...route.request().headers(), authorization } });
        });
        try {
            const page = await context.newPage();
            await page.goto(origin + "/?filter=active");
            const epoch = await page.locator('meta[name="woge-page-epoch"]').getAttribute("content");
            const invalid = await submit(page, "   ", 400);
            await expect(page.getByRole("heading", { name: "Check your todo" })).toBeVisible();
            await expect(page.getByLabel("New todo")).toHaveValue("   ");
            await expect(page.locator("#todo-errors a")).toHaveAttribute("href", "#todo-title");
            if (javaScriptEnabled) {
                assert.match(invalid.headers()["content-type"], /^application\/vnd\.woge\.patch-stream/);
                await expect(page.locator("#todo-errors")).toBeFocused();
            }

            const title = javaScriptEnabled ? "Enhanced <Todo>" : "Native <Todo>";
            const success = await submit(page, title, javaScriptEnabled ? 200 : 303);
            await expect(page.locator("[data-todo-id]").filter({ hasText: title })).toHaveCount(1);
            await expect(page.locator("#todo-errors")).toHaveCount(0);
            if (javaScriptEnabled) {
                assert.match(success.headers()["content-type"], /^application\/vnd\.woge\.patch-stream/);
                await expect(page.locator('meta[name="woge-page-epoch"]')).toHaveAttribute("content", epoch);
                await expect(page.locator("#todo-status")).toHaveText("Todo added.");
            } else {
                assert.equal(success.headers().location, "/?filter=active");
                await page.waitForURL(origin + "/?filter=active");
            }
            await page.reload();
            await expect(page.locator("[data-todo-id]").filter({ hasText: title })).toHaveCount(1);

            await page.goto(origin + "/deferred");
            if (javaScriptEnabled) {
                await expect(page.getByText(/^active todos: \d+$/)).toBeVisible();
                await expect(page.getByText(/^completed todos: \d+$/)).toBeVisible();
            } else {
                await page.getByRole("link", { name: "View the complete Todo list" }).click();
                await expect(page.getByRole("heading", { name: "Todos", exact: true })).toBeVisible();
            }
        } finally {
            await context.close();
        }
    }
    console.log(`Chromium ${browser.version()} passed the packaged Micronaut Todo validation, one write and deferred content with JavaScript disabled and enabled.`);
} catch (error) {
    console.error(logs.trim());
    throw error;
} finally {
    try {
        await browser?.close();
    } finally {
        app.kill();
        await closed;
    }
}
