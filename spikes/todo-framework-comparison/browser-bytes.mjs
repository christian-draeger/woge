import { chromium } from "playwright";
const browser = await chromium.launch({ args: [`--unsafely-treat-insecure-origin-as-secure=${new URL(process.env.BENCH_URL).origin}`] });
try {
  const page = await browser.newPage();
  const failures = [];
  const httpFailures = [];
  const pageErrors = [];
  page.on("requestfailed", request => failures.push({ url: request.url(), type: request.resourceType(), error: request.failure()?.errorText }));
  page.on("response", response => { if (response.status() >= 400) httpFailures.push({ url: response.url(), status: response.status() }); });
  page.on("pageerror", error => pageErrors.push(error.message));
  await page.goto(process.env.BENCH_URL);
  await page.waitForTimeout(500);
  const entries = await page.evaluate(() => performance.getEntriesByType("resource").map(e => ({
    url: e.name, type: e.initiatorType, transferSize: e.transferSize, encodedBodySize: e.encodedBodySize, decodedBodySize: e.decodedBodySize,
  })));
  console.log(JSON.stringify({ browser: browser.version(), cache: "new context, no compression configured", entries, failures, httpFailures, pageErrors }));
} finally { await browser.close(); }
