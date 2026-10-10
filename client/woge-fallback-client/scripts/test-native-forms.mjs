import assert from "node:assert/strict";
import { chromium } from "@playwright/test";

const origin = process.argv[2];
assert.ok(origin, "The adapter TCK must provide its server origin");

const browser = await chromium.launch();
try {
  let expectedMutations = 1; // The base HTTP contract already performed one authorized mutation.
  for (const javaScriptEnabled of [false, true]) {
    const context = await browser.newContext({
      javaScriptEnabled,
      extraHTTPHeaders: { "X-Tck-Subject": "tck-user" },
    });
    try {
      const page = await context.newPage();
      const complete = `${origin}/woge-tck/action-complete`;
      await page.goto(complete);
      const form = page.locator("form");
      assert.equal(await form.getAttribute("method"), "post");
      assert.equal(await form.getAttribute("enctype"), "application/x-www-form-urlencoded");
      assert.equal(await form.getAttribute("accept-charset"), "UTF-8");
      assert.equal(await form.getAttribute("action"), "/woge-actions/tck-submit");

      await page.getByRole("textbox", { name: "Command value" }).fill("accepted");
      const [response] = await Promise.all([
        page.waitForNavigation(),
        page.getByRole("button", { name: "Submit command" }).click(),
      ]);
      assert.equal(response.status(), 200);
      assert.equal(page.url(), complete);
      assert.equal(response.request().method(), "GET");
      assert.equal(response.request().redirectedFrom().method(), "POST");
      assert.equal((await response.request().redirectedFrom().response()).status(), 303);
      expectedMutations++;
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));
      await page.reload();
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));

      // A duplicate scalar is submitted using two ordinary named controls, not a JS-only request.
      await page.goto(complete);
      await page.getByRole("textbox", { name: "Command value" }).fill("<private>");
      const [invalid] = await Promise.all([
        page.waitForNavigation(),
        page.getByRole("button", { name: "Submit ambiguous command" }).click(),
      ]);
      assert.equal(invalid.status(), 400);
      assert.equal(await page.getByRole("textbox", { name: "Command value" }).inputValue(), "<private>");
      assert.ok((await page.textContent("body")).includes("value: REPEATED"));
      assert.equal(await page.locator("private").count(), 0);
      await page.goto(complete);
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));

      await page.getByRole("textbox", { name: "Command value" }).fill("denied");
      const [denied] = await Promise.all([
        page.waitForNavigation(),
        page.getByRole("button", { name: "Submit command" }).click(),
      ]);
      assert.equal(denied.status(), 403);
      await page.goto(complete);
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));

      await context.setExtraHTTPHeaders({ "X-Tck-Subject": "other-user" });
      await page.getByRole("textbox", { name: "Command value" }).fill("accepted");
      const [unauthorized] = await Promise.all([
        page.waitForNavigation(),
        page.getByRole("button", { name: "Submit command" }).click(),
      ]);
      assert.equal(unauthorized.status(), 403);
      await page.goto(complete);
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));
    } finally {
      await context.close();
    }
  }
} finally {
  await browser.close();
}
