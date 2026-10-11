import { expect, test } from "@playwright/test";

async function start(page) {
  await page.goto("/navigation/a");
  await page.waitForFunction(() => !!globalThis.navigationHandle);
  await page.evaluate(() => { globalThis.initialToken = globalThis.documentToken; });
}

async function sameDocument(page) {
  expect(await page.evaluate(() => globalThis.initialToken === globalThis.documentToken)).toBe(true);
}

test("eligible GET swaps title, metadata and region; history, reload and deep links remain real", async ({ page }) => {
  await start(page);
  const requests = [];
  page.on("request", (request) => { if (request.url().includes("/navigation/")) requests.push(request); });
  await page.getByRole("link", { name: "Page B", exact: true }).click();
  await expect(page).toHaveURL(/\/navigation\/b$/);
  await expect(page).toHaveTitle("Page B");
  await expect(page.locator('meta[name="description"]')).toHaveAttribute("content", "Second page");
  await expect(page.getByRole("heading")).toBeFocused();
  await expect(page.locator('[role="status"]')).toHaveText("Page B");
  await sameDocument(page);
  expect(requests[0].method()).toBe("GET");
  expect(requests[0].resourceType()).toBe("fetch");
  expect(requests[0].headers().accept).toBe("text/html");
  await page.goBack();
  await expect(page.getByRole("heading")).toHaveText("Page A");
  await sameDocument(page);
  await page.goForward();
  await expect(page.getByRole("heading")).toHaveText("Page B");
  await sameDocument(page);
  await page.reload();
  await expect(page.getByRole("heading")).toHaveText("Page B");
  expect(await page.evaluate(() => globalThis.initialToken)).toBeUndefined();
  await page.goto("/navigation/b");
  await expect(page).toHaveTitle("Page B");
});

test("preserves shell, keyed dirty controls and island nodes but navigation owns heading focus", async ({ page }) => {
  await start(page);
  await page.locator("#shell").fill("Shell stays");
  await page.getByRole("textbox", { name: "Route draft" }).fill("Browser draft");
  await page.evaluate(() => { globalThis.editor = document.querySelector("[data-woge-island]"); });
  await page.getByRole("link", { name: "Page B", exact: true }).click();
  await expect(page.getByRole("heading")).toHaveText("Page B");
  await expect(page.locator("#shell")).toHaveValue("Shell stays");
  await expect(page.getByRole("textbox", { name: "Route draft" })).toHaveValue("Browser draft");
  expect(await page.evaluate(() => globalThis.editor === document.querySelector("[data-woge-island]"))).toBe(true);
  await expect(page.getByRole("heading")).toBeFocused();
});

test("scroll is owned explicitly, restored on traversal, and fragments scroll without motion", async ({ page }) => {
  await start(page);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.evaluate(() => scrollTo(0, 900));
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(900);
  await page.getByRole("link", { name: "Page B", exact: true }).dispatchEvent("click", { button: 0 });
  await expect(page).toHaveURL(/\/navigation\/b$/);
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
  await page.goBack();
  await expect(page.getByRole("heading")).toHaveText("Page A");
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(900);
  await page.getByRole("link", { name: "Fragment", exact: true }).dispatchEvent("click", { button: 0 });
  await expect(page).toHaveURL(/\/navigation\/b#bottom$/);
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(1500);
  await page.evaluate(() => navigationHandle.dispose());
  expect(await page.evaluate(() => history.scrollRestoration)).toBe("auto");
});

test("modified clicks, targets, download, external URLs, schemes and opt-outs are not intercepted", async ({ page }) => {
  await start(page);
  const outcomes = await page.evaluate(() => {
    const result = [];
    const send = (link, options) => {
      document.addEventListener("click", (event) => {
        result.push(event.defaultPrevented);
        event.preventDefault(); // Prevent the native action only after observing the module's decision.
      }, { once: true });
      link.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, button: 0, ...options }));
    };
    for (const options of [{ ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { altKey: true }, { button: 1 }]) {
      send([...document.querySelectorAll("a")].find((a) => a.textContent === "Page B"), options);
    }
    for (const text of ["Target", "Download", "External", "Mail", "Opt out", "External relationship", "Local fragment"]) {
      send([...document.querySelectorAll("a")].find((a) => a.textContent === text), {});
    }
    const unmarked = document.createElement("a");
    unmarked.href = "/navigation/b";
    document.body.append(unmarked);
    send(unmarked, {});
    return result;
  });
  expect(outcomes).toEqual(Array(13).fill(false));
  await sameDocument(page);
});

for (const name of ["Error", "Skew", "Non HTML", "Assets", "Managed"]) {
  test(`${name} escapes to one full native navigation`, async ({ page }) => {
    await start(page);
    const navigation = page.waitForNavigation({ waitUntil: "load" });
    const documentRequest = page.waitForEvent("request", (request) => request.isNavigationRequest() &&
      request.url().includes("/navigation/"));
    await page.getByRole("link", { name, exact: true }).click();
    const request = await documentRequest;
    expect(request.method()).toBe("GET");
    await navigation;
    expect(await page.evaluate(() => globalThis.initialToken)).toBeUndefined();
  });
}

test("same-origin redirect commits final URL; network errors escape without retries", async ({ page }) => {
  await start(page);
  await page.getByRole("link", { name: "Redirect", exact: true }).click();
  await expect(page).toHaveURL(/\/navigation\/b$/);
  await sameDocument(page);
  await page.route("**/navigation/a", async (route) => {
    if (route.request().resourceType() === "fetch") await route.abort("failed");
    else await route.continue();
  });
  await page.getByRole("link", { name: "Page A", exact: true }).click();
  await expect(page).toHaveURL(/\/navigation\/a$/);
  await page.waitForLoadState();
  expect(await page.evaluate(() => globalThis.initialToken)).toBeUndefined();
});

test("managed current documents and opt-out links navigate natively", async ({ page }) => {
  await start(page);
  await page.getByRole("link", { name: "Opt out", exact: true }).click();
  await page.waitForLoadState();
  expect(await page.evaluate(() => globalThis.initialToken)).toBeUndefined();
  await page.evaluate(() => {
    globalThis.initialToken = globalThis.documentToken;
    const meta = document.createElement("meta");
    meta.name = "woge-render-session";
    meta.content = "managed";
    document.head.append(meta);
  });
  await page.getByRole("link", { name: "Page A", exact: true }).click();
  await expect(page.getByRole("heading")).toHaveText("Page A");
  expect(await page.evaluate(() => globalThis.initialToken)).toBeUndefined();
});

test("native hash entries and repeated route entries restore their own scroll positions", async ({ page }) => {
  await start(page);
  await page.evaluate(() => scrollTo(0, 500));
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(500);
  await page.getByRole("link", { name: "Local fragment", exact: true }).dispatchEvent("click", { button: 0 });
  await expect(page).toHaveURL(/\/navigation\/a#bottom$/);
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(1500);
  await sameDocument(page);
  await page.evaluate(() => history.back());
  await expect(page).toHaveURL(/\/navigation\/a$/);
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(500);
  await page.getByRole("link", { name: "Page B", exact: true }).dispatchEvent("click", { button: 0 });
  await expect(page.getByRole("heading")).toHaveText("Page B");
  await page.evaluate(() => scrollTo(0, 800));
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(800);
  await page.getByRole("link", { name: "Page A", exact: true }).dispatchEvent("click", { button: 0 });
  await expect(page.getByRole("heading")).toHaveText("Page A");
  await page.goBack();
  await expect(page.getByRole("heading")).toHaveText("Page B");
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(800);
  await page.goBack();
  await expect(page.getByRole("heading")).toHaveText("Page A");
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(500);
});

test("cross-origin redirect takes the original URL's complete native redirect path", async ({ page }) => {
  await start(page);
  const navigation = page.waitForNavigation({ waitUntil: "load" });
  await page.getByRole("link", { name: "Cross origin redirect", exact: true }).click();
  await navigation;
  await expect(page).toHaveURL("http://localhost:4173/navigation/b");
  await expect(page).toHaveTitle("Page B");
  expect(await page.evaluate(() => globalThis.initialToken)).toBeUndefined();
});

test("target opens its native browsing context and download is a native attachment", async ({ page }) => {
  await start(page);
  const popupEvent = page.waitForEvent("popup");
  await page.getByRole("link", { name: "Target", exact: true }).click();
  const popup = await popupEvent;
  await popup.waitForLoadState();
  await expect(popup).toHaveURL(/\/navigation\/b$/);
  await expect(popup).toHaveTitle("Page B");
  await popup.close();
  const downloadEvent = page.waitForEvent("download");
  await page.getByRole("link", { name: "Download", exact: true }).click();
  expect((await downloadEvent).suggestedFilename()).toBe("page.html");
  await sameDocument(page);
});

test("dirty-state conflicts escape before a partial region or metadata update", async ({ page }) => {
  await start(page);
  await page.getByRole("textbox", { name: "Route draft" }).fill("Do not silently discard");
  await page.route("**/navigation/b", async (route) => {
    if (route.request().resourceType() !== "fetch") { await route.continue(); return; }
    const response = await route.fetch();
    const body = (await response.text()).replace('data-woge-state-key="draft"', 'data-woge-state-key="different"');
    await route.fulfill({ response, body });
  });
  const navigation = page.waitForNavigation({ waitUntil: "load" });
  await page.getByRole("link", { name: "Page B", exact: true }).click();
  await navigation;
  await expect(page.getByRole("heading")).toHaveText("Page B");
  expect(await page.evaluate(() => globalThis.initialToken)).toBeUndefined();
});

test("superseded GET cannot commit or force a full navigation; disposal cancels owned work", async ({ page }) => {
  await start(page);
  let release;
  const delayed = new Promise((resolve) => { release = resolve; });
  let requested;
  const started = new Promise((resolve) => { requested = resolve; });
  await page.route("**/navigation/b", async (route) => {
    if (route.request().resourceType() !== "fetch") { await route.continue(); return; }
    requested();
    await delayed;
    await route.continue().catch(() => {});
  });
  await page.getByRole("link", { name: "Page B", exact: true }).click();
  await started;
  await page.evaluate(() => navigationHandle.dispose());
  release();
  await page.waitForTimeout(50);
  await expect(page).toHaveURL(/\/navigation\/a$/);
  await sameDocument(page);
  expect(await page.evaluate(() => history.scrollRestoration)).toBe("auto");
  await page.unroute("**/navigation/b");
  const navigation = page.waitForNavigation({ waitUntil: "load" });
  await page.getByRole("link", { name: "Page B", exact: true }).click();
  await navigation;
  expect(await page.evaluate(() => globalThis.initialToken)).toBeUndefined();
});
