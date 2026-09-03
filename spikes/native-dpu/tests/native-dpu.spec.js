import { readFile } from "node:fs/promises";
import { createServer } from "node:http";
import { fileURLToPath } from "node:url";
import { test, expect } from "@playwright/test";

const fixturePath = fileURLToPath(new URL("../fixtures/primitives.html", import.meta.url));
const primitiveFixture = await readFile(fixturePath);

let server;
let origin;
let activeStream;

test.beforeAll(async () => {
  server = createServer((request, response) => {
    if (request.url === "/primitives") {
      response.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
      response.end(primitiveFixture);
      return;
    }

    if (request.url === "/completion-order") {
      response.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
      response.flushHeaders();
      response.write(`<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Completion order</title></head><body>
        <main>
          <section id="slow"><?start name="slow">Slow pending<?end></section>
          <section id="fast"><?start name="fast">Fast pending<?end></section>
        </main>`);
      activeStream = response;
      return;
    }

    if (request.url === "/active-content") {
      response.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
      response.end(`<!doctype html><html lang="en"><body>
        <section id="active-content"><?start name="active-content">Pending<?end></section>
        <template for="active-content">
          <script>globalThis.__nativePatchScript = "executed"</script>
          <img src="/missing-native-patch" alt="" onerror="globalThis.__nativePatchHandler = 'executed'">
        </template>
      </body></html>`);
      return;
    }

    response.writeHead(404);
    response.end();
  });

  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  origin = `http://127.0.0.1:${address.port}`;
});

test.afterEach(() => {
  if (activeStream && !activeStream.writableEnded) activeStream.end("</body></html>");
  activeStream = undefined;
});

test.afterAll(async () => {
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
});

test("applies marker replacement, range replacement and repeated append", async ({ page }) => {
  await page.goto(`${origin}/primitives`);

  await expect(page.locator("#marker")).toHaveText("Before Inserted After");
  await expect(page.locator("#range")).toHaveText("Range ready");
  await expect(page.locator("#items > li")).toHaveText(["One", "Two"]);
  await expect(page.locator('head meta[name="dpu-head-patched"]')).toHaveAttribute("content", "yes");

  const markerNode = await page.locator("#items").evaluate((element) => {
    const node = [...element.childNodes].at(-1);
    return { type: node.nodeType, name: node.nodeName, value: node.nodeValue };
  });
  expect(markerNode).toEqual({ type: 7, name: "marker", value: 'name="items"' });
});

test("keeps nested, parent and shadow tree scopes separate", async ({ page }) => {
  await page.goto(`${origin}/primitives`);

  await expect(page.locator("#local-target")).toHaveText("Local ready");
  await expect(page.locator("#inner-range")).toHaveText("Inner ready");
  await expect(page.locator("#light-target")).toHaveText("Light ready");
  expect(await page.locator("#shadow-host").evaluate((host) => host.shadowRoot.querySelector("#shadow-target").textContent.trim())).toBe("Shadow ready");

  await expect(page.locator("#cross-scope-target")).toHaveText("Cross-scope pending");
  await expect(page.locator("#right-scope template[for='cross-scope']")).toHaveCount(1);
});

test("applies patches in server completion order", async ({ page }) => {
  await page.goto(`${origin}/completion-order`, { waitUntil: "commit" });
  await expect(page.locator("#fast")).toHaveText("Fast pending");
  await expect.poll(() => Boolean(activeStream)).toBe(true);

  activeStream.write('<template for="fast"><strong>Fast ready</strong></template>');
  await expect(page.locator("#fast")).toHaveText("Fast ready");
  await expect(page.locator("#slow")).toHaveText("Slow pending");

  activeStream.end('<template for="slow"><strong>Slow ready</strong></template></body></html>');
  await page.waitForLoadState("load");
  await expect(page.locator("#slow")).toHaveText("Slow ready");
});

test("does not let a fragment parser patch the existing document", async ({ page }) => {
  await page.goto(`${origin}/primitives`);

  await page.locator("body").evaluate((body) => {
    body.insertAdjacentHTML("beforeend", '<template for="dynamic-target"><strong>Fragment parser</strong></template>');
  });

  await expect(page.locator("#dynamic-target")).toHaveText("Dynamic pending");
  await expect(page.locator("body > template[for='dynamic-target']")).toHaveCount(1);
});

test("experimental streaming parser can patch an existing descendant", async ({ page }) => {
  await page.goto(`${origin}/primitives`);
  const supported = await page.evaluate(() => "streamAppendHTMLUnsafe" in Element.prototype);
  test.skip(!supported, "The script-initiated streaming HTML API is not enabled in this Chromium configuration");

  await page.locator("body").evaluate(async (body) => {
    const writer = body.streamAppendHTMLUnsafe().getWriter();
    await writer.write('<template for="dynamic-target"><strong>Streaming parser</strong></template>');
    await writer.close();
  });

  await expect(page.locator("#dynamic-target")).toHaveText("Streaming parser");
});

test("records that native parser patches execute active markup", async ({ page }) => {
  await page.goto(`${origin}/active-content`);

  await expect.poll(() => page.evaluate(() => globalThis.__nativePatchHandler)).toBe("executed");
  expect(await page.evaluate(() => globalThis.__nativePatchScript)).toBe("executed");
  await expect(page.locator("#active-content script")).toHaveCount(1);
});

test("exposes a detectable template-for capability", async ({ page }) => {
  await page.goto(`${origin}/primitives`);

  expect(await page.evaluate(() => "htmlFor" in HTMLTemplateElement.prototype)).toBe(true);
});
