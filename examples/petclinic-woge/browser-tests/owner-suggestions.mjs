import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtemp, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { chromium } from "../../../client/woge-fallback-client/node_modules/playwright/index.mjs";

const scratch = await mkdtemp(join(tmpdir(), "woge-owner-suggestions-"));
const seed = join(scratch, "owners.sql");
await writeFile(seed, Array.from({ length: 80 }, (_, index) => {
  const name = String(index + 1).padStart(2, "0");
  return `insert into owners values (default, 'Resident ${name}', 'North', 'Fixture street', 'Fixture town', '1234567890');`;
}).join("\n"));
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin", "java") : "java";
const app = spawn(java, [
  "-jar", fileURLToPath(new URL("../build/libs/woge-petclinic.jar", import.meta.url)),
  "--server.address=127.0.0.1", "--server.port=0",
  "--spring.security.user.name=reference", "--spring.security.user.password=local-test-only",
  `--spring.sql.init.data-locations=classpath:db/h2/data.sql,${pathToFileURL(seed)}`,
]);
let browser;
let log = "";

async function origin() {
  return await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("PetClinic startup timed out")), 30_000);
    app.once("error", error => { clearTimeout(timeout); reject(error); });
    app.once("exit", code => { clearTimeout(timeout); reject(new Error(`PetClinic exited with ${code}`)); });
    for (const output of [app.stdout, app.stderr]) {
      output.on("data", chunk => {
        log = (log + chunk).slice(-16_000);
        const port = log.match(/Tomcat started on port (\d+)/)?.[1];
        if (port) {
          clearTimeout(timeout);
          resolve(`http://127.0.0.1:${port}`);
        }
      });
    }
  });
}

try {
  const address = await origin();
  browser = await chromium.launch();
  const searchPath = `${address}/woge/owners`;
  const suggestions = page => page.getByRole("list", { name: "Owner suggestions" });
  const searchResults = page => page.getByRole("list", { name: "Owner search results" });
  async function signedIn(options = {}, configure = async () => {}) {
    const context = await browser.newContext(options);
    const page = await context.newPage();
    await page.goto(`${address}/login`);
    await page.locator('input[name="username"]').fill("reference");
    await page.locator('input[name="password"]').fill("local-test-only");
    await Promise.all([page.waitForURL(url => url.pathname !== "/login"), page.getByRole("button").click()]);
    await configure(page);
    await page.goto(searchPath, { waitUntil: "networkidle" });
    return page;
  }

  const native = await signedIn({ javaScriptEnabled: false });
  await native.getByLabel("Last name").fill("No");
  await native.getByRole("button", { name: "Find owners" }).click();
  assert.equal(await searchResults(native).getByRole("link").count(), 20);
  for (let index = 0; index < 3; index++) {
    await native.getByRole("link", { name: "Next page" }).click();
  }
  await native.getByRole("link", { name: "Resident 80 North" }).click();
  await native.getByRole("heading", { name: "Resident 80 North" }).waitFor();
  await native.context().close();
  console.log("PASS JavaScript off: paged native search reaches owner 80");

  const enhanced = await signedIn({}, page => page.addInitScript(() => {
    window.cspViolations = [];
    document.addEventListener("securitypolicyviolation", event => window.cspViolations.push(event.violatedDirective));
    const fetch = window.fetch;
    window.fetch = async (url, options) => {
      if (new URL(url).searchParams.get("lastName") !== "No") return fetch(url, options);
      window.firstLookupSignal = options.signal;
      // Deliver an actual old service response even if abort has already run.
      const response = await fetch(url, { ...options, signal: undefined });
      const matches = await response.json();
      await new Promise(resolve => { window.releaseFirstLookup = resolve; });
      return { ok: response.ok, json: async () => { window.oldLookupDelivered = true; return matches; } };
    };
  }));
  const input = enhanced.getByLabel("Last name");
  await input.fill("No");
  await enhanced.waitForFunction(() => typeof window.releaseFirstLookup === "function");
  const started = performance.now();
  await input.fill("Fr");
  const franklin = suggestions(enhanced).getByRole("link", { name: "George Franklin" });
  await franklin.waitFor();
  const latency = performance.now() - started;
  await enhanced.evaluate(() => window.releaseFirstLookup());
  await enhanced.waitForFunction(() => window.oldLookupDelivered);
  assert.equal(await enhanced.evaluate(() => window.firstLookupSignal.aborted), true);
  assert.deepEqual(await enhanced.evaluate(() => window.cspViolations), []);
  assert.equal(await suggestions(enhanced).getByRole("link").count(), 1);
  await input.press("Tab");
  await enhanced.keyboard.press("Tab");
  assert.equal(await franklin.evaluate(node => node === document.activeElement), true);
  await enhanced.keyboard.press("Enter");
  await enhanced.getByRole("heading", { name: "George Franklin" }).waitFor();
  await enhanced.context().close();
  console.log(`PASS stale response, strict CSP and keyboard navigation (local type-to-link ${latency.toFixed(1)} ms)`);

  const touch = await signedIn({ hasTouch: true });
  await touch.getByLabel("Last name").fill("No");
  const firstOwner = suggestions(touch).getByRole("link", { name: "Resident 01 North" });
  await firstOwner.waitFor();
  assert.equal(await suggestions(touch).getByRole("link").count(), 8);
  await firstOwner.tap();
  await touch.getByRole("heading", { name: "Resident 01 North" }).waitFor();
  await touch.context().close();
  console.log("PASS bounded suggestions select an ordinary link with Chromium touch emulation");

  let failedImports = 0;
  const failed = await signedIn({}, page =>
    page.route("**/clinic-owner-suggestions.js?*", route => {
      failedImports++;
      return route.abort();
    }));
  assert.equal(failedImports, 1);
  await failed.getByLabel("Last name").fill("Fr");
  await failed.getByRole("button", { name: "Find owners" }).click();
  await searchResults(failed).getByRole("link", { name: "George Franklin" }).waitFor();
  await failed.context().close();
  console.log("PASS failed island import leaves native search usable");

  const unavailable = await signedIn({}, page =>
    page.route("**/woge/owner-suggestions?*", route => route.fulfill({ status: 503 })));
  await unavailable.getByLabel("Last name").fill("Fr");
  await unavailable.getByText("Suggestions are unavailable. Use Find owners to search.").waitFor();
  await unavailable.getByRole("button", { name: "Find owners" }).click();
  await searchResults(unavailable).getByRole("link", { name: "George Franklin" }).waitFor();
  await unavailable.context().close();
  console.log("PASS unavailable lookup announces the ordinary search fallback");
} catch (error) {
  console.error(log);
  throw error;
} finally {
  await browser?.close();
  if (app.exitCode === null && app.pid) {
    const exited = once(app, "exit");
    app.kill();
    await exited;
  }
  await rm(scratch, { recursive: true, force: true });
}
