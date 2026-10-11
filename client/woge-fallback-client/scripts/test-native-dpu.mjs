import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { chromium } from "@playwright/test";

const origin = process.argv[2];
assert.ok(origin, "The real adapter TCK must provide its origin");
const asset = await readFile(new URL("../dist/dpu.js", import.meta.url), "utf8");
const browser = await chromium.launch({ args: ["--enable-blink-features=DeclarativePartialUpdates"] });
try {
  const outcomes = [];
  for (const mode of ["fallback", "native"]) {
    const context = await browser.newContext();
    await context.route("**/assets/woge/dpu.js", (route) =>
      mode === "native" ? route.abort() : route.fulfill({ body: asset, contentType: "text/javascript" }));
    try {
      const page = await context.newPage();
      const errors = [];
      page.on("pageerror", (error) => errors.push(error.message));
      const fetched = [];
      page.on("request", (request) => {
        if (["fetch", "xhr"].includes(request.resourceType())) fetched.push(request.url());
      });
      const response = await page.goto(`${origin}/woge-tck/dpu/${mode}/safe`);
      assert.equal(response.status(), 200);
      assert.equal(response.headers()["cache-control"], "no-store");
      assert.match(response.headers().vary, /Cookie/u);
      assert.ok(await page.evaluate(() => "htmlFor" in HTMLTemplateElement.prototype),
        "Chromium must expose native DPU; this conformance gate must not skip");
      await page.locator("[data-woge-region=results] li").last().waitFor();
      outcomes.push({
        text: await page.locator("[data-woge-region=results] li").allTextContents(),
        revision: await page.locator("[data-woge-region=results]").getAttribute("data-woge-revision"),
        accessibility: await page.locator("[data-woge-region=results]").ariaSnapshot(),
      });
      assert.deepEqual(outcomes.at(-1).text, ["Alpha", "Beta", "Gamma"]);
      assert.equal(outcomes.at(-1).revision, "1");
      const bytes = await response.text();
      assert.equal(bytes.includes("<template for="), mode === "native");
      assert.deepEqual(fetched, [], "Initial document replacements must not open a detached fetch");
      assert.deepEqual(errors, []);
    } finally {
      await context.close();
    }
  }
  assert.deepEqual(outcomes[0], outcomes[1], "Unchanged application Kotlin must produce identical semantic outcomes");

  const context = await browser.newContext();
  await context.route("**/assets/woge/dpu.js", (route) => route.fulfill({ body: asset, contentType: "text/javascript" }));
  try {
    const page = await context.newPage();
    for (const scenario of ["late-unsafe", "loading-unsafe"]) {
      const response = await page.goto(`${origin}/woge-tck/dpu/native/${scenario}`);
      await page.locator("[data-woge-region=results] li").last().waitFor();
      assert.deepEqual(await page.locator("[data-woge-region=results] li").allTextContents(), ["Alpha", "Beta", "Gamma"]);
      assert.equal((await response.text()).includes("<template for="), false);
    }
    const mixed = await page.goto(`${origin}/woge-tck/dpu/native/mixed`);
    await page.locator("[data-woge-region=results] li").last().waitFor();
    assert.equal(await page.locator("[data-woge-region=native-sibling]").textContent(), "Native sibling complete");
    assert.equal((await mixed.text()).includes("<template for="), true);
    assert.deepEqual(await page.locator("[data-woge-region=results] li").allTextContents(), ["Alpha", "Beta", "Gamma"]);
    await context.clearCookies();
    const first = await page.goto(`${origin}/woge-tck/dpu/auto/safe`);
    await page.locator("[data-woge-region=results] li").last().waitFor();
    assert.equal((await first.text()).includes("<template for="), false);
    assert.equal((await context.cookies()).find((cookie) => cookie.name === "woge_dpu")?.value, "1");
    const second = await page.reload();
    await page.locator("[data-woge-region=results] li").last().waitFor();
    assert.equal((await second.text()).includes("<template for="), true);
  } finally {
    await context.close();
  }

  const noScript = await browser.newContext({ javaScriptEnabled: false });
  try {
    const page = await noScript.newPage();
    await page.goto(`${origin}/woge-tck/dpu/fallback/safe`);
    await page.getByRole("link", { name: "View complete results" }).click();
    assert.deepEqual(await page.getByRole("listitem").allTextContents(), ["Alpha", "Beta", "Gamma"]);
  } finally {
    await noScript.close();
  }
  console.log("[woge-dpu] Real adapter native/fallback, eligibility, cookie and HTML-only navigation passed");
} finally {
  await browser.close();
}
