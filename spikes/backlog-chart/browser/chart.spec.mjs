import { expect, test } from "@playwright/test";

const documentPath = "/backlog";
const choosePath = "/woge-actions/select-backlog-snapshot";
const chartFile = "/assets/chart/backlog.js";
const island = '[data-woge-state-key="warehouse-backlog"]';

async function reveal(page) {
  await page.getByRole("heading", { name: "Backlog trend" }).scrollIntoViewIfNeeded();
  await expect(page.getByRole("img", { name: "Packing and dispatch backlog by day" })).toBeVisible();
}

async function changeRange(page) {
  await page.getByLabel("Date range").selectOption("late");
  const result = page.waitForResponse((response) => new URL(response.url()).pathname === choosePath);
  await page.getByRole("button", { name: "Show backlog" }).click();
  return await result;
}

test("native report reaches accurate daily rows without loading a chart", async ({ browser, baseURL }) => {
  const context = await browser.newContext({ javaScriptEnabled: false, baseURL });
  try {
    const page = await context.newPage();
    const assets = [];
    page.on("request", (request) => {
      if (request.url().includes(chartFile)) assets.push(request.url());
    });
    await page.goto(documentPath);
    const response = await changeRange(page);
    expect(response.status()).toBe(303);
    await expect(page).toHaveURL(/\/backlog\?window=late$/);
    await expect(page.getByRole("table", { name: "Open orders by day, June 15–28" })).toBeVisible();
    await expect(page.locator("tbody tr")).toHaveCount(14);
    await expect(page.getByRole("rowheader", { name: "2026-06-28" })).toBeVisible();
    await expect(page.getByRole("img")).toHaveCount(0);
    expect(assets).toHaveLength(0);
  } finally {
    await context.close();
  }
});

test("keyed chart keeps the controller and local view while a server patch changes its data", async ({ page, browser }) => {
  const assets = [];
  await page.addInitScript(() => {
    window.cspFailures = [];
    document.addEventListener("securitypolicyviolation", (event) => window.cspFailures.push(event.violatedDirective));
  });
  page.on("request", (request) => {
    if (request.url().includes(chartFile)) assets.push(request.url());
  });
  const document = await page.goto(documentPath);
  expect(document.headers()["content-security-policy"]).not.toContain("unsafe-");
  await expect(page.locator(island)).not.toBeInViewport();
  expect(assets).toHaveLength(0);

  await page.evaluate(() => performance.mark("chart-reveal"));
  await reveal(page);
  const revealMs = await page.evaluate(() => performance.measure("chart-display", "chart-reveal").duration);
  expect(assets).toHaveLength(1);
  const packing = page.getByRole("button", { name: "Show packing orders" });
  const latest = page.getByRole("button", { name: "Show last four days" });
  await packing.focus();
  await page.keyboard.press("Space");
  await latest.focus();
  await page.evaluate(() => performance.mark("chart-keyboard"));
  await page.keyboard.press("Enter");
  await expect(packing).toHaveAttribute("aria-pressed", "false");
  await expect(latest).toHaveAttribute("aria-pressed", "true");
  const keyboardMs = await page.evaluate(() => performance.measure("chart-keyboard-update", "chart-keyboard").duration);
  await page.locator(island).evaluate((root) => { window.originalChartRoot = root; });
  const before = await page.locator("canvas").evaluate((canvas) => canvas.toDataURL());

  const response = await changeRange(page);
  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain("application/vnd.woge.patch-stream");
  await expect(page.getByRole("table", { name: "Open orders by day, June 15–28" })).toBeVisible();
  await expect(page.getByRole("status")).toHaveText("Showing backlog for June 15–28.");
  expect(await page.locator(island).evaluate((root) => root === window.originalChartRoot)).toBe(true);
  await expect(packing).toHaveAttribute("aria-pressed", "false");
  await expect(latest).toHaveAttribute("aria-pressed", "true");
  await expect.poll(() => page.locator("canvas").evaluate((canvas) => canvas.toDataURL())).not.toBe(before);
  expect(assets).toHaveLength(1);
  expect(await page.evaluate(() => window.cspFailures)).toEqual([]);

  const timing = await page.evaluate((url) => {
    const { transferSize, encodedBodySize, duration } = performance.getEntriesByName(url).at(-1);
    return { transferSize, encodedBodySize, moduleRequestMs: Math.round(duration * 10) / 10 };
  }, assets[0]);
  const actionTiming = response.request().timing();
  console.log(JSON.stringify({
    ...timing,
    chromium: browser.version(),
    revealMs: Math.round(revealMs * 10) / 10,
    keyboardMs: Math.round(keyboardMs * 10) / 10,
    actionResponseMs: Math.round((actionTiming.responseEnd - actionTiming.requestStart) * 10) / 10,
  }));
});

test("an explicit reset disposes the old Chart.js canvas and starts a fresh local view", async ({ page }) => {
  await page.goto(`${documentPath}?reset=1`);
  await reveal(page);
  await page.getByRole("button", { name: "Show packing orders" }).click();
  await page.locator("canvas").evaluate((canvas) => {
    window.oldChart = canvas;
    window.chartDisplay = canvas.style.display;
  });
  expect(await page.evaluate(() => window.chartDisplay)).toBe("block");

  expect((await changeRange(page)).status()).toBe(200);
  await reveal(page);
  await expect(page.getByRole("button", { name: "Show packing orders" })).toHaveAttribute("aria-pressed", "true");
  expect(await page.evaluate(() => ({
    connected: window.oldChart.isConnected,
    display: window.oldChart.style.display,
  }))).toEqual({ connected: false, display: "" });
});

test("a failed library import keeps the table and its native link useful", async ({ page }) => {
  await page.addInitScript(() => {
    window.islandErrors = [];
    document.addEventListener("woge:island-error", (event) => window.islandErrors.push(event.detail.code));
  });
  const blocked = [];
  await page.route("**/assets/chart/backlog.js?*", (route) => {
    blocked.push(route.request().url());
    return route.abort();
  });
  await page.goto(documentPath);
  const link = page.getByRole("link", { name: "Read the daily totals" });
  await link.scrollIntoViewIfNeeded();
  await expect.poll(() => page.evaluate(() => window.islandErrors)).toContain("WOGE_ISLAND_IMPORT_FAILED");
  await expect(page.getByRole("img")).toHaveCount(0);
  await link.click();
  await expect(page).toHaveURL(/#daily-backlog$/);
  await expect(page.locator("tbody tr")).toHaveCount(14);
  expect((await changeRange(page)).status()).toBe(200);
  await expect(page.getByRole("table", { name: "Open orders by day, June 15–28" })).toBeVisible();
  await expect(page.getByRole("img")).toHaveCount(0);
  expect(blocked).toHaveLength(1);
});

test("touch controls work and direct invalid or cross-origin selections are rejected", async ({ browser, baseURL, request }) => {
  const context = await browser.newContext({ baseURL, hasTouch: true, viewport: { width: 390, height: 600 } });
  try {
    const page = await context.newPage();
    await page.goto(documentPath);
    await reveal(page);
    const latest = page.getByRole("button", { name: "Show last four days" });
    await latest.tap();
    await expect(latest).toHaveAttribute("aria-pressed", "true");
    const invalid = await request.post(choosePath, { headers: { Origin: baseURL }, form: { window: "unknown" } });
    expect(invalid.status()).toBe(400);
    const foreign = await request.post(choosePath, {
      headers: { Origin: "https://other.example" },
      form: { window: "late" },
    });
    expect(foreign.status()).toBe(403);
  } finally {
    await context.close();
  }
});

test("SSE replaces the chart panel with keyed preservation or an explicit reset", async ({ context, page, request, baseURL }) => {
  const livePath = "/backlog/live";
  const resetPage = await context.newPage();
  const refresh = (reader) => reader.waitForResponse((response) => {
    const url = new URL(response.url());
    return url.pathname === livePath && url.searchParams.has("_woge_refresh");
  });

  for (const [reader, path] of [[page, livePath], [resetPage, `${livePath}?reset=1`]]) {
    const opened = reader.waitForResponse((response) => {
      const url = new URL(response.url());
      return url.pathname === livePath && url.searchParams.has("_woge_live");
    });
    const initial = refresh(reader);
    await reader.goto(path);
    const stream = await opened;
    expect(stream.status()).toBe(200);
    expect(stream.headers()["content-type"]).toContain("text/event-stream");
    expect((await initial).status()).toBe(200);
    await expect(reader.locator("#backlog-panel")).toHaveAttribute("data-woge-revision", "1");
    await reveal(reader);
    await reader.getByRole("button", { name: "Show packing orders" }).click();
    await reader.getByRole("button", { name: "Show last four days" }).click();
    await reader.locator(island).evaluate((root) => {
      window.liveChart = {
        root,
        canvas: root.querySelector("canvas"),
        table: document.querySelector("#daily-backlog"),
      };
    });
  }

  const next = [refresh(page), refresh(resetPage)];
  const before = await page.locator("canvas").evaluate((canvas) => canvas.toDataURL());
  const published = await request.post("/woge-actions/select-live-backlog-snapshot", {
    headers: { Origin: baseURL },
    form: { window: "late" },
    maxRedirects: 0,
  });
  expect(published.status()).toBe(303);
  for (const response of await Promise.all(next)) {
    expect(response.status()).toBe(200);
    expect(response.headers()["content-type"]).toContain("application/vnd.woge.patch-stream");
  }

  for (const reader of [page, resetPage]) {
    await expect(reader.locator("#backlog-panel")).toHaveAttribute("data-woge-revision", "2");
    await expect(reader.getByRole("table", { name: "Open orders by day, June 15–28" })).toBeVisible();
    expect(await reader.evaluate(() => window.liveChart.table.isConnected)).toBe(false);
  }
  expect(await page.locator(island).evaluate((root) => root === window.liveChart.root)).toBe(true);
  await expect(page.getByRole("button", { name: "Show packing orders" })).toHaveAttribute("aria-pressed", "false");
  await expect(page.getByRole("button", { name: "Show last four days" })).toHaveAttribute("aria-pressed", "true");
  await expect.poll(() => page.locator("canvas").evaluate((canvas) => canvas.toDataURL())).not.toBe(before);

  await reveal(resetPage);
  await expect(resetPage.getByRole("button", { name: "Show packing orders" })).toHaveAttribute("aria-pressed", "true");
  await expect(resetPage.getByRole("button", { name: "Show last four days" })).toHaveAttribute("aria-pressed", "false");
  expect(await resetPage.locator(island).evaluate((root) => ({
    same: root === window.liveChart.root,
    previousCanvasConnected: window.liveChart.canvas.isConnected,
    previousCanvasDisplay: window.liveChart.canvas.style.display,
  }))).toEqual({ same: false, previousCanvasConnected: false, previousCanvasDisplay: "" });
});
