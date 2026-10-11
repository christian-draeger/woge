import { expect, test } from "../test-support/strict-csp.mjs";
import { mkdir, readFile, writeFile } from "node:fs/promises";

const policy = JSON.parse(await readFile(new URL("../../../testing/performance/browser-policy.json", import.meta.url), "utf8"));
const host = process.env.WOGE_REFERENCE_HOST ?? "spring-webflux";

test("real adapter production budgets under constrained Chromium", async ({ page, browserName }, testInfo) => {
  test.skip(browserName !== "chromium", "CDP constraints require Chromium; other engines retain conformance coverage");
  const cdp = await page.context().newCDPSession(page);
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: policy.cpuThrottleRate });
  await cdp.send("Network.enable");
  await cdp.send("Network.setCacheDisabled", { cacheDisabled: true });
  await cdp.send("Network.emulateNetworkConditions", {
    offline: false, latency: policy.latencyMs,
    downloadThroughput: policy.downloadBytesPerSecond, uploadThroughput: policy.uploadBytesPerSecond,
  });
  await page.addInitScript(() => {
    globalThis.performancePatches = [];
    const starts = new WeakMap();
    document.addEventListener("woge:before-replace", (event) => starts.set(event.target, performance.now()));
    document.addEventListener("woge:after-replace", (event) => {
      performancePatches.push({ at: performance.now(), duration: performance.now() - starts.get(event.target) });
    });
  });
  const samples = [];
  for (let sample = 0; sample < policy.samples; sample++) {
    await page.goto("/projects/woge");
    await expect(page.locator('[data-woge-region][data-woge-revision="1"]')).toHaveCount(3);
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const dashboard = await page.evaluate(() => ({
      firstShell: performance.getEntriesByName("first-contentful-paint")[0]?.startTime,
      firstPatch: performancePatches[0]?.at,
      patchApply: Math.max(...performancePatches.map((value) => value.duration)),
    }));
    expect(dashboard.firstShell).toBeGreaterThan(0);
    expect(dashboard.firstPatch).toBeGreaterThan(0);
    await page.goto("/projects/woge/tasks");
    const title = `Performance ${host} ${sample}`;
    await page.getByLabel("Task title").fill(title);
    const actionRoundTrip = await page.evaluate((value) => new Promise((resolve) => {
      const tasks = document.querySelector("#board-tasks").closest("[data-woge-region]");
      const observer = new MutationObserver(() => {
        if (!tasks.textContent.includes(value)) return;
        observer.disconnect();
        requestAnimationFrame(() => requestAnimationFrame(() => resolve(performance.now() - start)));
      });
      observer.observe(tasks, { childList: true, subtree: true });
      const start = performance.now();
      const form = document.querySelector("#board-form");
      form.requestSubmit(form.querySelector('button[type="submit"]'));
    }), title);
    await expect(page.locator("#board-tasks li", { hasText: title })).toHaveCount(1);
    samples.push({ ...dashboard, actionRoundTrip });
  }
  const median = Object.fromEntries(Object.keys(policy.ceilingsMs).map((key) => [
    key, samples.map((value) => value[key]).sort((a, b) => a - b)[Math.floor(samples.length / 2)],
  ]));
  const result = { host, conditions: policy, samples, median };
  await mkdir(new URL("../build/", import.meta.url), { recursive: true });
  await writeFile(new URL(`../build/browser-budget-${host}.json`, import.meta.url), `${JSON.stringify(result, null, 2)}\n`);
  await testInfo.attach("production-performance", { body: JSON.stringify(result, null, 2), contentType: "application/json" });
  console.log(`[woge-browser-budgets][${host}] ${JSON.stringify(median)}`);
  for (const [key, ceiling] of Object.entries(policy.ceilingsMs)) expect(median[key], key).toBeLessThanOrEqual(ceiling);
});
