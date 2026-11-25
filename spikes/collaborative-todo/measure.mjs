import assert from "node:assert/strict";
import { execFileSync, spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import { once } from "node:events";
import { mkdir, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { setTimeout as delay } from "node:timers/promises";
import { openRelay } from "./relay.mjs";

const require = createRequire(new URL("../../client/woge-fallback-client/package.json", import.meta.url));
const { chromium } = require("playwright");
const jar = fileURLToPath(new URL("../../examples/todo-spring-boot/build/libs/woge-todo-0.1.0.jar", import.meta.url));
const hotwire = fileURLToPath(new URL("hotwire/", import.meta.url));
const java = process.env.JAVA_HOME ? `${process.env.JAVA_HOME}/bin/java` : "java";
const output = process.argv[2] || "build/collaborative-todo";
const roundCount = Number(process.env.STUDY_ROUNDS || "3");
const halfRtt = Number(process.env.STUDY_HALF_RTT || "20");
const timeout = 6_000;
const results = [];
const choices = (process.env.STUDY_CONTENDERS || "woge,hotwire,hotwire-stable-form").split(",");
assert.ok(choices.length && choices.every(name => ["woge", "hotwire", "hotwire-stable-form"].includes(name)));
const input = page => page.getByLabel("New todo");
const button = page => page.getByRole("button", { name: "Add todo", exact: true });
const row = (page, title) => page.locator("[data-todo-id]").filter({ hasText: title });
const elapsed = since => Math.round(performance.now() - since);
const rss = pid => Number(execFileSync("ps", ["-o", "rss=", "-p", String(pid)], { encoding: "utf8" }).trim());

const browser = await chromium.launch({ timeout: 20_000 });

try {
    for (let round = 1; round <= roundCount; round++) {
        const position = (round - 1) % choices.length;
        const order = [...choices.slice(position), ...choices.slice(0, position)];
        for (const name of order) {
            const result = { round, name, browser: browser.version(), addedRttMs: halfRtt * 2, scenarios: {} };
            results.push(result);
            try {
                await measure(result);
            } catch (error) {
                result.failure = error.message;
            }
            console.log(JSON.stringify(result));
        }
    }
} finally {
    await browser.close();
    await mkdir(output, { recursive: true });
    await writeFile(`${output}/rounds.json`, `${JSON.stringify(results, null, 2)}\n`);
}

async function measure(result) {
    const server = await start(result.name);
    const relay = await openRelay(server.port, halfRtt);
    result.startupMs = server.startupMs;
    try {
        const native = await open(relay, "bob", false);
        await seed(native.page);
        result.native = await nativeCase(native.page);
        await native.context.close();
        const alice = await open(relay, "alice");
        const bob = await open(relay, "bob");
        await until(() => relay.active("alice") > 0 && relay.active("bob") > 0, "two persistent connections", 10_000);
        result.idleRssKiB = rss(server.process.pid);
        const baseline = relay.stats();
        const requestOffset = relay.requests.length;

        await scenario(result, "authorization", async () => {
            const outsider = await browser.newContext();
            try {
                const missing = await outsider.request.get(relay.origin);
                const write = await outsider.request.post(`${relay.origin}/woge-actions/add-todo`, { form: { title: "Denied" } });
                const foreign = await alice.context.request.post(`${relay.origin}/woge-actions/add-todo`, {
                    headers: { origin: "https://foreign.invalid" }, form: { title: "Denied origin" },
                });
                assert.equal(missing.status(), 403);
                assert.equal(write.status(), 403);
                assert.equal(foreign.status(), 403);
                return { read: missing.status(), write: write.status(), foreign: foreign.status() };
            } finally {
                await outsider.close();
            }
        });
        await scenario(result, "differentFilters", async () => {
            await alice.page.getByRole("link", { name: "Active", exact: true }).click();
            await bob.page.getByRole("link", { name: "Completed", exact: true }).click();
            await alice.page.waitForURL(/filter=active/);
            await bob.page.waitForURL(/filter=completed/);
            await row(bob.page, "Todo 06").waitFor();
            await row(alice.page, "Todo 06").waitFor({ state: "detached" });
            const started = performance.now();
            await row(bob.page, "Todo 06").getByRole("button", { name: "Mark active" }).click();
            await row(alice.page, "Todo 06").waitFor();
            await row(bob.page, "Todo 06").waitFor({ state: "detached" });
            assert.match(alice.page.url(), /filter=active/);
            assert.match(bob.page.url(), /filter=completed/);
            return { visibleMs: elapsed(started) };
        });
        await bob.page.getByRole("link", { name: "All", exact: true }).click();
        await bob.page.waitForURL(/filter=all/);
        await row(bob.page, "Todo 01").waitFor();
        await scenario(result, "draft", async () => {
            await selection(alice.page, "Unsubmitted draft");
            const started = performance.now();
            await add(bob.page, "Draft update");
            await row(alice.page, "Draft update").waitFor();
            const observed = await readSelection(alice.page);
            const preserved = observed.value === "Unsubmitted draft" && observed.selected === "sub" && observed.focused;
            return { passed: preserved, ...observed, visibleMs: elapsed(started) };
        });
        await scenario(result, "stablePointer", async () => {
            await input(alice.page).fill("Local pointer submission");
            await button(alice.page).hover();
            const postOffset = relay.requests.length;
            await alice.page.mouse.down();
            let retained;
            try {
                await add(bob.page, "During pointer");
                await row(alice.page, "During pointer").waitFor();
                retained = (await input(alice.page).inputValue()) === "Local pointer submission";
            } finally {
                await alice.page.mouse.up();
            }
            if (!retained) {
                await delay(150);
                return { passed: false, lostDraftBeforeRelease: true, posts: postCount(relay, postOffset) };
            }
            await row(alice.page, "Local pointer submission").waitFor();
            await row(bob.page, "Local pointer submission").waitFor();
            assert.equal(await row(bob.page, "Local pointer submission").count(), 1);
            assert.equal(postCount(relay, postOffset), 1);
            return { posts: 1 };
        });
        await scenario(result, "removedPointer", async () => {
            await row(alice.page, "Todo 01").getByRole("button", { name: "Complete" }).hover();
            const postOffset = relay.requests.length;
            await alice.page.mouse.down();
            try {
                await row(bob.page, "Todo 01").getByRole("button", { name: "Delete" }).click();
                await row(alice.page, "Todo 01").waitFor({ state: "detached" });
            } finally {
                await alice.page.mouse.up();
            }
            await delay(150);
            assert.equal(postCount(relay, postOffset), 0);
            return { posts: 0 };
        });
        await scenario(result, "reconnect", async () => {
            await selection(alice.page, "Reconnect draft");
            relay.disconnect("alice");
            await until(() => relay.active("alice") === 0, "disconnected Alice reader");
            await add(bob.page, "While disconnected");
            assert.equal(await row(alice.page, "While disconnected").count(), 0);
            const started = performance.now();
            relay.reconnect("alice");
            const transportReopened = await until(() => relay.active("alice") > 0, "reopened Alice reader", 12_000)
                .then(() => true, () => false);
            const auto = transportReopened && await row(alice.page, "While disconnected")
                .waitFor({ timeout: 3_000 }).then(() => true, () => false);
            const retained = await readSelection(alice.page);
            const autoElapsed = auto ? elapsed(started) : null;
            if (!auto) {
                await alice.page.reload();
                await row(alice.page, "While disconnected").waitFor();
            }
            return { transportReopened, auto, autoVisibleMs: autoElapsed,
                explicitGetVisibleMs: auto ? null : elapsed(started), ...retained };
        });
        await scenario(result, "uncertain", async () => {
            await alice.page.reload();
            const postOffset = relay.requests.length;
            await alice.page.evaluate(() => {
                window.studyIntent = { submits: 0, turboFetches: 0 };
                document.addEventListener("submit", () => window.studyIntent.submits++);
                document.addEventListener("turbo:before-fetch-request", event => {
                    if (event.detail.fetchOptions.method.toUpperCase() === "POST") window.studyIntent.turboFetches++;
                });
            });
            relay.dropNextPost("alice");
            await add(alice.page, "Committed without response");
            await until(() => relay.requests.some(request => request.actor === "alice" && request.responseDropped), "dropped response");
            const status = await alice.page.locator("#todo-alert").textContent({ timeout: 400 }).catch(() => null);
            const intent = await alice.page.evaluate(() => window.studyIntent);
            await Promise.all([alice.page.reload(), bob.page.reload()]);
            const committed = await row(bob.page, "Committed without response").count();
            const recovered = await row(alice.page, "Committed without response").count();
            const posts = postCount(relay, postOffset);
            return { passed: posts === 1 && committed === 1 && recovered === 1, posts, status, intent,
                recoveredViaGet: recovered, committed };
        });
        await scenario(result, "revoked", async () => {
            await until(() => relay.active("alice") > 0 && relay.active("bob") > 0, "both subscriptions before revocation", 12_000);
            relay.disconnect("alice");
            relay.revoke("alice");
            relay.reconnect("alice");
            const read = await alice.context.request.get(relay.origin);
            assert.equal(read.status(), 403);
            await until(() => relay.requests.some(request => request.actor === "alice" && request.live && request.status === 403), "denied re-subscription", 12_000);
            assert.equal(relay.active("alice"), 0);
            assert.ok(relay.active("bob") > 0);
            return { read: read.status(), subscription: 403 };
        });
        result.activeRssKiB = rss(server.process.pid);
        const ended = performance.now();
        await Promise.all([alice.context.close(), bob.context.close()]);
        await until(() => relay.stats().readers === 0, "all persistent connections closed");
        result.cleanupMs = elapsed(ended);
        const traffic = relay.requests.slice(requestOffset);
        result.traffic = { downstreamBytes: relay.stats().downstreamBytes - baseline.downstreamBytes,
            peakReaders: relay.stats().peakReaders, requests: traffic };
    } catch (error) {
        result.failureRequests = relay.requests.slice(-15);
        throw error;
    } finally {
        relay.close();
        await server.stop();
    }
}

async function seed(page) {
    for (let index = 1; index <= 10; index++) {
        const title = `Todo ${String(index).padStart(2, "0")}`;
        await add(page, title);
        await row(page, title).waitFor();
        if (index > 5) {
            await row(page, title).getByRole("button", { name: "Complete", exact: true }).click();
            await row(page, title).getByText("(completed)", { exact: false }).waitFor();
        }
    }
    assert.equal(await page.locator("#todo-count").textContent(), "5 remaining · 10 total");
}

async function nativeCase(page) {
    await page.getByRole("link", { name: "Completed", exact: true }).click();
    await page.waitForURL(/filter=completed/);
    await row(page, "Todo 10").waitFor();
    assert.equal(await page.locator("[data-todo-id]").count(), 5);
    await page.getByRole("link", { name: "Active", exact: true }).click();
    await page.waitForURL(/filter=active/);
    await row(page, "Todo 01").waitFor();
    assert.equal(await page.locator("[data-todo-id]").count(), 5);
    await add(page, "   ");
    await page.getByRole("heading", { name: "Check your todo" }).waitFor();
    assert.equal(await input(page).inputValue(), "   ");
    await add(page, "Native <HTML> & CSS");
    const native = row(page, "Native <HTML> & CSS");
    await native.waitFor();
    assert.equal(await native.locator("html, css").count(), 0);
    await native.getByRole("button", { name: "Delete", exact: true }).click();
    await row(page, "Todo 05").waitFor();
    assert.equal(await page.locator("[data-todo-id]").count(), 5);
    return { filterAndPostRedirectGet: true, invalidRetained: true, escaped: true };
}

async function open(relay, actor, javaScriptEnabled = true) {
    const context = await browser.newContext({ javaScriptEnabled });
    await context.addCookies([{ name: "study_actor", value: actor, url: relay.origin, httpOnly: true }]);
    const page = await context.newPage();
    page.setDefaultTimeout(timeout);
    const shell = await page.goto(relay.origin);
    assert.equal(shell.status(), 200, `initial shell for ${actor}`);
    return { context, page };
}

async function start(name) {
    const started = performance.now();
    const processOptions = { stdio: ["ignore", "pipe", "pipe"] };
    const child = name === "woge"
        ? spawn(java, ["-Xms128m", "-Xmx384m", "-XX:ActiveProcessorCount=4", "-jar", jar, "--server.port=0"], processOptions)
        : spawn("bundle", ["exec", "puma", "--bind", "tcp://127.0.0.1:0", "--threads", "4:4"], {
            ...processOptions,
            cwd: hotwire,
            env: { ...process.env, RAILS_ENV: "production", STUDY_SECRET_KEY_BASE: randomBytes(32).toString("hex"),
                STUDY_STABLE_FORM: name === "hotwire-stable-form" ? "1" : "0" },
        });
    const closed = once(child, "close");
    let logs = "";
    const pattern = name === "woge" ? /Netty started on port (\d+)/ : /Listening on http:\/\/127\.0\.0\.1:(\d+)/;
    try {
        const port = await new Promise((resolve, reject) => {
            AbortSignal.timeout(30_000).addEventListener("abort", () => reject(new Error(`Startup deadline: ${logs}`)));
            child.once("error", reject);
            closed.then(([code]) => reject(new Error(`Server exited ${code}: ${logs}`)), reject);
            child.stderr.on("data", text => { logs += text; });
            child.stdout.on("data", text => {
                logs += text;
                const match = logs.match(pattern);
                if (match) resolve(Number(match[1]));
            });
        });
        return { port, process: child, startupMs: elapsed(started), stop: async () => { child.kill(); await closed; } };
    } catch (error) {
        child.kill();
        await closed.catch(() => {});
        throw error;
    }
}

async function add(page, title) {
    await input(page).fill(title);
    await button(page).click();
}

async function selection(page, value) {
    await input(page).fill(value);
    await input(page).evaluate(field => field.setSelectionRange(2, 5));
}

async function readSelection(page) {
    return input(page).evaluate(field => ({ value: field.value,
        selected: field.value.slice(field.selectionStart, field.selectionEnd), focused: field === document.activeElement }));
}

function postCount(relay, offset) {
    return relay.requests.slice(offset).filter(request => request.actor === "alice" && request.method === "POST").length;
}

async function scenario(result, name, run) {
    try {
        result.scenarios[name] = { passed: true, ...await run() };
    } catch (error) {
        result.scenarios[name] = { passed: false, error: error.message };
    }
}

async function until(predicate, description, waitMs = timeout) {
    const started = performance.now();
    while (!predicate()) {
        assert.ok(elapsed(started) < waitMs, `Timed out waiting for ${description}`);
        await delay(25);
    }
}
