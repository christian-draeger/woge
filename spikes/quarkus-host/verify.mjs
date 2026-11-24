import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createRequire } from "node:module";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(new URL("../../client/woge-fallback-client/package.json", import.meta.url));
const { chromium } = require("playwright");
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
    browser = await chromium.launch();

    const native = await browser.newPage({ javaScriptEnabled: false });
    await native.goto(`${origin}/forecast`);
    await native.getByRole("link", { name: "View the complete forecast" }).click();
    await native.waitForURL(`${origin}/forecast?complete=true`);
    await native.getByText("Saturday: sunny", { exact: true }).waitFor();

    const enhanced = await browser.newPage();
    const patch = enhanced.waitForResponse(response => new URL(response.url()).pathname.startsWith("/forecast/woge-patches/"));
    await enhanced.goto(`${origin}/forecast`);
    assert.equal((await patch).status(), 200);
    await enhanced.getByText("Saturday: sunny", { exact: true }).waitFor();
    await enhanced.getByText("Sunday: light rain", { exact: true }).waitFor();
    await enhanced.getByText("Loading the forecast…", { exact: true }).waitFor({ state: "detached" });
    assert.equal(enhanced.url(), `${origin}/forecast`);
    console.log("Quarkus forecast passed with JavaScript disabled and enabled.");
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
