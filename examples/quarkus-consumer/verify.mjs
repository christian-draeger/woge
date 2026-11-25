import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createRequire } from "node:module";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(new URL("../../client/woge-fallback-client/package.json", import.meta.url));
const browsers = require("playwright");
const requested = process.argv.slice(2);
const engines = requested.length === 0 ? ["chromium", "firefox", "webkit"] : requested;
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin", "java") : "java";
const jar = fileURLToPath(new URL("build/quarkus-app/quarkus-run.jar", import.meta.url));
const app = spawn(java, ["-Dquarkus.http.port=0", "-jar", jar], { stdio: ["ignore", "pipe", "pipe"] });
const closed = new Promise(resolve => app.once("close", resolve));
let logs = "";
let browser;

try {
    const timer = AbortSignal.timeout(30_000);
    const port = await new Promise((resolve, reject) => {
        timer.addEventListener("abort", () => reject(new Error("Quarkus did not start within 30 seconds")), { once: true });
        once(app, "exit").then(([code]) => reject(new Error(`Quarkus exited during startup: ${code}`)), reject);
        app.stderr.on("data", chunk => { logs += chunk; });
        app.stdout.on("data", chunk => {
            logs += chunk;
            const match = logs.match(/Listening on: http:\/\/127\.0\.0\.1:(\d+)/);
            if (match) resolve(Number(match[1]));
        });
    });
    const origin = `http://127.0.0.1:${port}`;
    for (const engine of engines) {
        assert.ok(["chromium", "firefox", "webkit"].includes(engine), "Unknown browser engine");
        browser = await browsers[engine].launch({ timeout: 20_000 });
        const native = await browser.newPage({ javaScriptEnabled: false });
        await native.goto(`${origin}/forecast`);
        await native.getByRole("link", { name: "View the complete forecast" }).click();
        await native.waitForURL(`${origin}/forecast?complete=true`);
        await native.getByText("Saturday: sunny", { exact: true }).waitFor();

        await native.goto(`${origin}/validated-notes`);
        const note = native.getByRole("textbox", { name: "New note" });
        await note.fill("  ");
        const rejected = waitForPost(native);
        await native.getByRole("button", { name: "Add note" }).click();
        assert.equal((await rejected).status(), 400);
        assert.equal(await note.inputValue(), "  ");
        await native.getByText("Enter a note of 1 to 80 characters.").first().waitFor();
        await note.fill(`Native note from ${engine}`);
        const redirected = waitForPost(native);
        await native.getByRole("button", { name: "Add note" }).click();
        assert.equal((await redirected).status(), 303);
        await native.getByText(`Native note from ${engine}`, { exact: true }).waitFor();

        const enhanced = await browser.newPage();
        const patch = enhanced.waitForResponse(response => new URL(response.url()).pathname.startsWith("/forecast/woge-patches/"));
        await enhanced.goto(`${origin}/forecast`);
        assert.equal((await patch).status(), 200);
        await enhanced.getByText("Saturday: sunny", { exact: true }).waitFor();
        await enhanced.getByText("Sunday: light rain", { exact: true }).waitFor();

        await enhanced.goto(`${origin}/enhanced-notes`);
        await enhanced.getByRole("textbox", { name: "New note" }).fill(`Enhanced note from ${engine}`);
        const updated = waitForPost(enhanced);
        await enhanced.getByRole("button", { name: "Add note" }).click();
        const response = await updated;
        assert.equal(response.status(), 200);
        assert.match(response.headers()["content-type"], /application\/vnd\.woge\.patch-stream/);
        await enhanced.getByText(`Enhanced note from ${engine}`, { exact: true }).waitFor();
        await enhanced.getByRole("status").getByText("Note added").waitFor();
        assert.equal(enhanced.url(), `${origin}/enhanced-notes`);

        console.log(`Quarkus ${engine} packaged native/enhanced form and forecast passed`);
        await browser.close();
        browser = undefined;
    }
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

function waitForPost(page) {
    return page.waitForResponse(response => response.request().method() === "POST");
}
