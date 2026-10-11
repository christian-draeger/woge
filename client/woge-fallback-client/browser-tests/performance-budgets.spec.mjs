import { expect, test } from "@playwright/test";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { encodeStream, patchFrame, completeFrame } from "../test-support/protocol-fixture.mjs";

const policy = JSON.parse(await readFile(new URL("../../../testing/performance/browser-policy.json", import.meta.url), "utf8"));

test("production timing smoke budgets on a constrained browser", async ({ page, browserName }, testInfo) => {
  test.skip(browserName !== "chromium", "CDP CPU/network constraints are Chromium-only; conformance still runs all engines");
  const cdp = await page.context().newCDPSession(page);
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: policy.cpuThrottleRate });
  await cdp.send("Network.enable");
  await cdp.send("Network.setCacheDisabled", { cacheDisabled: true });
  await cdp.send("Network.emulateNetworkConditions", {
    offline: false, latency: policy.latencyMs,
    downloadThroughput: policy.downloadBytesPerSecond, uploadThroughput: policy.uploadBytesPerSecond,
  });
  const samples = [];
  for (let sample = 0; sample < policy.samples; sample++) {
    await page.goto("/deferred");
    await page.waitForFunction(() => deferredTimeline.applied.length === 1);
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const deferred = await page.evaluate(() => ({
      // FCP measures an actual paint of the native loading shell, not module evaluation.
      firstShell: performance.getEntriesByName("first-contentful-paint")[0]?.startTime,
      firstPatch: deferredTimeline.applied[0].at,
    }));
    expect(deferred.firstShell, "A loading shell must actually paint").toBeGreaterThan(0);
    await page.evaluate(() => fetch(`/release-deferred?run=${encodeURIComponent(deferredRunId)}`, { method: "POST" }));
    await page.waitForFunction(() => deferredCompletion?.patchCount === 2);

    await page.goto("/action-forms");
    await page.waitForFunction(() => globalThis.actions !== undefined);
    const actionRoundTrip = await page.evaluate(() => new Promise((resolve) => {
      const start = performance.now();
      const status = document.querySelector("#status");
      const observer = new MutationObserver(() => {
        if (status.textContent !== "Saved") return;
        observer.disconnect();
        requestAnimationFrame(() => requestAnimationFrame(() => resolve(performance.now() - start)));
      });
      observer.observe(status, { childList: true, subtree: true });
      const form = document.querySelector("form");
      form.requestSubmit(form.querySelector("button"));
    }));
    expect(await page.evaluate(() => actionErrors)).toEqual([]);

    const html = `<table><tbody>${Array.from({ length: 102 }, (_, row) =>
      `<tr><td>Deployment ${row} &lt;ready&gt; &amp; healthy</td><td><button>Inspect</button></td></tr>`).join("")}</tbody></table>`;
    const bytes = encodeStream([patchFrame({ html, baseRevision: 1, nextRevision: 2 }), completeFrame()]);
    const patchApply = await page.evaluate(async (values) => {
      const stream = new ReadableStream({ start(controller) {
        controller.enqueue(Uint8Array.from(values)); controller.close();
      } });
      const runtime = Woge.createWogePatchRuntime(document);
      const start = performance.now();
      await runtime.applyPatchStream(stream);
      return performance.now() - start;
    }, Array.from(bytes));
    await expect(page.locator("main tbody tr")).toHaveCount(102);
    samples.push({ ...deferred, actionRoundTrip, patchApply });
  }
  const median = Object.fromEntries(Object.keys(policy.ceilingsMs).map((key) => [
    key, samples.map((value) => value[key]).sort((a, b) => a - b)[Math.floor(samples.length / 2)],
  ]));
  const result = { conditions: policy, samples, median };
  await mkdir(new URL("../build/", import.meta.url), { recursive: true });
  await writeFile(new URL("../build/browser-budget-metrics.json", import.meta.url), `${JSON.stringify(result, null, 2)}\n`);
  await testInfo.attach("production-performance", { body: JSON.stringify(result, null, 2), contentType: "application/json" });
  console.log(`[woge-browser-budgets] ${JSON.stringify(median)}`);
  for (const [key, ceiling] of Object.entries(policy.ceilingsMs)) {
    expect(median[key], `${key}: median of ${policy.samples} at 4x CPU`).toBeLessThanOrEqual(ceiling);
  }
});
